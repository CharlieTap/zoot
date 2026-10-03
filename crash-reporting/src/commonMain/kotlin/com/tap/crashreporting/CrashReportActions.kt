package com.tap.crashreporting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.CancellationException

internal class CrashReportActions(
    private val openUri: (String) -> Unit,
    private val copyText: suspend (String) -> Unit,
) {
    suspend fun open(draft: GitHubIssueDraft): ActionResult {
        val report = draft.reportToPaste
        if (report != null && copy(report) == ActionResult.CopyFailed) return ActionResult.CopyFailed
        return try {
            openUri(draft.url)
            if (report != null) ActionResult.CopiedForPasting else ActionResult.Opened
        } catch (failure: Exception) {
            ActionResult.OpenFailed
        }
    }

    suspend fun copy(report: String): ActionResult =
        try {
            copyText(report)
            ActionResult.Copied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ActionResult.CopyFailed
        }
}

internal enum class ActionResult(
    val message: String?,
) {
    Opened(null),
    CopiedForPasting("Report copied. Paste it into the issue on GitHub."),
    Copied("Report copied."),
    CopyFailed("Couldn't copy the report. You can select it in the details below."),
    OpenFailed("Couldn't open GitHub. You can copy the report instead."),
}

@Composable
internal fun rememberCrashReportActions(): CrashReportActions {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    return remember(uriHandler, clipboard) {
        CrashReportActions(
            openUri = uriHandler::openUri,
            copyText = { text -> clipboard.setClipEntry(plainTextClipEntry(text)) },
        )
    }
}

internal expect fun plainTextClipEntry(text: String): ClipEntry
