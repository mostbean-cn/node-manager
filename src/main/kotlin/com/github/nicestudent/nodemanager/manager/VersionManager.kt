package com.github.nicestudent.nodemanager.manager

import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.intellij.openapi.progress.ProgressIndicator

/**
 * 版本管理器统一接口
 *
 * 抽象 nvm / fnm 等不同版本管理工具的操作，
 * UI 层只依赖此接口，不关心底层实现。
 */
interface VersionManager {

    /** 管理器名称（如 "nvm"、"fnm"） */
    val name: String

    /** 管理器显示名称（如 "NVM for Windows"、"fnm"） */
    val displayName: String

    /** 检测管理器是否已安装且可用 */
    fun isAvailable(): Boolean

    /** 获取管理器自身的版本号 */
    fun getManagerVersion(): String?

    /** 列出本地已安装的所有 Node.js 版本 */
    fun listInstalled(): List<NodeInstallation>

    /** 获取当前激活的 Node.js 版本 */
    fun current(): String?

    /** 安装指定版本的 Node.js */
    fun install(version: String, indicator: ProgressIndicator? = null): Boolean

    /** 卸载指定版本的 Node.js */
    fun uninstall(version: String): Boolean

    /** 切换到指定版本的 Node.js */
    fun use(version: String): Boolean

    /** 列出可远程安装的 Node.js 版本 */
    fun listAvailable(): List<String>
}
