package com.github.nicestudent.nodemanager.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Tool Window 工厂
 *
 * 包含两个 Tab：
 * - Versions: 已安装的 Node.js 版本管理
 * - Managers: 版本管理器（nvm/fnm）自身的管理
 */
class NodeManagerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // Tab 1: 版本管理
        val versionsPanel = NodeVersionListPanel(project)
        val versionsContent = contentFactory.createContent(versionsPanel.getContent(), "Versions", false)
        toolWindow.contentManager.addContent(versionsContent)

        // Tab 2: 管理器管理
        val managerPanel = ManagerPanel(project)
        val managerContent = contentFactory.createContent(managerPanel.getContent(), "Managers", false)
        toolWindow.contentManager.addContent(managerContent)
    }
}
