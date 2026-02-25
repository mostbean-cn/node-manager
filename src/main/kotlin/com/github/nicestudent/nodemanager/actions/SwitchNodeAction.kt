package com.github.nicestudent.nodemanager.actions

import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.github.nicestudent.nodemanager.services.NodeSwitchService
import com.github.nicestudent.nodemanager.services.NodeVersionService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

/**
 * 切换 Node.js 版本的 Action
 */
class SwitchNodeAction : AnAction("Switch Node.js Version...", "Switch to a different Node.js version", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val versions = NodeVersionService.getInstance().getLocalVersions()

        if (versions.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Node Manager")
                .createNotification("No Node.js versions found. Please install one first.", NotificationType.WARNING)
                .notify(project)
            return
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<NodeInstallation>("Switch Node.js Version", versions) {
                override fun getTextFor(value: NodeInstallation): String {
                    val active = if (value.isActive) " ✓" else ""
                    return "${value.version} [${value.source.displayName}]$active"
                }

                override fun onChosen(selectedValue: NodeInstallation, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice && !selectedValue.isActive) {
                        doSwitch(project, selectedValue.version)
                    }
                    return PopupStep.FINAL_CHOICE
                }
            }
        )
        popup.showInFocusCenter()
    }

    private fun doSwitch(project: com.intellij.openapi.project.Project, version: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Switching to Node.js $version...") {
            override fun run(indicator: ProgressIndicator) {
                val success = NodeSwitchService.switchGlobal(version)
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val type = if (success) NotificationType.INFORMATION else NotificationType.ERROR
                    val msg = if (success) "Switched to Node.js $version" else "Failed to switch to Node.js $version"
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Node Manager")
                        .createNotification(msg, type)
                        .notify(project)
                }
            }
        })
    }
}
