package com.tap.crashreporting

import io.github.charlietap.chasm.embedding.error.TrapReason
import io.github.charlietap.chasm.embedding.error.WasmTrap
import io.github.charlietap.chasm.embedding.error.WasmTrapException

private const val MAX_CAUSES = 16
private const val UNKNOWN = "unknown"

/**
 * Describes a failure using Chasm's trap trace when it has one. Other
 * failures report only their type, because their messages can contain
 * paths or other private details.
 */
fun CrashReport(
    failure: Throwable,
    environment: CrashEnvironment,
    config: CrashReportingConfig,
): CrashReport {
    val trap = failure.wasmTrap()
    val summary =
        when {
            trap == null -> failure::class.simpleName ?: "Unknown error"
            trap.reason == TrapReason.OTHER -> "Wasm trap"
            else -> trap.reason.description
        }
    val details =
        buildString {
            appendLine("App: ${config.applicationName} ${environment.applicationVersion}")
            appendLine("Chasm: ${environment.chasmVersion}")
            appendLine("Wasm SHA-256: ${environment.wasmSha256 ?: UNKNOWN}")
            appendLine("Platform: ${environment.platform} ${environment.osVersion ?: UNKNOWN}")
            appendLine("Device: ${environment.deviceModel ?: UNKNOWN}")
            appendLine()
            append(trap?.toString() ?: "$summary (no Wasm stack trace)")
        }
    return CrashReport(
        title = "Crash: $summary",
        details = details,
        markdown = issueBody(details),
    )
}

private fun Throwable.wasmTrap(): WasmTrap? =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSES)
        .firstNotNullOfOrNull { (it as? WasmTrapException)?.trap }

private fun issueBody(details: String): String {
    val longestRun = Regex("`+").findAll(details).maxOfOrNull { it.value.length } ?: 0
    val fence = "`".repeat(maxOf(3, longestRun + 1))
    return buildString {
        appendLine("**What were you doing when this happened?**")
        appendLine()
        appendLine("<!-- Describe what you were doing just before the problem. -->")
        appendLine()
        appendLine("<details>")
        appendLine("<summary>Technical details</summary>")
        appendLine()
        appendLine("${fence}text")
        appendLine(details)
        appendLine(fence)
        appendLine()
        append("</details>")
    }
}
