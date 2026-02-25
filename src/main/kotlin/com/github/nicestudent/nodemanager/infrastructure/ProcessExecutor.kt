package com.github.nicestudent.nodemanager.infrastructure

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 封装命令行执行，统一处理进程调用
 */
object ProcessExecutor {
    private val LOG = Logger.getInstance(ProcessExecutor::class.java)

    /**
     * 执行命令并返回输出结果
     *
     * @param command 命令及参数列表
     * @param workDir 工作目录（可选）
     * @param timeoutMs 超时时间（毫秒），默认 30 秒
     * @return ProcessOutput 包含 stdout、stderr、exitCode
     */
    fun execute(
        command: List<String>,
        workDir: String? = null,
        timeoutMs: Int = 30_000,
    ): ProcessOutput {
        LOG.info("Executing: ${command.joinToString(" ")}")

        val commandLine = GeneralCommandLine(command)
            .withCharset(StandardCharsets.UTF_8)

        if (workDir != null) {
            commandLine.withWorkDirectory(workDir)
        }

        val handler = CapturingProcessHandler(commandLine)
        val output = handler.runProcess(timeoutMs)

        if (output.exitCode != 0) {
            LOG.warn("Command failed (exit=${output.exitCode}): ${output.stderr}")
        }

        return output
    }

    /**
     * 快速执行命令并返回 stdout（去除首尾空格）
     */
    fun executeAndGetOutput(
        command: List<String>,
        workDir: String? = null,
        timeoutMs: Int = 30_000,
    ): String? {
        val output = execute(command, workDir, timeoutMs)
        return if (output.exitCode == 0) output.stdout.trim() else null
    }

    /**
     * 通过临时批处理文件执行命令（仅 Windows）
     *
     * 用于解决 nvm-windows 的「Terminal Only」限制：
     * nvm.exe 检测 GetConsoleWindow() == NULL 时会拒绝执行，
     * 通过 .bat 脚本可确保子进程拥有完整的控制台上下文。
     *
     * @param script 批处理脚本内容（如 "nvm install 20.11.0"）
     * @param timeoutMs 超时时间
     * @return ProcessOutput
     */
    fun executeBatScript(
        script: String,
        timeoutMs: Int = 300_000,
    ): ProcessOutput {
        val tempBat = File.createTempFile("node-manager-", ".bat")
        try {
            // 写入脚本内容，@echo off 隐藏命令本身的回显
            tempBat.writeText("@echo off\r\n$script\r\n", StandardCharsets.UTF_8)
            LOG.info("Executing bat script: $script")

            val commandLine = GeneralCommandLine("cmd.exe", "/c", tempBat.absolutePath)
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(timeoutMs)

            if (output.exitCode != 0) {
                LOG.warn("Bat script failed (exit=${output.exitCode}): ${output.stderr}")
            }

            return output
        } finally {
            tempBat.delete()
        }
    }
}

