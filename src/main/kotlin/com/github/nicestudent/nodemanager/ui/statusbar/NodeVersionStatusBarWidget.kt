package com.github.nicestudent.nodemanager.ui.statusbar

import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.github.nicestudent.nodemanager.services.NodeSwitchService
import com.github.nicestudent.nodemanager.services.NodeVersionService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * 状态栏 Widget 工厂
 */
class NodeVersionStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "NodeVersionStatusBar"

    override fun getDisplayName(): String = "Node.js Version"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = NodeVersionStatusBarWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

/**
 * 状态栏 Widget，显示当前 Node.js 版本
 * 点击后弹出已安装版本列表，支持快速切换
 */
class NodeVersionStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null

    companion object {
        const val ID = "NodeVersionStatusBar"
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        NodeVersionService.getInstance().refreshVersionStateAsync {
            ApplicationManager.getApplication().invokeLater {
                updateWidget()
            }
        }
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getText(): String {
        val version = NodeVersionService.getInstance().getCurrentVersion()
        return if (version != null) "Node: $version" else "Node: N/A"
    }

    override fun getAlignment(): Float = 0f

    override fun getTooltipText(): String = "Current Node.js version (click to switch)"

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
        val versions = NodeVersionService.getInstance().getLocalVersions()
        if (versions.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Node Manager")
                .createNotification("No Node.js versions found.", NotificationType.WARNING)
                .notify(project)
            return@Consumer
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<NodeInstallation>("Switch Node.js Version", versions) {
                override fun getTextFor(value: NodeInstallation): String {
                    val active = if (value.isActive) " ✓" else ""
                    return "${value.version} [${value.source.displayName}]$active"
                }

                override fun onChosen(selectedValue: NodeInstallation, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice && !selectedValue.isActive) {
                        doSwitch(selectedValue.version)
                    }
                    return PopupStep.FINAL_CHOICE
                }
            }
        )

        // 在状态栏组件位置弹出
        val component = event.component
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInFocusCenter()
        }
    }

    private fun doSwitch(version: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Switching to Node.js $version...") {
            override fun run(indicator: ProgressIndicator) {
                val success = NodeSwitchService.switchGlobal(version)
                if (success) {
                    NodeVersionService.getInstance().refreshVersionStateAsync {
                        ApplicationManager.getApplication().invokeLater {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("Node Manager")
                                .createNotification("Switched to Node.js $version", NotificationType.INFORMATION)
                                .notify(project)
                            updateWidget()
                        }
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Node Manager")
                            .createNotification("Failed to switch to Node.js $version", NotificationType.ERROR)
                            .notify(project)
                    }
                }
            }
        })
    }

    /**
     * 刷新 Widget 显示
     */
    fun updateWidget() {
        statusBar?.updateWidget(ID)
    }
}
