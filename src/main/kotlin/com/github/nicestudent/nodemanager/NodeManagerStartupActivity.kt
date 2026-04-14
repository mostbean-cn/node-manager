package com.github.nicestudent.nodemanager

import com.github.nicestudent.nodemanager.services.NodeVersionService
import com.github.nicestudent.nodemanager.services.ProjectConfigService
import com.github.nicestudent.nodemanager.ui.settings.NodeManagerSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 项目启动后自动检测 Node.js 版本
 */
class NodeManagerStartupActivity : ProjectActivity {

    private val log = Logger.getInstance(NodeManagerStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        val settings = NodeManagerSettings.getInstance()
        if (!settings.state.autoDetect) return

        log.info("Node Manager: detecting Node.js version on startup...")

        NodeVersionService.getInstance().refreshVersionStateAsync {
            val currentVersion = NodeVersionService.getInstance().getCurrentVersion()
            if (currentVersion != null) {
                log.info("Current Node.js version: $currentVersion")
            }

            // 检查项目级版本配置
            val projectConfig = ProjectConfigService.getInstance(project)
            val requiredVersion = projectConfig.getProjectNodeVersion()
            if (requiredVersion != null && currentVersion != null) {
                val normalizedRequired = requiredVersion.removePrefix("v")
                val normalizedCurrent = currentVersion.removePrefix("v")
                if (normalizedRequired != normalizedCurrent) {
                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Node Manager")
                            .createNotification(
                                "Node.js Version Mismatch",
                                "Project requires Node.js $requiredVersion, but current version is $currentVersion.",
                                NotificationType.WARNING,
                            )
                            .notify(project)
                    }
                }
            }

            // 检查 package.json 中的 engines.node
            val enginesNode = projectConfig.getEnginesNodeRequirement()
            if (enginesNode != null) {
                log.info("package.json engines.node requirement: $enginesNode")
            }
        }
    }
}
