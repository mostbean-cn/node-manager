package com.github.nicestudent.nodemanager.model

/**
 * 本地已安装的 Node.js 实例信息
 */
data class NodeInstallation(
    /** 版本号，如 "v20.11.0" */
    val version: String,

    /** 安装路径 */
    val path: String,

    /** 是否为当前激活的版本 */
    val isActive: Boolean = false,

    /** 来源：nvm / fnm / volta / system */
    val source: Source = Source.SYSTEM,
) {
    /** 语义版本号（去掉 v 前缀） */
    val semver: String get() = version.removePrefix("v")

    /** 版本来源枚举 */
    enum class Source(val displayName: String) {
        NVM("nvm"),
        FNM("fnm"),
        VOLTA("volta"),
        SYSTEM("system"),
    }
}
