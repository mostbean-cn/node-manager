package com.github.nicestudent.nodemanager.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * 项目级 Node.js 配置管理服务
 *
 * 职责：
 * - 读写 .nvmrc / .node-version 文件
 * - 查询项目绑定的 Node.js 版本
 */
@Service(Service.Level.PROJECT)
class ProjectConfigService(private val project: Project) {

    private val log = Logger.getInstance(ProjectConfigService::class.java)

    companion object {
        private const val NVMRC = ".nvmrc"
        private const val NODE_VERSION = ".node-version"

        fun getInstance(project: Project): ProjectConfigService =
            project.getService(ProjectConfigService::class.java)
    }

    /**
     * 读取项目绑定的 Node.js 版本
     * 优先读取 .nvmrc，其次 .node-version
     *
     * @return 版本号（如 "20.11.0" 或 "v20.11.0"），未找到返回 null
     */
    fun getProjectNodeVersion(): String? {
        val basePath = project.basePath ?: return null

        // 优先 .nvmrc
        val nvmrc = File(basePath, NVMRC)
        if (nvmrc.exists()) {
            val version = nvmrc.readText().trim()
            if (version.isNotBlank()) return version
        }

        // 其次 .node-version
        val nodeVersion = File(basePath, NODE_VERSION)
        if (nodeVersion.exists()) {
            val version = nodeVersion.readText().trim()
            if (version.isNotBlank()) return version
        }

        return null
    }

    /**
     * 设置项目绑定的 Node.js 版本（写入 .nvmrc）
     *
     * @param version 版本号
     */
    fun setProjectNodeVersion(version: String) {
        val basePath = project.basePath ?: return
        val nvmrc = File(basePath, NVMRC)

        try {
            nvmrc.writeText(version.removePrefix("v") + "\n")
            log.info("Set project Node.js version to $version in $NVMRC")
        } catch (e: Exception) {
            log.error("Failed to write $NVMRC", e)
        }
    }

    /**
     * 检查 package.json 中 engines.node 的推荐版本
     */
    fun getEnginesNodeRequirement(): String? {
        val basePath = project.basePath ?: return null
        val packageJson = File(basePath, "package.json")
        if (!packageJson.exists()) return null

        return try {
            val content = packageJson.readText()
            // 简单正则提取 engines.node 字段
            val regex = """"engines"\s*:\s*\{[^}]*"node"\s*:\s*"([^"]+)"[^}]*\}""".toRegex()
            regex.find(content)?.groupValues?.get(1)
        } catch (e: Exception) {
            log.warn("Failed to parse package.json", e)
            null
        }
    }
}
