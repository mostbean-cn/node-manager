package com.github.nicestudent.nodemanager.infrastructure

import com.github.nicestudent.nodemanager.model.LtsValue
import com.github.nicestudent.nodemanager.model.NodeVersion
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Node.js 官方 Registry 客户端
 * 从 https://nodejs.org/dist/index.json 拉取版本列表
 */
object NodeRegistryClient {
    private val LOG = Logger.getInstance(NodeRegistryClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private const val DEFAULT_REGISTRY = "https://nodejs.org/dist/index.json"
    private const val TAOBAO_REGISTRY = "https://npmmirror.com/mirrors/node/index.json"

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /**
     * 获取所有可用的 Node.js 版本列表
     *
     * @param mirror 是否使用淘宝镜像
     * @return 版本列表，按版本号降序
     */
    fun fetchVersions(mirror: Boolean = false): List<NodeVersion> {
        val registryUrl = if (mirror) TAOBAO_REGISTRY else DEFAULT_REGISTRY

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(registryUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                LOG.warn("Registry returned status ${response.statusCode()}")
                return emptyList()
            }

            parseVersions(response.body())
        } catch (e: Exception) {
            LOG.error("Failed to fetch Node.js versions from $registryUrl", e)
            emptyList()
        }
    }

    /**
     * 获取指定版本的下载 URL
     *
     * @param version 版本号（如 "v20.11.0"）
     * @param mirror 是否使用镜像
     * @return 下载 URL
     */
    fun getDownloadUrl(version: String, mirror: Boolean = false): String {
        val baseUrl = if (mirror) "https://npmmirror.com/mirrors/node" else "https://nodejs.org/dist"
        val os = getOsName()
        val arch = getArchName()
        val ext = if (os == "win") "zip" else "tar.gz"
        return "$baseUrl/$version/node-$version-$os-$arch.$ext"
    }

    private fun parseVersions(body: String): List<NodeVersion> {
        val jsonArray = json.parseToJsonElement(body).jsonArray
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val ltsValue = obj["lts"]?.let { ltsElement ->
                when {
                    ltsElement is JsonPrimitive && ltsElement.booleanOrNull != null ->
                        LtsValue.BooleanLts(ltsElement.boolean)
                    ltsElement is JsonPrimitive && ltsElement.isString ->
                        LtsValue.StringLts(ltsElement.content)
                    else -> LtsValue.BooleanLts(false)
                }
            } ?: LtsValue.BooleanLts(false)

            NodeVersion(
                version = obj["version"]?.jsonPrimitive?.content ?: "",
                date = obj["date"]?.jsonPrimitive?.content ?: "",
                lts = ltsValue,
                security = obj["security"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    private fun getOsName(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "win"
        System.getProperty("os.name").lowercase().contains("mac") -> "darwin"
        else -> "linux"
    }

    private fun getArchName(): String = when (System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> "x64"
    }
}
