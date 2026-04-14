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
import java.util.concurrent.atomic.AtomicReference

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

    /** 缓存的当前版本（使用 AtomicReference 保证线程安全） */
    private val currentVersionCache = AtomicReference<String?>(null)

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
     *
     * 注意：此方法会执行阻塞操作，必须在后台线程调用。
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
        val activeVersion = currentVersionCache.get()
        localVersionsCache = installations.map {
            it.copy(isActive = it.version == activeVersion)
        }.sortedByDescending { it.version }

        return localVersionsCache
    }

    /**
     * 获取当前激活的 Node.js 版本（从缓存读取）
     *
     * 此方法可在 EDT 上安全调用，不会执行阻塞操作。
     */
    fun getCurrentVersion(): String? = currentVersionCache.get()

    /**
     * 异步刷新当前版本缓存
     *
     * 在后台线程中执行版本检测，完成后回调通知 UI 更新。
     */
    fun refreshCurrentVersionAsync(onComplete: (() -> Unit)? = null) {
        // 先异步初始化版本管理器注册中心
        VersionManagerRegistry.getInstance().initializeAsync {
            // 版本管理器初始化完成后，再检测当前版本
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val version = detectCurrentVersionSync()
                    currentVersionCache.set(version)
                    log.info("Current Node.js version refreshed: $version")
                } catch (e: Exception) {
                    log.warn("Failed to refresh current version: ${e.message}")
                } finally {
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * 异步刷新当前版本和本地版本列表缓存
     *
     * 统一串联初始化、当前版本检测和本地版本检测，
     * 避免 UI 因调用顺序不一致拿到过期状态。
     */
    fun refreshVersionStateAsync(onComplete: ((List<NodeInstallation>) -> Unit)? = null) {
        VersionManagerRegistry.getInstance().initializeAsync {
            ApplicationManager.getApplication().executeOnPooledThread {
                var versions = localVersionsCache
                try {
                    val version = detectCurrentVersionSync()
                    currentVersionCache.set(version)
                    versions = detectLocalVersions()
                    log.info("Version state refreshed: current=$version, local=${versions.size}")
                } catch (e: Exception) {
                    log.warn("Failed to refresh version state: ${e.message}")
                } finally {
                    onComplete?.invoke(versions)
                }
            }
        }
    }

    /**
     * 同步检测当前版本（必须在后台线程调用）
     */
    private fun detectCurrentVersionSync(): String? {
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
     *
     * 此方法可在 EDT 上安全调用，返回缓存数据。
     */
    fun getLocalVersions(): List<NodeInstallation> = localVersionsCache

    /**
     * 异步刷新本地版本列表
     *
     * 在后台线程中执行检测，完成后回调通知 UI 更新。
     */
    fun refreshLocalVersionsAsync(onComplete: (() -> Unit)? = null) {
        VersionManagerRegistry.getInstance().initializeAsync {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    detectLocalVersions()
                    log.info("Local versions refreshed: ${localVersionsCache.size} versions")
                } catch (e: Exception) {
                    log.warn("Failed to refresh local versions: ${e.message}")
                } finally {
                    onComplete?.invoke()
                }
            }
        }
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
