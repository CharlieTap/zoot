package com.tap.crashreporting

data class CrashReportingConfig(
    val applicationName: String,
    /** Such as `https://github.com/owner/repository`. */
    val githubRepositoryUrl: String,
)
