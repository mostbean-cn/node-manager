package com.github.nicestudent.nodemanager.services

import com.github.nicestudent.nodemanager.infrastructure.FileSystemHelper
import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager

/**
 * Node.js 版本切换服务
 *
 * 委托给当前活跃的 VersionManager 执行切换，
 * 切换成功后尝试同步 IDE 的 Node.js 解释器配置。
 */
object NodeSwitchService {
    private val log = Logger.getInstance(NodeSwitchService::class.java)

    /**
     * 全局切换 Node.js 版本
     *
     * @param version 目标版本号（如 "v20.11.0"）
     * @return 切换是否成功
     */
    fun switchGlobal(version: String): Boolean {
        val manager = VersionManagerRegistry.getInstance().getActiveManager()
        if (manager == null) {
            log.warn("No version manager available for switching")
            return false
        }

        val success = manager.use(version)
        if (success) {
            log.info("Switched to Node.js $version via ${manager.displayName}")
            syncIdeInterpreter()
        }
        return success
    }

    /**
     * 切换后尝试同步 IDE 的 Node.js 解释器配置
     *
     * 通过反射调用 NodeJsInterpreterManager（需要 JavaScript 插件），
     * 如果 JavaScript 插件不可用则静默跳过，不影响核心功能。
     */
    private fun syncIdeInterpreter() {
        try {
            val nodePath = FileSystemHelper.findSystemNode()?.absolutePath
            if (nodePath == null) {
                log.info("Cannot sync IDE interpreter: node executable not found in PATH")
                return
            }

            val managerClass = Class.forName("com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager")
            val localClass = Class.forName("com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter")

            for (project in ProjectManager.getInstance().openProjects) {
                val manager = managerClass.getMethod("getInstance", com.intellij.openapi.project.Project::class.java)
                    .invoke(null, project)

                val interpreter = localClass.getConstructor(String::class.java).newInstance(nodePath)

                managerClass.getMethod("setInterpreter", Class.forName("com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter"))
                    .invoke(manager, interpreter)

                log.info("Synced IDE Node.js interpreter to $nodePath for project ${project.name}")
            }
        } catch (e: ClassNotFoundException) {
            log.info("JavaScript plugin not available, skipping IDE interpreter sync")
        } catch (e: Exception) {
            log.warn("Failed to sync IDE Node.js interpreter: ${e.message}")
        }
    }
}
