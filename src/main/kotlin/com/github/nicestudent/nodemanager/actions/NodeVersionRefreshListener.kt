package com.github.nicestudent.nodemanager.actions

import com.intellij.util.messages.Topic

/**
 * 版本列表刷新事件监听器
 *
 * 用于在安装/卸载操作完成后通知 UI 刷新版本列表。
 */
interface NodeVersionRefreshListener {
    companion object {
        val TOPIC = Topic.create("NodeVersionRefresh", NodeVersionRefreshListener::class.java)
    }

    fun onRefreshRequested()
}
