package com.github.nicestudent.nodemanager.actions

import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.github.nicestudent.nodemanager.services.NodeInstallService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 安装 Node.js 版本的 Action
 *
 * 通过 NodeRegistryClient (HTTP) 获取可安装版本列表，
 * 弹出带搜索过滤的版本选择列表，选中后在后台安装。
 */
class InstallNodeAction : AnAction("Install Node.js Version...", "Install a new Node.js version", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = VersionManagerRegistry.getInstance().getActiveManager()

        if (manager == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Node Manager")
                .createNotification("No version manager found. Please install nvm or fnm.", NotificationType.WARNING)
                .notify(project)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching available versions...") {
            override fun run(indicator: ProgressIndicator) {
                val versions = manager.listAvailable()

                ApplicationManager.getApplication().invokeLater {
                    if (versions.isEmpty()) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Node Manager")
                            .createNotification("Failed to fetch available versions", NotificationType.ERROR)
                            .notify(project)
                        return@invokeLater
                    }

                    showVersionChooser(project, versions)
                }
            }
        })
    }

    /**
     * 弹出带搜索框的版本选择列表
     */
    private fun showVersionChooser(project: Project, versions: List<String>) {
        val listModel = DefaultListModel<String>().apply {
            versions.forEach { addElement(it) }
        }
        val filteredModel = DefaultListModel<String>().apply {
            versions.forEach { addElement(it) }
        }

        val versionList = JBList(filteredModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 15
        }

        val searchField = SearchTextField(false).apply {
            textEditor.emptyText.text = "Search version (e.g. 22, 18.19)..."
        }

        // 搜索过滤逻辑
        searchField.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                val query = searchField.text.trim().lowercase()
                filteredModel.clear()
                for (i in 0 until listModel.size()) {
                    val ver = listModel.getElementAt(i)
                    if (query.isEmpty() || ver.lowercase().contains(query)) {
                        filteredModel.addElement(ver)
                    }
                }
                if (filteredModel.size() > 0) {
                    versionList.selectedIndex = 0
                }
            }
        })

        // 组装面板
        val panel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            preferredSize = Dimension(280, 360)
        }

        val headerLabel = JBLabel("Select Node.js Version to Install").apply {
            border = BorderFactory.createEmptyBorder(0, 2, 4, 0)
        }
        panel.add(headerLabel, BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout(0, 4))
        centerPanel.add(searchField, BorderLayout.NORTH)
        centerPanel.add(JBScrollPane(versionList), BorderLayout.CENTER)
        panel.add(centerPanel, BorderLayout.CENTER)

        // 底部提示
        val hintLabel = JBLabel("${versions.size} versions available").apply {
            foreground = JBColor.GRAY
            border = BorderFactory.createEmptyBorder(2, 2, 0, 0)
        }
        panel.add(hintLabel, BorderLayout.SOUTH)

        // 创建 Popup
        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField.textEditor)
            .setTitle("Install Node.js")
            .setFocusable(true)
            .setRequestFocus(true)
            .setResizable(true)
            .setMovable(true)
            .createPopup()

        // 双击安装
        versionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = versionList.selectedValue ?: return
                    popup.closeOk(null)
                    doInstall(project, selected)
                }
            }
        })

        // 回车安装
        versionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val selected = versionList.selectedValue ?: return
                    popup.closeOk(null)
                    doInstall(project, selected)
                }
            }
        })

        // 搜索框中按回车 → 安装第一个匹配项
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val selected = versionList.selectedValue ?: return
                        popup.closeOk(null)
                        doInstall(project, selected)
                    }
                    KeyEvent.VK_DOWN -> {
                        versionList.requestFocusInWindow()
                        if (versionList.selectedIndex < filteredModel.size() - 1) {
                            versionList.selectedIndex++
                        }
                    }
                    KeyEvent.VK_UP -> {
                        versionList.requestFocusInWindow()
                        if (versionList.selectedIndex > 0) {
                            versionList.selectedIndex--
                        }
                    }
                }
            }
        })

        popup.showInFocusCenter()
    }

    private fun doInstall(project: Project, version: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing Node.js $version...") {
            override fun run(indicator: ProgressIndicator) {
                val success = NodeInstallService.install(version, indicator = indicator)
                ApplicationManager.getApplication().invokeLater {
                    val type = if (success) NotificationType.INFORMATION else NotificationType.ERROR
                    val msg = if (success) "Node.js $version installed successfully" else "Failed to install Node.js $version"
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Node Manager")
                        .createNotification(msg, type)
                        .notify(project)

                    if (success) {
                        ApplicationManager.getApplication().messageBus
                            .syncPublisher(NodeVersionRefreshListener.TOPIC)
                            .onRefreshRequested()
                    }
                }
            }
        })
    }
}
