package com.github.nicestudent.nodemanager.manager

import com.github.nicestudent.nodemanager.infrastructure.FileSystemHelper
import com.github.nicestudent.nodemanager.infrastructure.NodeRegistryClient
import com.github.nicestudent.nodemanager.infrastructure.ProcessExecutor
import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * NVM 版本管理器实现
 *
 * 设计原则：
 * - **只读操作**（isAvailable、getManagerVersion、listInstalled、current）
 *   全部通过文件系统检测，**绝不调用 nvm.exe**，
 *   避免 nvm-windows 的「Terminal Only」弹框。
 * - **写操作**（install、uninstall、use）通过 `start "" /min /wait` 在
 *   最小化的新控制台窗口中运行 nvm.exe，用户不会看到弹框。
 */
class NvmVersionManager : VersionManager {

    private val log = Logger.getInstance(NvmVersionManager::class.java)

    override val name: String = "nvm"

    override val displayName: String
        get() = if (SystemInfo.isWindows) "NVM for Windows" else "nvm"

    // ==================== 只读操作（文件系统检测） ====================

    override fun isAvailable(): Boolean {
        return if (SystemInfo.isWindows) {
            findNvmExe() != null
        } else {
            val nvmDir = FileSystemHelper.getNvmDir()
            nvmDir != null && File(nvmDir, "nvm.sh").exists()
        }
    }

    override fun getManagerVersion(): String? {
        if (SystemInfo.isWindows) {
            // nvm.exe 没有嵌入版本信息，读取同目录下卸载程序的版本号
            val nvmDir = FileSystemHelper.getNvmDir() ?: return null
            val uninstaller = File(nvmDir, "unins000.exe")
            if (!uninstaller.exists()) return null
            return try {
                ProcessExecutor.executeAndGetOutput(
                    listOf("powershell", "-NoProfile", "-Command",
                        "(Get-Item '${uninstaller.absolutePath}').VersionInfo.ProductVersion"),
                    timeoutMs = 5_000,
                )?.trim()?.ifBlank { null }
            } catch (e: Exception) {
                null
            }
        }

        // Unix: 从 ~/.nvm/package.json 解析版本
        val nvmDir = FileSystemHelper.getNvmDir() ?: return null
        val packageJson = File(nvmDir, "package.json")
        if (!packageJson.exists()) return null

        return try {
            val content = packageJson.readText()
            Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    override fun listInstalled(): List<NodeInstallation> {
        val nvmDir = FileSystemHelper.getNvmDir() ?: return emptyList()
        val installations = mutableListOf<NodeInstallation>()

        if (SystemInfo.isWindows) {
            nvmDir.listFiles()
                ?.filter { it.isDirectory && it.name.matches(Regex("v?\\d+\\.\\d+\\.\\d+")) }
                ?.forEach { dir ->
                    val version = if (dir.name.startsWith("v")) dir.name else "v${dir.name}"
                    installations.add(
                        NodeInstallation(version = version, path = dir.absolutePath, source = NodeInstallation.Source.NVM)
                    )
                }
        } else {
            val versionsDir = File(nvmDir, "versions/node")
            versionsDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("v") }
                ?.forEach { dir ->
                    installations.add(
                        NodeInstallation(version = dir.name, path = dir.absolutePath, source = NodeInstallation.Source.NVM)
                    )
                }
        }

        return installations
    }

    override fun current(): String? {
        return if (SystemInfo.isWindows) {
            ProcessExecutor.executeAndGetOutput(listOf("cmd", "/c", "node", "--version"))
        } else {
            ProcessExecutor.executeAndGetOutput(listOf("node", "--version"))
        }
    }

    // ==================== 写操作 ====================

    override fun install(version: String, indicator: ProgressIndicator?): Boolean {
        return try {
            indicator?.text = "Installing Node.js $version via nvm..."
            val cleanVersion = version.removePrefix("v")
            executeNvmWrite("install $cleanVersion", timeoutMs = 300_000)

            // 不信任退出码，通过文件系统验证是否真的安装成功
            val nvmDir = FileSystemHelper.getNvmDir() ?: return false
            val versionDir = File(nvmDir, "v$cleanVersion")
            val installed = versionDir.exists() && versionDir.isDirectory
            if (!installed) {
                log.warn("nvm install reported success but v$cleanVersion directory not found in ${nvmDir.absolutePath}")
            }
            installed
        } catch (e: Exception) {
            log.warn("nvm install failed: ${e.message}")
            false
        }
    }

    override fun uninstall(version: String): Boolean {
        return try {
            val cleanVersion = version.removePrefix("v")
            executeNvmWrite("uninstall $cleanVersion", timeoutMs = 60_000)
        } catch (e: Exception) {
            log.warn("nvm uninstall failed: ${e.message}")
            false
        }
    }

    override fun use(version: String): Boolean {
        return try {
            val cleanVersion = version.removePrefix("v")
            executeNvmWrite("use $cleanVersion", timeoutMs = 30_000)
        } catch (e: Exception) {
            log.warn("nvm use failed: ${e.message}")
            false
        }
    }

    /**
     * 列出可安装的 Node.js 版本
     *
     * 重要：Windows 上绝不调用 nvm.exe（会弹出 Terminal Only 对话框）。
     * 直接通过 HTTP 从 nodejs.org/dist/index.json 获取，
     * 这也是 nvm list available 内部所做的事情。
     */
    override fun listAvailable(): List<String> {
        return try {
            val versions = NodeRegistryClient.fetchVersions()
            versions.map { it.version }
        } catch (e: Exception) {
            log.warn("Failed to fetch available versions: ${e.message}")
            emptyList()
        }
    }

    // ==================== 内部方法 ====================

    private fun findNvmExe(): File? {
        val nvmHome = System.getenv("NVM_HOME")
        if (!nvmHome.isNullOrBlank()) {
            val exe = File(nvmHome, "nvm.exe")
            if (exe.exists()) return exe
        }
        val appData = System.getenv("APPDATA") ?: return null
        val exe = File(appData, "nvm/nvm.exe")
        return if (exe.exists()) exe else null
    }

    /**
     * 执行 nvm 写操作
     *
     * Windows: 通过 VBScript 的 WshShell.Run 完全隐藏控制台窗口执行 nvm 命令。
     * Unix: 通过 `bash -c "source ~/.nvm/nvm.sh && nvm <args>"` 执行。
     */
    private fun executeNvmWrite(args: String, timeoutMs: Int = 30_000): Boolean {
        return if (SystemInfo.isWindows) {
            executeNvmWindows(args, timeoutMs)
        } else {
            val output = ProcessExecutor.execute(
                listOf("bash", "-c", "source ~/.nvm/nvm.sh && nvm $args"),
                timeoutMs = timeoutMs,
            )
            output.exitCode == 0
        }
    }

    /**
     * Windows 专用：通过 VBScript 在完全隐藏的窗口中执行 nvm 命令
     *
     * WshShell.Run 返回命令的退出码，通过 WScript.Quit 传递给 cscript。
     * bat 脚本用 exit /b %ERRORLEVEL% 确保 nvm 的退出码冒泡。
     * 窗口参数 0 = 完全隐藏，True = 等待命令完成。
     */
    private fun executeNvmWindows(args: String, timeoutMs: Int): Boolean {
        val tempBat = File.createTempFile("node-manager-", ".bat")
        val tempVbs = File.createTempFile("node-manager-", ".vbs")
        try {
            // bat 脚本：执行 nvm 命令，用 exit /b 传递退出码
            tempBat.writeText(
                "@echo off\r\nnvm $args\r\nexit /b %ERRORLEVEL%\r\n",
                StandardCharsets.UTF_8,
            )

            // VBScript：隐藏窗口执行，并将退出码传递给 cscript
            tempVbs.writeText(
                "Set WshShell = CreateObject(\"WScript.Shell\")\r\n" +
                "exitCode = WshShell.Run(\"cmd.exe /c \"\"${tempBat.absolutePath}\"\"\", 0, True)\r\n" +
                "WScript.Quit(exitCode)\r\n",
                StandardCharsets.UTF_8,
            )

            log.info("Executing nvm command (hidden window): nvm $args")

            val commandLine = GeneralCommandLine("cscript", "//Nologo", tempVbs.absolutePath)
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(timeoutMs)

            if (output.exitCode != 0) {
                log.warn("nvm command failed (exit=${output.exitCode}): ${output.stderr}")
            }
            return output.exitCode == 0
        } catch (e: Exception) {
            log.error("Failed to execute nvm command: nvm $args", e)
            return false
        } finally {
            tempBat.delete()
            tempVbs.delete()
        }
    }
}
