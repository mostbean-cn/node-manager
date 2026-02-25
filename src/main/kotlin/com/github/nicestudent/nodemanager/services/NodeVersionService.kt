package com.github.nicestudent.nodemanager.services

import com.github.nicestudent.nodemanager.infrastructure.FileSystemHelper
import com.github.nicestudent.nodemanager.infrastructure.NodeRegistryClient
import com.github.nicestudent.nodemanager.infrastructure.ProcessExecutor
import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.github.nicestudent.nodemanager.model.NodeVersion
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo

/**
 * Node.js 版本管理核心服务（Application 级别单例）
 *
 * 通过 VersionManagerRegistry 获取当前活跃的版本管理器，
 * 委托其执行版本检测、安装、卸载、切换等操作。
 */
@Service
class NodeVersionService {

    private val log = Logger.getInstance(NodeVersionService::class.java)

    /** 缓存的本地安装列表 */
    private var localVersionsCache: List<NodeInstallation> = emptyList()

    /** 缓存的远程版本列表 */
    private var remoteVersionsCache: List<NodeVersion> = emptyList()

    companion object {
        fun getInstance(): NodeVersionService =
            ApplicationManager.getApplication().getService(NodeVersionService::class.java)
    }

    // ==================== 本地版本 ====================

    /**
     * 检测本地已安装的所有 Node.js 版本
     *
     * 委托给当前活跃的 VersionManager，
     * 如果没有可用管理器则回退到系统 PATH 检测。
     */
    fun detectLocalVersions(): List<NodeInstallation> {
        val installations = mutableListOf<NodeInstallation>()

        val manager = VersionManagerRegistry.getInstance().getActiveManager()
        if (manager != null) {
            installations.addAll(manager.listInstalled())
        }

        // 始终检测系统 PATH 中的 node（可能不受任何管理器管理）
        detectSystemNode()?.let { systemNode ->
            // 避免重复：如果管理器已包含相同版本则跳过
            if (installations.none { it.version == systemNode.version }) {
                installations.add(systemNode)
            }
        }

        // 标记当前激活版本，按版本号降序排列
        val activeVersion = getCurrentVersion()
        localVersionsCache = installations.map {
            it.copy(isActive = it.version == activeVersion)
        }.sortedByDescending { it.version }

        return localVersionsCache
    }

    /**
     * 获取当前激活的 Node.js 版本
     */
    fun getCurrentVersion(): String? {
        // 优先通过管理器获取
        val manager = VersionManagerRegistry.getInstance().getActiveManager()
        val managerCurrent = manager?.current()
        if (managerCurrent != null) return managerCurrent

        // 回退到 node --version
        return ProcessExecutor.executeAndGetOutput(
            if (SystemInfo.isWindows) listOf("cmd", "/c", "node", "--version")
            else listOf("node", "--version")
        )
    }

    /**
     * 获取缓存的本地版本列表
     */
    fun getLocalVersions(): List<NodeInstallation> {
        if (localVersionsCache.isEmpty()) {
            detectLocalVersions()
        }
        return localVersionsCache
    }

    // ==================== 远程版本 ====================

    /**
     * 获取远程可用的 Node.js 版本列表
     */
    fun fetchRemoteVersions(useMirror: Boolean = false): List<NodeVersion> {
        remoteVersionsCache = NodeRegistryClient.fetchVersions(useMirror)
        return remoteVersionsCache
    }

    /**
     * 获取缓存的远程版本列表
     */
    fun getRemoteVersions(): List<NodeVersion> = remoteVersionsCache

    // ==================== 内部方法 ====================

    private fun detectSystemNode(): NodeInstallation? {
        val nodeFile = FileSystemHelper.findSystemNode() ?: return null
        val version = ProcessExecutor.executeAndGetOutput(
            listOf(nodeFile.absolutePath, "--version")
        ) ?: return null

        return NodeInstallation(
            version = version,
            path = nodeFile.parentFile.absolutePath,
            source = NodeInstallation.Source.SYSTEM,
        )
    }
}
