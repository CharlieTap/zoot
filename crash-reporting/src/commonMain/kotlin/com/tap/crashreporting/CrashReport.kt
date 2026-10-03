package com.tap.crashreporting

data class CrashReport(
    val title: String,
    /** Plain text technical details shown to the player. */
    val details: String,
    /** The complete issue body. */
    val markdown: String,
)
