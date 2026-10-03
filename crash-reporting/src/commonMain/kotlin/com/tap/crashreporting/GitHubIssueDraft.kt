package com.tap.crashreporting

/** Browsers and GitHub reject long URLs, so longer reports are pasted instead. */
internal const val MAX_ISSUE_URL_LENGTH = 6_000

internal const val PASTE_PROMPT = "Paste the copied report here, replacing this line."

data class GitHubIssueDraft(
    val url: String,
    /** Set when the report is too long for [url] and must be copied for the player to paste. */
    val reportToPaste: String? = null,
)

fun GitHubIssueDraft(
    report: CrashReport,
    config: CrashReportingConfig,
): GitHubIssueDraft {
    val repository = config.githubRepositoryUrl.removeSuffix("/")
    val url = issueUrl(repository, report.title, report.markdown)
    if (url.length <= MAX_ISSUE_URL_LENGTH) return GitHubIssueDraft(url)
    return GitHubIssueDraft(issueUrl(repository, report.title, PASTE_PROMPT), reportToPaste = report.markdown)
}

private fun issueUrl(
    repository: String,
    title: String,
    body: String,
): String = "$repository/issues/new?title=${title.percentEncoded()}&body=${body.percentEncoded()}"

private const val HEX_DIGITS = "0123456789ABCDEF"

internal fun String.percentEncoded(): String =
    buildString {
        for (byte in this@percentEncoded.encodeToByteArray()) {
            val value = byte.toInt() and 0xff
            val char = value.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[value shr 4])
                append(HEX_DIGITS[value and 0xf])
            }
        }
    }
