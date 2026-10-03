package com.tap.crashreporting

/** Null fields are reported as unknown. */
data class CrashEnvironment(
    val applicationVersion: String,
    val chasmVersion: String,
    val wasmSha256: String?,
    val platform: String,
    val osVersion: String?,
    val deviceModel: String?,
)
