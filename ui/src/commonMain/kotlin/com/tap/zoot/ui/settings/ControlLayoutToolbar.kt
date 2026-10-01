package com.tap.zoot.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap.zoot.ui.GameBackHandler
import com.tap.zoot.ui.components.ToggleIndicator
import com.tap.zoot.ui.theme.PanelShape
import com.tap.zoot.ui.theme.ZootColors
import com.tap.zoot.ui.theme.ZootTextStyles

@Composable
internal fun EditLayoutEntry(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(ZootColors.Mint.copy(alpha = 0.09f))
            .border(0.75.dp, ZootColors.Mint, PanelShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoveIcon(ZootColors.Mint, Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            BasicText("Edit layout", style = ZootTextStyles.Label)
            BasicText("Move controls anywhere", style = ZootTextStyles.Label.copy(color = ZootColors.Muted, fontSize = 11.sp))
        }
        Canvas(Modifier.size(12.dp)) {
            drawLine(ZootColors.Mint, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.85f, size.height / 2), 1.5.dp.toPx())
            drawLine(ZootColors.Mint, Offset(size.width * 0.85f, size.height / 2), Offset(size.width * 0.35f, size.height), 1.5.dp.toPx())
        }
    }
}

@Composable
internal fun ControlLayoutToolbar(
    canUndo: Boolean,
    touchAreas: Boolean,
    snap: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onTouchAreasChange: (Boolean) -> Unit,
    onSnapChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameBackHandler(onBack = onCancel)
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                .semantics { paneTitle = "Edit control layout" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.width(540.dp).editorPanel().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MoveIcon(ZootColors.Text, Modifier.padding(horizontal = 6.dp).size(20.dp))
                BasicText(
                    "Edit layout",
                    Modifier.weight(1f).semantics {
                        heading()
                    },
                    style = ZootTextStyles.Label.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
                ToolbarDivider()
                EditorButton("Undo", onUndo, enabled = canUndo, icon = EditorIcon.Undo)
                EditorButton("Reset", onReset, icon = EditorIcon.Reset)
                ToolbarDivider()
                EditorButton("Cancel", onCancel)
                EditorButton("Done", onDone, primary = true)
            }
            BasicText(
                "Drag any control to move it",
                Modifier.padding(top = 8.dp),
                style = ZootTextStyles.Label.copy(color = ZootColors.Muted),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .editorPanel()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorToggle("Touch areas", touchAreas, onTouchAreasChange)
            ToolbarDivider()
            EditorToggle("Snap", snap, onSnapChange)
        }
    }
}

private fun Modifier.editorPanel(): Modifier =
    clip(PanelShape)
        .background(ZootColors.Panel)
        .border(0.75.dp, ZootColors.Border, PanelShape)
        .pointerInput(Unit) { detectTapGestures {} }

@Composable
private fun EditorToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(label, style = ZootTextStyles.Label)
        ToggleIndicator(checked)
    }
}

@Composable
private fun ToolbarDivider() {
    Spacer(Modifier.width(0.5.dp).height(24.dp).background(ZootColors.Border))
}

private enum class EditorIcon { Undo, Reset }

@Composable
private fun EditorButton(
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    icon: EditorIcon? = null,
) {
    val color =
        when {
            primary -> ZootColors.Panel
            enabled -> ZootColors.Text
            else -> ZootColors.Muted.copy(alpha = 0.4f)
        }
    Row(
        Modifier
            .clip(PanelShape)
            .background(if (primary) ZootColors.Mint else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = if (primary) 20.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Canvas(Modifier.size(16.dp)) {
                val stroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round)
                if (icon == EditorIcon.Reset) {
                    drawArc(
                        color,
                        220f,
                        310f,
                        false,
                        Offset(size.width * 0.1f, size.height * 0.1f),
                        Size(
                            size.width * 0.8f,
                            size.height * 0.8f,
                        ),
                        style = stroke,
                    )
                } else {
                    drawArc(
                        color,
                        -90f,
                        230f,
                        false,
                        Offset(size.width * 0.1f, size.height * 0.3f),
                        Size(
                            size.width * 0.8f,
                            size.height * 0.65f,
                        ),
                        style = stroke,
                    )
                    drawLine(
                        color,
                        Offset(size.width / 2, size.height * 0.3f),
                        Offset(size.width * 0.05f, size.height * 0.3f),
                        stroke.width,
                    )
                }
                drawLine(
                    color,
                    Offset(size.width * 0.05f, size.height * 0.05f),
                    Offset(size.width * 0.05f, size.height * 0.3f),
                    stroke.width,
                )
                drawLine(color, Offset(size.width * 0.05f, size.height * 0.3f), Offset(size.width * 0.3f, size.height * 0.3f), stroke.width)
            }
        }
        BasicText(
            title,
            style = ZootTextStyles.Label.copy(color = color, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal),
        )
    }
}

@Composable
internal fun MoveIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val centre = size.width / 2f
        val low = size.width * 0.1f
        val high = size.width * 0.9f
        val inset = size.width * 0.13f
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(low, centre), Offset(high, centre), stroke)
        drawLine(color, Offset(centre, low), Offset(centre, high), stroke)
        for (side in 0..1) {
            val tip = if (side == 0) low else high
            val inner = if (side == 0) tip + inset else tip - inset
            drawLine(color, Offset(tip, centre), Offset(inner, centre - inset), stroke)
            drawLine(color, Offset(tip, centre), Offset(inner, centre + inset), stroke)
            drawLine(color, Offset(centre, tip), Offset(centre - inset, inner), stroke)
            drawLine(color, Offset(centre, tip), Offset(centre + inset, inner), stroke)
        }
    }
}
