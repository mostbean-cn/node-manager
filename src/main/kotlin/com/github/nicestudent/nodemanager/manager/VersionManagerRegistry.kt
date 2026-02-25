package com.github.nicestudent.nodemanager.manager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

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

    /** 当前选中的管理器 */
    private var activeManager: VersionManager? = null

    companion object {
        fun getInstance(): VersionManagerRegistry =
            ApplicationManager.getApplication().getService(VersionManagerRegistry::class.java)
    }

    /**
     * 检测所有可用的版本管理器
     *
     * @return 可用管理器列表
     */
    fun detectAvailable(): List<VersionManager> {
        return managers.filter { manager ->
            val available = manager.isAvailable()
            log.info("${manager.displayName}: ${if (available) "available" else "not found"}")
            available
        }
    }

    /**
     * 获取当前活跃的版本管理器
     *
     * 选择顺序：用户手动设置 > 自动检测（nvm 优先）
     *
     * @return 当前管理器，如果都不可用则返回 null
     */
    fun getActiveManager(): VersionManager? {
        if (activeManager != null) return activeManager

        // 自动检测并选择第一个可用的管理器
        activeManager = detectAvailable().firstOrNull()
        if (activeManager != null) {
            log.info("Auto-selected version manager: ${activeManager!!.displayName}")
        } else {
            log.warn("No version manager found (nvm / fnm)")
        }
        return activeManager
    }

    /**
     * 手动设置活跃管理器（用户从设置面板选择）
     *
     * @param managerName 管理器名称（"nvm" 或 "fnm"）
     */
    fun setActiveManager(managerName: String) {
        activeManager = managers.find { it.name == managerName && it.isAvailable() }
        if (activeManager != null) {
            log.info("Manually set version manager: ${activeManager!!.displayName}")
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
        activeManager = null
    }
}
