package com.github.nicestudent.nodemanager.ui.toolwindow

import com.github.nicestudent.nodemanager.infrastructure.FileSystemHelper
import com.github.nicestudent.nodemanager.infrastructure.ProcessExecutor
import com.github.nicestudent.nodemanager.manager.FnmVersionManager
import com.github.nicestudent.nodemanager.manager.NvmVersionManager
import com.github.nicestudent.nodemanager.manager.VersionManager
import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.*

/**
 * 版本管理器管理面板
 *
 * 展示各个版本管理器（nvm、fnm）的状态、路径和可用操作。
 * 当两个管理器同时安装时，显示冲突警告并提供禁用操作。
 */
class ManagerPanel(private val project: Project) {

    fun getContent(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // 标题
        val titleLabel = JBLabel("Version Managers").apply {
            font = font.deriveFont(Font.BOLD, 14f)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        // 管理器卡片列表
        val cardsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
        }

        val nvm = NvmVersionManager()
        val fnm = FnmVersionManager()

        // 冲突警告（仅当 nvm 启用且 fnm shell 已配置时显示）
        if (nvm.isAvailable() && fnm.isAvailable() && isNvmEnabled() && isFnmShellConfigured()) {
            cardsPanel.add(createConflictWarning())
            cardsPanel.add(Box.createVerticalStrut(8))
        }

        // NVM 卡片
        cardsPanel.add(createManagerCard(
            manager = nvm,
            installUrl = "https://github.com/coreybutler/nvm-windows/releases",
            description = "Node Version Manager for Windows",
        ))
        cardsPanel.add(Box.createVerticalStrut(8))

        // FNM 卡片
        cardsPanel.add(createManagerCard(
            manager = fnm,
            installUrl = "https://github.com/Schniz/fnm#installation",
            description = "Fast Node Manager (cross-platform)",
        ))

        cardsPanel.add(Box.createVerticalGlue())

        mainPanel.add(JBScrollPane(cardsPanel), BorderLayout.CENTER)

        return mainPanel
    }

    // ==================== 冲突警告 ====================

    /**
     * 冲突警告横幅
     */
    private fun createConflictWarning(): JPanel {
        val banner = JPanel(BorderLayout(8, 4))
        banner.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xE5A000, 0xE5A000), 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12),
        )
        banner.background = JBColor(Color(255, 243, 205), Color(60, 50, 20))
        banner.maximumSize = Dimension(Int.MAX_VALUE, 80)

        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        textPanel.add(JBLabel("⚠ Conflict: nvm and fnm are both active").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = JBColor(0xB8860B, 0xE5A000)
        })
        textPanel.add(Box.createVerticalStrut(4))
        textPanel.add(JBLabel("Both are controlling PATH. Disable one below to avoid conflicts.").apply {
            foreground = JBColor(0x8B6914, 0xC8A000)
        })

        banner.add(textPanel, BorderLayout.CENTER)

        return banner
    }

    /**
     * 禁用 nvm（执行 nvm off）
     */
    private fun disableNvm() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Disabling nvm...") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val success = if (SystemInfo.isWindows) {
                        executeNvmOffWindows()
                    } else {
                        val output = ProcessExecutor.execute(
                            listOf("bash", "-c", "source ~/.nvm/nvm.sh && nvm deactivate"),
                            timeoutMs = 10_000,
                        )
                        output.exitCode == 0
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (success) {
                            showRestartDialog("nvm has been disabled.")
                        } else {
                            showNotification("Failed to disable nvm", NotificationType.ERROR)
                        }
                    }
                } catch (e: Exception) {
                    showNotification("Failed to disable nvm: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    /**
     * Windows 上通过 VBScript 隐藏窗口执行 nvm off
     */
    private fun executeNvmOffWindows(): Boolean {
        val tempBat = File.createTempFile("node-manager-", ".bat")
        val tempVbs = File.createTempFile("node-manager-", ".vbs")
        try {
            tempBat.writeText(
                "@echo off\r\nnvm off\r\nexit /b %ERRORLEVEL%\r\n",
                StandardCharsets.UTF_8,
            )
            tempVbs.writeText(
                "Set WshShell = CreateObject(\"WScript.Shell\")\r\n" +
                "exitCode = WshShell.Run(\"cmd.exe /c \"\"${tempBat.absolutePath}\"\"\", 0, True)\r\n" +
                "WScript.Quit(exitCode)\r\n",
                StandardCharsets.UTF_8,
            )

            val commandLine = GeneralCommandLine("cscript", "//Nologo", tempVbs.absolutePath)
                .withCharset(StandardCharsets.UTF_8)
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(10_000)
            return output.exitCode == 0
        } finally {
            tempBat.delete()
            tempVbs.delete()
        }
    }

    /**
     * 移除 fnm shell 集成（从 PowerShell Profile 中删除 fnm env 行）
     */
    private fun removeFnmShell() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Removing fnm shell integration...") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val profilePath = getPowerShellProfilePath()
                    if (profilePath == null) {
                        showNotification("Cannot determine PowerShell profile path", NotificationType.ERROR)
                        return
                    }

                    val profileFile = File(profilePath)
                    if (!profileFile.exists()) {
                        showNotification("PowerShell profile not found", NotificationType.WARNING)
                        return
                    }

                    val content = profileFile.readText()
                    if (!content.contains("fnm env")) {
                        showNotification("fnm shell integration not found in profile", NotificationType.INFORMATION)
                        return
                    }

                    // 移除 fnm env 相关行
                    val newContent = content.lines()
                        .filter { line ->
                            !line.contains("fnm env") && !line.trim().startsWith("# fnm")
                        }
                        .joinToString("\n")
                        .trimEnd() + "\n"

                    profileFile.writeText(newContent)

                    ApplicationManager.getApplication().invokeLater {
                        showRestartDialog("fnm shell integration has been removed.")
                    }
                } catch (e: Exception) {
                    showNotification("Failed to remove fnm shell: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    // ==================== 管理器卡片 ====================

    private fun createManagerCard(
        manager: VersionManager,
        installUrl: String,
        description: String,
    ): JPanel {
        val card = JPanel(GridBagLayout())
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12),
        )
        card.maximumSize = Dimension(Int.MAX_VALUE, 200)

        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(2, 0, 2, 8)
        }

        val available = manager.isAvailable()
        val registry = VersionManagerRegistry.getInstance()
        val isActive = registry.getActiveManager()?.name == manager.name

        var row = 0

        // 名称
        gbc.gridx = 0; gbc.gridy = row
        card.add(JBLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        card.add(JBLabel(manager.displayName).apply {
            font = font.deriveFont(Font.BOLD)
        }, gbc)

        // 状态
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        card.add(JBLabel("Status:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        if (available) {
            val activeTag = if (isActive) " (active)" else ""
            card.add(JBLabel("✓ Installed$activeTag").apply {
                foreground = JBColor(0x5FA04E, 0x6BBF59)
            }, gbc)
        } else {
            card.add(JBLabel("✗ Not installed").apply {
                foreground = JBColor.GRAY
            }, gbc)
        }

        if (available) {
            // 版本
            row++
            gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            card.add(JBLabel("Version:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            card.add(JBLabel(manager.getManagerVersion() ?: "—"), gbc)

            // 安装路径
            row++
            gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            card.add(JBLabel("Path:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val path = getManagerPath(manager)
            card.add(JBLabel(path ?: "—").apply {
                foreground = JBColor.GRAY
                toolTipText = path
            }, gbc)

            // 管理的版本数量
            row++
            gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            card.add(JBLabel("Versions:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val count = try { manager.listInstalled().size } catch (e: Exception) { 0 }
            card.add(JBLabel("$count installed"), gbc)

            // nvm 专属：Enabled/Disabled 状态（通过 NVM_SYMLINK 检测）
            if (manager.name == "nvm" && SystemInfo.isWindows) {
                row++
                gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                card.add(JBLabel("Enabled:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0

                val nvmEnabled = isNvmEnabled()
                val enabledPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
                if (nvmEnabled) {
                    enabledPanel.add(JBLabel("✓ On").apply {
                        foreground = JBColor(0x5FA04E, 0x6BBF59)
                    })
                    enabledPanel.add(JButton("Disable").apply {
                        toolTipText = "Run 'nvm off'"
                        addActionListener { toggleNvm(false, card) }
                    })
                } else {
                    enabledPanel.add(JBLabel("✗ Off").apply {
                        foreground = JBColor(0xE5A000, 0xE5A000)
                    })
                    enabledPanel.add(JButton("Enable").apply {
                        toolTipText = "Run 'nvm on'"
                        addActionListener { toggleNvm(true, card) }
                    })
                }
                card.add(enabledPanel, gbc)
            }

            // fnm 专属：Shell 集成检测
            if (manager.name == "fnm" && SystemInfo.isWindows) {
                row++
                gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                card.add(JBLabel("Shell:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0

                if (isFnmShellConfigured()) {
                    val configuredPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
                    configuredPanel.add(JBLabel("✓ Configured").apply {
                        foreground = JBColor(0x5FA04E, 0x6BBF59)
                    })
                    configuredPanel.add(JButton("Remove").apply {
                        toolTipText = "Remove fnm env from PowerShell profile"
                        addActionListener { removeFnmShell() }
                    })
                    card.add(configuredPanel, gbc)
                } else {
                    val shellPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
                    shellPanel.add(JBLabel("✗ Not configured").apply {
                        foreground = JBColor(0xE5A000, 0xE5A000)
                    })
                    shellPanel.add(JButton("Setup").apply {
                        toolTipText = "Add fnm env to PowerShell profile"
                        addActionListener { setupFnmShell(card) }
                    })
                    card.add(shellPanel, gbc)
                }
            }
        }

        // 描述
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        card.add(JBLabel("About:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        card.add(JBLabel(description).apply { foreground = JBColor.GRAY }, gbc)

        // 操作按钮
        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

        if (available) {
            buttonPanel.add(JButton("Open Directory").apply {
                addActionListener {
                    val path = getManagerPath(manager)
                    if (path != null) Desktop.getDesktop().open(File(path))
                }
            })
        }

        buttonPanel.add(createLinkButton(
            if (available) "Homepage" else "Installation Guide",
            installUrl,
        ))
        card.add(buttonPanel, gbc)

        return card
    }

    // ==================== fnm Shell 集成 ====================

    private fun isFnmShellConfigured(): Boolean {
        return try {
            val profilePath = getPowerShellProfilePath() ?: return false
            val profileFile = File(profilePath)
            profileFile.exists() && profileFile.readText().contains("fnm env")
        } catch (e: Exception) {
            false
        }
    }

    private fun setupFnmShell(card: JPanel) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Setting up fnm shell integration...") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val profilePath = getPowerShellProfilePath()
                    if (profilePath == null) {
                        showNotification("Cannot determine PowerShell profile path", NotificationType.ERROR)
                        return
                    }

                    val profileFile = File(profilePath)
                    profileFile.parentFile?.mkdirs()

                    if (profileFile.exists() && profileFile.readText().contains("fnm env")) {
                        showNotification("fnm shell integration is already configured", NotificationType.INFORMATION)
                        return
                    }

                    profileFile.appendText(
                        "\n# fnm (Fast Node Manager) shell integration\n" +
                        "fnm env --use-on-cd --shell powershell | Out-String | Invoke-Expression\n"
                    )

                    ApplicationManager.getApplication().invokeLater {
                        showRestartDialog("fnm shell integration configured!")
                        SwingUtilities.getWindowAncestor(card)?.repaint()
                    }
                } catch (e: Exception) {
                    showNotification("Failed to setup fnm shell: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun getPowerShellProfilePath(): String? {
        if (!SystemInfo.isWindows) return null
        val shells = listOf(
            listOf("pwsh", "-NoProfile", "-Command", "echo \$PROFILE"),
            listOf("powershell", "-NoProfile", "-Command", "echo \$PROFILE"),
        )
        for (shell in shells) {
            try {
                val output = ProcessExecutor.execute(shell, timeoutMs = 5_000)
                if (output.exitCode == 0) {
                    val path = output.stdout.trim()
                    if (path.isNotBlank()) return path
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    // ==================== nvm on/off ====================

    /**
     * 检测 nvm 是否处于 enabled 状态
     *
     * nvm on 会创建 NVM_SYMLINK 目录（symlink 到当前版本），
     * nvm off 会删除该目录。
     */
    private fun isNvmEnabled(): Boolean {
        val symlink = System.getenv("NVM_SYMLINK") ?: return false
        return File(symlink).exists()
    }

    /**
     * 切换 nvm on/off
     */
    private fun toggleNvm(enable: Boolean, card: JPanel) {
        val cmd = if (enable) "on" else "off"
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running nvm $cmd...") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val success = executeNvmCommand(cmd)
                    ApplicationManager.getApplication().invokeLater {
                        if (success) {
                            showRestartDialog("nvm $cmd executed successfully.")
                        } else {
                            showNotification("Failed to run nvm $cmd", NotificationType.ERROR)
                        }
                        // 刷新整个面板
                        SwingUtilities.getWindowAncestor(card)?.repaint()
                    }
                } catch (e: Exception) {
                    showNotification("Failed to run nvm $cmd: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    /**
     * Windows 上通过 VBScript 隐藏窗口执行 nvm 命令
     */
    private fun executeNvmCommand(args: String): Boolean {
        val tempBat = File.createTempFile("node-manager-", ".bat")
        val tempVbs = File.createTempFile("node-manager-", ".vbs")
        try {
            tempBat.writeText(
                "@echo off\r\nnvm $args\r\nexit /b %ERRORLEVEL%\r\n",
                StandardCharsets.UTF_8,
            )
            tempVbs.writeText(
                "Set WshShell = CreateObject(\"WScript.Shell\")\r\n" +
                "exitCode = WshShell.Run(\"cmd.exe /c \"\"${tempBat.absolutePath}\"\"\", 0, True)\r\n" +
                "WScript.Quit(exitCode)\r\n",
                StandardCharsets.UTF_8,
            )

            val commandLine = GeneralCommandLine("cscript", "//Nologo", tempVbs.absolutePath)
                .withCharset(StandardCharsets.UTF_8)
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(10_000)
            return output.exitCode == 0
        } finally {
            tempBat.delete()
            tempVbs.delete()
        }
    }

    // ==================== 通用方法 ====================

    private fun getManagerPath(manager: VersionManager): String? {
        return when (manager.name) {
            "nvm" -> FileSystemHelper.getNvmDir()?.absolutePath
            "fnm" -> FileSystemHelper.getFnmDir()?.absolutePath
            else -> null
        }
    }

    private fun createLinkButton(text: String, url: String): JButton {
        return JButton(text).apply {
            addActionListener { BrowserUtil.browse(url) }
        }
    }

    private fun showNotification(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Node Manager")
            .createNotification(message, type)
            .notify(project)
    }

    /**
     * 弹出重启确认对话框
     *
     * 提示用户环境变更需要重启 IDEA 才能生效，
     * 提供「立即重启」和「稍后」两个选项。
     */
    private fun showRestartDialog(message: String) {
        val result = Messages.showYesNoDialog(
            project,
            "$message\n\nRestart IDEA for changes to take effect?",
            "Restart Required",
            "Restart Now",
            "Later",
            Messages.getQuestionIcon(),
        )
        if (result == Messages.YES) {
            ApplicationManager.getApplication().restart()
        }
    }
}
