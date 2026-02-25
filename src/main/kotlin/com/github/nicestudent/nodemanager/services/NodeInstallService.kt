package com.github.nicestudent.nodemanager.services

import com.github.nicestudent.nodemanager.infrastructure.NodeRegistryClient
import com.github.nicestudent.nodemanager.manager.VersionManagerRegistry
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Node.js 版本安装/卸载服务
 *
 * 委托给当前活跃的 VersionManager 执行，
 * 如果没有可用管理器则回退到手动下载。
 */
object NodeInstallService {
    private val log = Logger.getInstance(NodeInstallService::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * 安装指定版本的 Node.js
     */
    fun install(version: String, useMirror: Boolean = false, indicator: ProgressIndicator? = null): Boolean {
        val manager = VersionManagerRegistry.getInstance().getActiveManager()
        if (manager != null) {
            return manager.install(version, indicator)
        }

        // 无可用管理器时回退到手动下载
        log.warn("No version manager found, falling back to manual download")
        return installManually(version, useMirror, indicator)
    }

    /**
     * 卸载指定版本
     */
    fun uninstall(version: String): Boolean {
        val manager = VersionManagerRegistry.getInstance().getActiveManager()
        if (manager != null) {
            return manager.uninstall(version)
        }

        log.warn("No version manager available for uninstall")
        return false
    }

    // ==================== 手动下载安装（回退方案） ====================

    private fun installManually(
        version: String,
        useMirror: Boolean,
        indicator: ProgressIndicator?,
    ): Boolean {
        val downloadUrl = NodeRegistryClient.getDownloadUrl(version, useMirror)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "node-manager")
        tempDir.mkdirs()

        val fileName = downloadUrl.substringAfterLast("/")
        val downloadFile = File(tempDir, fileName)

        return try {
            indicator?.text = "Downloading Node.js $version..."
            indicator?.isIndeterminate = false

            downloadFile(downloadUrl, downloadFile, indicator)
            indicator?.text = "Download complete."

            log.info("Downloaded Node.js $version to ${downloadFile.absolutePath}")
            true
        } catch (e: Exception) {
            log.error("Failed to download Node.js $version", e)
            downloadFile.delete()
            false
        }
    }

    private fun downloadFile(url: String, target: File, indicator: ProgressIndicator?) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val contentLength = response.headers().firstValueAsLong("content-length").orElse(-1)

        FileOutputStream(target).use { fos ->
            response.body().use { input ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        indicator?.fraction = totalRead.toDouble() / contentLength
                    }
                }
            }
        }
    }
}
