package com.github.nicestudent.nodemanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 远程 Node.js 版本信息（来自 Node.js 官方 API）
 */
@Serializable
data class NodeVersion(
    val version: String,
    val date: String,
    val lts: LtsValue = LtsValue.BooleanLts(false),
    val security: Boolean = false,
) {
    /** 语义版本号（去掉 v 前缀） */
    val semver: String get() = version.removePrefix("v")

    /** 主版本号 */
    val major: Int get() = semver.split(".").firstOrNull()?.toIntOrNull() ?: 0

    /** 是否为 LTS 版本 */
    val isLts: Boolean
        get() = when (lts) {
            is LtsValue.StringLts -> true
            is LtsValue.BooleanLts -> lts.value
        }

    /** LTS 代号（如 "Hydrogen", "Iron"） */
    val ltsName: String?
        get() = when (lts) {
            is LtsValue.StringLts -> lts.name
            is LtsValue.BooleanLts -> null
        }
}

/**
 * Node.js API 中 lts 字段的多态类型：可能是 false 或一个字符串代号
 */
@Serializable
sealed class LtsValue {
    @Serializable
    @SerialName("boolean")
    data class BooleanLts(val value: Boolean) : LtsValue()

    @Serializable
    @SerialName("string")
    data class StringLts(val name: String) : LtsValue()
}
