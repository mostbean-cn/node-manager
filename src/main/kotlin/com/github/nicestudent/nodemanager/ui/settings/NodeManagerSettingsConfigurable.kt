package com.github.nicestudent.nodemanager.ui.settings

import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Node Manager 设置面板
 */
class NodeManagerSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var mirrorField: JBTextField? = null
    private var autoDetectCheckbox: JBCheckBox? = null
    private var managerComboBox: JComboBox<String>? = null

    override fun getDisplayName(): String = "Node Manager"

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout(0, 12))

        // === 版本管理器区域 ===
        val managerSection = createManagerSection()
        panel.add(managerSection, BorderLayout.NORTH)

        // === 下载设置区域 ===
        val downloadSection = createDownloadSection()
        panel.add(downloadSection, BorderLayout.CENTER)

        mainPanel = panel
        loadSettings()
        return panel
    }

    private fun createManagerSection(): JPanel {
        val section = JPanel(GridBagLayout())
        section.border = BorderFactory.createTitledBorder("Version Manager")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
        }

        val registry = VersionManagerRegistry.getInstance()
        val available = registry.detectAvailable()

        // 行 1：检测到的管理器 + 安装路径
        gbc.gridx = 0; gbc.gridy = 0
        section.add(JBLabel("Detected:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        val detectedText = if (available.isNotEmpty()) {
            available.joinToString(" | ") { manager ->
                val ver = manager.getManagerVersion()
                if (ver != null) "${manager.displayName} v$ver" else manager.displayName
            }
        } else {
            "No version manager found"
        }
        section.add(JBLabel(detectedText), gbc)

        // 行 2：管理器选择
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        section.add(JBLabel("Preferred:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        val managerNames = available.map { it.name }.toTypedArray()
        managerComboBox = JComboBox(if (managerNames.isEmpty()) arrayOf("(none)") else managerNames)
        managerComboBox?.isEnabled = managerNames.size > 1
        section.add(managerComboBox!!, gbc)

        // 行 3：当前管理器状态
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        section.add(JBLabel("Active:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        val activeManager = registry.getActiveManager()
        val activeText = if (activeManager != null) {
            val ver = activeManager.getManagerVersion()
            val versionText = if (ver != null) " v$ver" else ""
            "${activeManager.displayName}$versionText  ✓"
        } else {
            "—"
        }
        section.add(JBLabel(activeText), gbc)

        // 行 4：安装指南链接
        if (available.isEmpty()) {
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2
            val linkPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
            linkPanel.add(JBLabel("Install: "))

            val nvmLink = createHyperlink("nvm-windows", "https://github.com/coreybutler/nvm-windows/releases")
            linkPanel.add(nvmLink)
            linkPanel.add(JBLabel(" | "))
            val fnmLink = createHyperlink("fnm", "https://github.com/Schniz/fnm#installation")
            linkPanel.add(fnmLink)

            section.add(linkPanel, gbc)
        }

        return section
    }

    private fun createHyperlink(text: String, url: String): JButton {
        return JButton("<html><a href=''>$text</a></html>").apply {
            isBorderPainted = false
            isContentAreaFilled = false
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            addActionListener { BrowserUtil.browse(url) }
        }
    }

    private fun createDownloadSection(): JPanel {
        val section = JPanel(GridBagLayout())
        section.border = BorderFactory.createTitledBorder("Download Settings")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
        }

        // 行 1：镜像源
        gbc.gridx = 0; gbc.gridy = 0
        section.add(JBLabel("Mirror URL:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        mirrorField = JBTextField()
        mirrorField?.emptyText?.text = "https://nodejs.org/dist (default)"
        section.add(mirrorField!!, gbc)

        // 行 2：自动检测
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        autoDetectCheckbox = JBCheckBox("Auto-detect Node.js version on project open", true)
        section.add(autoDetectCheckbox!!, gbc)

        return section
    }

    override fun isModified(): Boolean {
        val settings = NodeManagerSettings.getInstance()
        return mirrorField?.text != settings.state.mirrorUrl ||
                autoDetectCheckbox?.isSelected != settings.state.autoDetect ||
                managerComboBox?.selectedItem?.toString() != settings.state.preferredManager
    }

    override fun apply() {
        val settings = NodeManagerSettings.getInstance()
        settings.state.mirrorUrl = mirrorField?.text ?: ""
        settings.state.autoDetect = autoDetectCheckbox?.isSelected ?: true
        settings.state.preferredManager = managerComboBox?.selectedItem?.toString() ?: ""

        // 应用管理器偏好
        val preferred = settings.state.preferredManager
        if (preferred.isNotBlank() && preferred != "(none)") {
            VersionManagerRegistry.getInstance().setActiveManager(preferred)
        }
    }

    override fun reset() {
        loadSettings()
    }

    private fun loadSettings() {
        val settings = NodeManagerSettings.getInstance()
        mirrorField?.text = settings.state.mirrorUrl
        autoDetectCheckbox?.isSelected = settings.state.autoDetect

        // 选中当前偏好管理器
        val preferred = settings.state.preferredManager
        if (preferred.isNotBlank()) {
            managerComboBox?.selectedItem = preferred
        }
    }
}

// ==================== 持久化配置 ====================

@Service
@State(name = "NodeManagerSettings", storages = [Storage("NodeManagerSettings.xml")])
class NodeManagerSettings : PersistentStateComponent<NodeManagerSettings.State> {

    data class State(
        var mirrorUrl: String = "",
        var autoDetect: Boolean = true,
        var preferredManager: String = "",
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): NodeManagerSettings =
            ApplicationManager.getApplication().getService(NodeManagerSettings::class.java)
    }
}
