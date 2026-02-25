package com.github.nicestudent.nodemanager.infrastructure

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * 文件系统工具，处理跨平台路径检测
 */
object FileSystemHelper {
    private val LOG = Logger.getInstance(FileSystemHelper::class.java)

    /**
     * 获取 nvm 安装目录
     */
    fun getNvmDir(): File? {
        // Windows: 优先检查 NVM_HOME（nvm-windows 专用环境变量）
        val envNvmHome = System.getenv("NVM_HOME")
        if (!envNvmHome.isNullOrBlank()) {
            val dir = File(envNvmHome)
            if (dir.exists()) return dir
        }

        // Unix: 检查 NVM_DIR
        val envNvmDir = System.getenv("NVM_DIR")
        if (!envNvmDir.isNullOrBlank()) {
            val dir = File(envNvmDir)
            if (dir.exists()) return dir
        }

        return when {
            SystemInfo.isWindows -> getNvmDirWindows()
            SystemInfo.isMac || SystemInfo.isLinux -> getNvmDirUnix()
            else -> null
        }
    }

    /**
     * 获取 fnm 安装目录
     */
    fun getFnmDir(): File? {
        val envFnmDir = System.getenv("FNM_DIR")
        if (!envFnmDir.isNullOrBlank()) {
            val dir = File(envFnmDir)
            if (dir.exists()) return dir
        }

        return when {
            SystemInfo.isWindows -> {
                val appData = System.getenv("APPDATA") ?: return null
                val dir = File(appData, "fnm")
                if (dir.exists()) dir else null
            }
            else -> {
                val home = System.getProperty("user.home")
                val dir = File(home, ".fnm")
                if (dir.exists()) dir else null
            }
        }
    }

    /**
     * 获取系统默认 node 可执行文件路径
     */
    fun findSystemNode(): File? {
        val nodeName = if (SystemInfo.isWindows) "node.exe" else "node"
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: return null

        for (dir in pathDirs) {
            val nodeFile = File(dir, nodeName)
            if (nodeFile.exists() && nodeFile.canExecute()) {
                return nodeFile
            }
        }
        return null
    }

    private fun getNvmDirWindows(): File? {
        // nvm-windows 默认安装在 %APPDATA%\nvm
        val appData = System.getenv("APPDATA") ?: return null
        val nvmDir = File(appData, "nvm")
        return if (nvmDir.exists()) nvmDir else null
    }

    private fun getNvmDirUnix(): File? {
        val home = System.getProperty("user.home")
        val nvmDir = File(home, ".nvm")
        return if (nvmDir.exists()) nvmDir else null
    }
}
