package com.github.nicestudent.nodemanager.manager

import com.github.nicestudent.nodemanager.infrastructure.FileSystemHelper
import com.github.nicestudent.nodemanager.infrastructure.NodeRegistryClient
import com.github.nicestudent.nodemanager.infrastructure.ProcessExecutor
import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File

/**
 * fnm (Fast Node Manager) 版本管理器实现
 *
 * fnm 是跨平台的，命令格式统一，无需区分 Windows/Unix。
 */
class FnmVersionManager : VersionManager {

    private val log = Logger.getInstance(FnmVersionManager::class.java)

    override val name: String = "fnm"

    override val displayName: String = "fnm (Fast Node Manager)"

    override fun isAvailable(): Boolean {
        return try {
            val output = ProcessExecutor.executeAndGetOutput(listOf("fnm", "--version"))
            output != null && output.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    override fun getManagerVersion(): String? {
        return try {
            ProcessExecutor.executeAndGetOutput(listOf("fnm", "--version"))
                ?.trim()
                ?.removePrefix("fnm ")
        } catch (e: Exception) {
            null
        }
    }

    override fun listInstalled(): List<NodeInstallation> {
        val fnmDir = FileSystemHelper.getFnmDir() ?: return emptyList()
        val nodeVersionsDir = File(fnmDir, "node-versions")
        if (!nodeVersionsDir.exists()) return emptyList()

        return nodeVersionsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("v") }
            ?.map { dir ->
                val installDir = File(dir, "installation")
                NodeInstallation(
                    version = dir.name,
                    path = if (installDir.exists()) installDir.absolutePath else dir.absolutePath,
                    source = NodeInstallation.Source.FNM,
                )
            } ?: emptyList()
    }

    /**
     * 获取 fnm 当前默认 Node.js 版本
     *
     * 不调用 `fnm current`（依赖 fnm env shell 环境）。
     * 通过读取 `aliases/default` 目录链接 (Junction) 的目标路径来获取：
     * 目标路径格式如 `.../node-versions/v23.6.1/installation`，
     * 从中提取版本号。
     */
    override fun current(): String? {
        return try {
            val fnmDir = FileSystemHelper.getFnmDir() ?: return null
            val defaultAlias = File(fnmDir, "aliases/default").toPath()

            // 读取 Junction/Symlink 的目标路径
            val target = java.nio.file.Files.readSymbolicLink(defaultAlias).toString()

            // 从路径中提取版本号: .../node-versions/v23.6.1/installation
            val versionRegex = Regex("node-versions[/\\\\](v\\d+\\.\\d+\\.\\d+)")
            versionRegex.find(target)?.groupValues?.get(1)
        } catch (e: Exception) {
            log.info("Failed to detect fnm current version: ${e.message}")
            null
        }
    }

    override fun install(version: String, indicator: ProgressIndicator?): Boolean {
        return try {
            indicator?.text = "Installing Node.js $version via fnm..."
            val output = ProcessExecutor.execute(
                listOf("fnm", "install", version),
                timeoutMs = 300_000,
            )
            output.exitCode == 0
        } catch (e: Exception) {
            log.warn("fnm install failed: ${e.message}")
            false
        }
    }

    override fun uninstall(version: String): Boolean {
        return try {
            val output = ProcessExecutor.execute(
                listOf("fnm", "uninstall", version),
                timeoutMs = 60_000,
            )
            output.exitCode == 0
        } catch (e: Exception) {
            log.warn("fnm uninstall failed: ${e.message}")
            false
        }
    }

    /**
     * 切换 fnm 默认 Node.js 版本
     *
     * 使用 `fnm default` 而非 `fnm use`，
     * 因为 `fnm use` 依赖 `fnm env` 设置的 shell 环境变量，
     * 在 IDE 进程中不可用。`fnm default` 直接修改全局默认版本。
     */
    override fun use(version: String): Boolean {
        return try {
            val output = ProcessExecutor.execute(
                listOf("fnm", "default", version),
                timeoutMs = 30_000,
            )
            output.exitCode == 0
        } catch (e: Exception) {
            log.warn("fnm default failed: ${e.message}")
            false
        }
    }

    override fun listAvailable(): List<String> {
        return try {
            val versions = NodeRegistryClient.fetchVersions()
            versions.map { it.version }
        } catch (e: Exception) {
            log.warn("Failed to fetch available versions: ${e.message}")
            emptyList()
        }
    }
}
