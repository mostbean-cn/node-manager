package com.github.nicestudent.nodemanager.actions

import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.github.nicestudent.nodemanager.services.NodeInstallService
import com.github.nicestudent.nodemanager.services.NodeVersionService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

/**
 * 卸载 Node.js 版本的 Action
 */
class UninstallNodeAction : AnAction("Uninstall Node.js Version...", "Uninstall a Node.js version", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val versions = NodeVersionService.getInstance().getLocalVersions().filter { !it.isActive }

        if (versions.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Node Manager")
                .createNotification("No inactive Node.js versions to uninstall.", NotificationType.WARNING)
                .notify(project)
            return
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<NodeInstallation>("Select Node.js Version to Uninstall", versions) {
                override fun getTextFor(value: NodeInstallation): String {
                    return "${value.version} [${value.source.displayName}]"
                }

                override fun onChosen(selectedValue: NodeInstallation, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        val confirm = Messages.showYesNoDialog(
                            project,
                            "Are you sure you want to uninstall Node.js ${selectedValue.version}?",
                            "Confirm Uninstall",
                            Messages.getQuestionIcon()
                        )
                        if (confirm == Messages.YES) {
                            doUninstall(project, selectedValue.version)
                        }
                    }
                    return PopupStep.FINAL_CHOICE
                }
            }
        )
        popup.showInFocusCenter()
    }

    private fun doUninstall(project: com.intellij.openapi.project.Project, version: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Uninstalling Node.js $version...") {
            override fun run(indicator: ProgressIndicator) {
                val success = NodeInstallService.uninstall(version)
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val type = if (success) NotificationType.INFORMATION else NotificationType.ERROR
                    val msg = if (success) "Node.js $version uninstalled" else "Failed to uninstall Node.js $version"
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Node Manager")
                        .createNotification(msg, type)
                        .notify(project)
                }
            }
        })
    }
}
