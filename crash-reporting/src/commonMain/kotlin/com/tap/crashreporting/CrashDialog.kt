package com.tap.crashreporting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * A blocking dialog that lets the player report [report] on GitHub or
 * recover. It cannot be dismissed; only [onRecover] leaves it.
 */
@Composable
fun CrashDialog(
    report: CrashReport,
    config: CrashReportingConfig,
    heading: String,
    message: String,
    recoveryLabel: String,
    onRecover: () -> Unit,
    style: CrashDialogStyle,
    modifier: Modifier = Modifier,
    recoveryNote: String? = null,
) = CrashDialog(report, config, heading, message, recoveryLabel, onRecover, style, rememberCrashReportActions(), modifier, recoveryNote)

@Composable
internal fun CrashDialog(
    report: CrashReport,
    config: CrashReportingConfig,
    heading: String,
    message: String,
    recoveryLabel: String,
    onRecover: () -> Unit,
    style: CrashDialogStyle,
    actions: CrashReportActions,
    modifier: Modifier = Modifier,
    recoveryNote: String? = null,
) {
    val draft = remember(report, config) { GitHubIssueDraft(report, config) }
    var expanded by remember(report) { mutableStateOf(false) }
    var result by remember(report) { mutableStateOf<ActionResult?>(null) }
    val scope = rememberCoroutineScope()
    val headingFocus = remember { FocusRequester() }
    val bodyStyle = style.textStyle.copy(color = style.mutedText, fontSize = 15.sp)
    val noteStyle = style.textStyle.copy(color = style.mutedText, fontSize = 13.sp)

    fun show(outcome: ActionResult) {
        result = outcome
        if (outcome == ActionResult.CopyFailed || outcome == ActionResult.OpenFailed) expanded = true
    }

    Box(modifier.fillMaxSize().testTag("crash-dialog")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(style.scrim)
                .pointerInput(Unit) { detectTapGestures {} }
                .clearAndSetSemantics {},
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .clip(style.shape)
                .background(style.panel)
                .border(0.5.dp, style.border, style.shape)
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics { paneTitle = heading }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            BasicText(
                heading,
                Modifier.focusRequester(headingFocus).focusable().semantics { heading() },
                style = style.textStyle.copy(color = style.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(message, Modifier.padding(top = 6.dp), style = bodyStyle)
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(
                    label = if (draft.reportToPaste == null) "Report on GitHub" else "Copy report and open GitHub",
                    style = style,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { show(actions.open(draft)) } },
                )
                DialogButton(recoveryLabel, style, primary = false, modifier = Modifier.weight(1f), onClick = onRecover)
            }
            if (recoveryNote != null) BasicText(recoveryNote, Modifier.padding(top = 10.dp), style = noteStyle)
            Divider(style, Modifier.padding(top = 16.dp))
            DetailsToggle(expanded, style) { expanded = !expanded }
            if (expanded) {
                SelectionContainer {
                    BasicText(
                        report.details,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .border(0.5.dp, style.border, style.shape)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        style = style.monoStyle,
                    )
                }
                DialogButton(
                    "Copy report",
                    style,
                    primary = false,
                    modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                    onClick = { scope.launch { show(actions.copy(report.markdown)) } },
                )
            }
            Divider(style)
            result?.message?.let { status ->
                BasicText(
                    status,
                    Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = noteStyle.copy(color = style.text),
                )
            }
            BasicText("Reports are public. You can review yours before submitting.", Modifier.padding(top = 12.dp), style = noteStyle)
        }
    }
    LaunchedEffect(Unit) { headingFocus.requestFocus() }
}

@Composable
private fun DialogButton(
    label: String,
    style: CrashDialogStyle,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val content = if (primary) style.onPrimary else style.text
    val surface =
        if (primary) {
            Modifier.background(style.primary)
        } else {
            Modifier.border(0.5.dp, style.border, style.shape)
        }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(style.shape)
            .then(surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            Modifier.weight(1f, fill = false),
            style = style.textStyle.copy(color = content, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
        if (primary) ExternalLinkIcon(content, Modifier.padding(start = 10.dp).size(16.dp))
    }
}

@Composable
private fun DetailsToggle(
    expanded: Boolean,
    style: CrashDialogStyle,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("View details", Modifier.weight(1f), style = style.textStyle.copy(color = style.text, fontSize = 16.sp))
        Chevron(style.mutedText, Modifier.size(16.dp).rotate(if (expanded) 180f else 0f))
    }
}

@Composable
private fun Divider(
    style: CrashDialogStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(style.border))
}

@Composable
private fun Chevron(
    color: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val path =
            Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.35f)
                lineTo(size.width * 0.5f, size.height * 0.7f)
                lineTo(size.width * 0.85f, size.height * 0.35f)
            }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ExternalLinkIcon(
    color: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val stroke = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val box =
            Path().apply {
                moveTo(w * 0.42f, w * 0.12f)
                lineTo(w * 0.1f, w * 0.12f)
                lineTo(w * 0.1f, w * 0.9f)
                lineTo(w * 0.88f, w * 0.9f)
                lineTo(w * 0.88f, w * 0.58f)
            }
        val arrow =
            Path().apply {
                moveTo(w * 0.6f, w * 0.1f)
                lineTo(w * 0.9f, w * 0.1f)
                lineTo(w * 0.9f, w * 0.4f)
            }
        drawPath(box, color, style = stroke)
        drawPath(arrow, color, style = stroke)
        drawLine(color, Offset(w * 0.45f, w * 0.55f), Offset(w * 0.88f, w * 0.12f), stroke.width, StrokeCap.Round)
    }
}
