package com.github.nicestudent.nodemanager.ui.toolwindow

import com.github.nicestudent.nodemanager.actions.NodeVersionRefreshListener
import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.github.nicestudent.nodemanager.model.NodeInstallation
import com.github.nicestudent.nodemanager.services.NodeInstallService
import com.github.nicestudent.nodemanager.services.NodeSwitchService
import com.github.nicestudent.nodemanager.services.NodeVersionService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

/**
 * Node.js 版本列表面板
 *
 * 单一版本列表，顶部显示当前管理器 + 切换下拉框。
 * 通过 ⊕ 按钮安装新版本，Use/Uninstall 管理已安装版本。
 */
class NodeVersionListPanel(private val project: Project) {

    private val localListModel = DefaultListModel<NodeInstallation>()
    private val versionService = NodeVersionService.getInstance()

    // 管理器状态
    private val managerStatusLabel = JBLabel()
    private val managerComboBox = JComboBox<String>()

    init {
        updateManagerStatus()

        // 订阅安装完成刷新事件
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(NodeVersionRefreshListener.TOPIC, object : NodeVersionRefreshListener {
                override fun onRefreshRequested() {
                    refreshLocalVersions()
                }
            })
    }

    fun getContent(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // 顶部：管理器状态 + 切换
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // 中间：版本列表区域
        val contentPanel = createContentPanel()
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        // 初始加载
        refreshLocalVersions()

        return mainPanel
    }

    /**
     * 顶部面板：管理器标识 + 切换下拉框
     */
    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        panel.border = BorderFactory.createEmptyBorder(6, 8, 6, 8)

        // 左侧：管理器状态标签
        panel.add(managerStatusLabel, BorderLayout.WEST)

        // 右侧：管理器切换下拉框
        val registry = VersionManagerRegistry.getInstance()
        val available = registry.detectAvailable()

        if (available.size > 1) {
            val managerNames = available.map { it.displayName }.toTypedArray()
            managerComboBox.model = DefaultComboBoxModel(managerNames)

            // 选中当前活跃管理器
            val active = registry.getActiveManager()
            if (active != null) {
                managerComboBox.selectedItem = active.displayName
            }

            managerComboBox.addActionListener {
                val selectedDisplay = managerComboBox.selectedItem?.toString() ?: return@addActionListener
                val selectedManager = available.find { it.displayName == selectedDisplay }
                if (selectedManager != null) {
                    registry.setActiveManager(selectedManager.name)
                    updateManagerStatus()
                    refreshLocalVersions()
                }
            }

            val switchPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
            switchPanel.add(JBLabel("Switch:"))
            switchPanel.add(managerComboBox)
            panel.add(switchPanel, BorderLayout.EAST)
        }

        return panel
    }

    /**
     * 内容区域：工具栏 + 版本列表 + 操作按钮
     */
    private fun createContentPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 工具栏（⊕ 安装按钮）
        val toolbar = createToolbar()
        panel.add(toolbar, BorderLayout.NORTH)

        // 版本列表
        val localList = JBList(localListModel)
        localList.cellRenderer = LocalVersionCellRenderer()
        localList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        panel.add(JBScrollPane(localList), BorderLayout.CENTER)

        // 底部操作按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val switchBtn = JButton("Use").apply {
            addActionListener {
                val selected = localList.selectedValue ?: return@addActionListener
                switchVersion(selected.version)
            }
        }
        val uninstallBtn = JButton("Uninstall").apply {
            addActionListener {
                val selected = localList.selectedValue ?: return@addActionListener
                if (selected.isActive) {
                    Messages.showWarningDialog(project, "Cannot uninstall the active Node.js version.", "Warning")
                    return@addActionListener
                }
                uninstallVersion(selected.version)
            }
        }
        buttonPanel.add(switchBtn)
        buttonPanel.add(uninstallBtn)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup()
        ActionManager.getInstance().getAction("NodeManager.InstallNode")?.let { actionGroup.add(it) }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("NodeManagerLocal", actionGroup, true)
        toolbar.targetComponent = null
        return toolbar.component
    }

    // ==================== 管理器状态 ====================

    private fun updateManagerStatus() {
        val registry = VersionManagerRegistry.getInstance()
        val manager = registry.getActiveManager()

        if (manager != null) {
            val version = manager.getManagerVersion()
            val versionSuffix = if (version != null) " v$version" else ""
            managerStatusLabel.text = "● ${manager.displayName}$versionSuffix"
            managerStatusLabel.toolTipText = "Using ${manager.displayName} to manage Node.js versions"
            managerStatusLabel.foreground = JBColor(0x5FA04E, 0x6BBF59)
        } else {
            managerStatusLabel.text = "⚠ No version manager found"
            managerStatusLabel.toolTipText = "Please install nvm or fnm to manage Node.js versions"
            managerStatusLabel.foreground = JBColor.ORANGE
        }
    }

    // ==================== 数据操作 ====================

    fun refreshLocalVersions() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Detecting Node.js versions...") {
            override fun run(indicator: ProgressIndicator) {
                val versions = versionService.detectLocalVersions()
                ApplicationManager.getApplication().invokeLater {
                    localListModel.clear()
                    versions.forEach { localListModel.addElement(it) }
                    updateManagerStatus()
                }
            }
        })
    }

    private fun switchVersion(version: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Switching to Node.js $version...") {
            override fun run(indicator: ProgressIndicator) {
                val success = NodeSwitchService.switchGlobal(version)
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        showNotification("Switched to Node.js $version", NotificationType.INFORMATION)
                        refreshLocalVersions()
                    } else {
                        showNotification("Failed to switch to Node.js $version", NotificationType.ERROR)
                    }
                }
            }
        })
    }

    private fun uninstallVersion(version: String) {
        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to uninstall Node.js $version?",
            "Confirm Uninstall",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Uninstalling Node.js $version...") {
            override fun run(indicator: ProgressIndicator) {
                val success = NodeInstallService.uninstall(version)
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        showNotification("Node.js $version uninstalled", NotificationType.INFORMATION)
                        refreshLocalVersions()
                    } else {
                        showNotification("Failed to uninstall Node.js $version", NotificationType.ERROR)
                    }
                }
            }
        })
    }

    private fun showNotification(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Node Manager")
            .createNotification(message, type)
            .notify(project)
    }

    // ==================== 列表渲染器 ====================

    private class LocalVersionCellRenderer : ListCellRenderer<NodeInstallation> {
        override fun getListCellRendererComponent(
            list: JList<out NodeInstallation>,
            value: NodeInstallation,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val panel = JPanel(BorderLayout())
            val versionLabel = JBLabel(value.version).apply {
                if (value.isActive) {
                    font = font.deriveFont(java.awt.Font.BOLD)
                    foreground = JBColor.GREEN.darker()
                }
            }
            val sourceLabel = JBLabel("[${value.source.displayName}]").apply {
                foreground = JBColor.GRAY
            }
            val activeLabel = if (value.isActive) JBLabel(" ✓ active").apply {
                foreground = JBColor.GREEN.darker()
            } else JBLabel("")

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
            leftPanel.add(versionLabel)
            leftPanel.add(activeLabel)

            panel.add(leftPanel, BorderLayout.WEST)
            panel.add(sourceLabel, BorderLayout.EAST)

            if (isSelected) {
                panel.background = list.selectionBackground
                leftPanel.background = list.selectionBackground
            } else {
                panel.background = list.background
                leftPanel.background = list.background
            }

            return panel
        }
    }
}
