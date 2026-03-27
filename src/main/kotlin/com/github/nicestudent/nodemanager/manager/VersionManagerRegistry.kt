package com.github.nicestudent.nodemanager.manager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicReference

/**
 * 版本管理器注册中心（Application 级别单例）
 *
 * 职责：
 * - 检测系统中可用的版本管理器（nvm / fnm）
 * - 根据用户偏好或自动检测结果选择当前活跃管理器
 * - 提供统一的管理器访问入口
 */
@Service
class VersionManagerRegistry {

    private val log = Logger.getInstance(VersionManagerRegistry::class.java)

    /** 所有支持的管理器实例 */
    private val managers: List<VersionManager> = listOf(
        NvmVersionManager(),
        FnmVersionManager(),
    )

    /** 已检测到的可用管理器缓存 */
    private val availableManagersRef = AtomicReference<List<VersionManager>>(emptyList())

    /** 当前选中的管理器（使用 AtomicReference 保证线程安全） */
    private val activeManagerRef = AtomicReference<VersionManager?>(null)

    /** 是否已初始化 */
    @Volatile
    private var initialized = false

    companion object {
        fun getInstance(): VersionManagerRegistry =
            ApplicationManager.getApplication().getService(VersionManagerRegistry::class.java)
    }

    /**
     * 异步初始化版本管理器检测
     *
     * 在后台线程中检测可用的版本管理器并缓存结果。
     */
    fun initializeAsync(onComplete: (() -> Unit)? = null) {
        if (initialized) {
            onComplete?.invoke()
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val detected = detectAvailableInternal()
                updateActiveManager(detected)
                initialized = true

                activeManagerRef.get()?.let {
                    log.info("Auto-selected version manager: ${it.displayName}")
                } ?: log.warn("No version manager found (nvm / fnm)")
            } catch (e: Exception) {
                log.warn("Failed to detect version managers: ${e.message}")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    /**
     * 检测所有可用的版本管理器
     *
     * 注意：此方法会执行阻塞操作，必须在后台线程调用。
     *
     * @return 可用管理器列表
     */
    fun detectAvailable(): List<VersionManager> {
        val detected = detectAvailableInternal()
        updateActiveManager(detected)
        initialized = true
        return detected
    }

    /**
     * 获取当前活跃的版本管理器
     *
     * 返回已缓存的管理器，不会执行阻塞操作。
     * 如果尚未初始化，返回 null。
     *
     * @return 当前管理器，如果都不可用则返回 null
     */
    fun getActiveManager(): VersionManager? = activeManagerRef.get()

    /**
     * 获取已缓存的可用管理器列表
     *
     * 不会触发新的检测，可在 EDT 上安全调用。
     */
    fun getAvailableManagers(): List<VersionManager> = availableManagersRef.get()

    /**
     * 手动设置活跃管理器（用户从设置面板选择）
     *
     * @param managerName 管理器名称（"nvm" 或 "fnm"）
     */
    fun setActiveManager(managerName: String) {
        val cachedAvailable = availableManagersRef.get()
        val manager = cachedAvailable.find { it.name == managerName }
            ?: if (!initialized) managers.find { it.name == managerName } else null

        if (manager != null) {
            activeManagerRef.set(manager)
            log.info("Manually set version manager: ${manager.displayName}")
        }
    }

    /**
     * 获取所有已注册的管理器（不论是否可用）
     */
    fun getAllManagers(): List<VersionManager> = managers

    /**
     * 强制重新检测
     */
    fun refresh() {
        availableManagersRef.set(emptyList())
        activeManagerRef.set(null)
        initialized = false
    }

    private fun detectAvailableInternal(): List<VersionManager> {
        val detected = managers.filter { manager ->
            val available = manager.isAvailable()
            log.info("${manager.displayName}: ${if (available) "available" else "not found"}")
            available
        }
        availableManagersRef.set(detected)
        return detected
    }

    private fun updateActiveManager(detected: List<VersionManager>) {
        val current = activeManagerRef.get()
        if (current == null || detected.none { it.name == current.name }) {
            activeManagerRef.set(detected.firstOrNull())
        }
    }
}
