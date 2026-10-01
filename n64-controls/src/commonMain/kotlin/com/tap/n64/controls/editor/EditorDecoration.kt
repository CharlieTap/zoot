package com.tap.n64.controls.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.tap.n64.controls.ControlId
import com.tap.n64.controls.internal.ControlArtwork

private val Mint = Color(0xff9cf0cf)
private val Panel = Color(0xf5141719)
private val Grey = Color(0xffa5b1b9)
private val LabelBorder = Color(0xff61696e)
private val LabelText = Color(0xfff4f5f5)

@Composable
internal fun EditorDecoration(
    state: N64ControlsEditorState,
    artwork: ControlArtwork,
) {
    val measurer = rememberTextMeasurer(cacheSize = ControlId.entries.size)
    Spacer(
        Modifier.drawWithCache {
            val unit = minOf(density, size.height / 400f, size.width / 720f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f * unit, 4f * unit))
            val outline = Stroke(unit, pathEffect = dash)
            val selectedOutline = Stroke(2f * unit, pathEffect = dash)
            val labels =
                ControlId.entries.map {
                    measurer.measure(
                        it.label,
                        TextStyle(fontFamily = artwork.editorLabels, fontWeight = FontWeight.Medium, fontSize = (11f * unit / density).sp),
                        density = Density(density, 1f),
                    )
                }
            onDrawBehind {
                state.drag?.takeIf { it.moved }?.let { drag ->
                    drawRoundRect(
                        Grey.copy(alpha = 90f / 255),
                        drag.origin.topLeftOffset,
                        drag.origin.sizeF,
                        CornerRadius(12f * unit),
                        style = outline,
                    )
                }
                for (id in ControlId.entries) {
                    val selected = id == state.selected
                    if (!state.showTouchAreas && !selected) continue
                    if (state.geometry == null) continue
                    val bounds = state.bounds(id)
                    drawRoundRect(
                        if (selected) Mint else Grey,
                        bounds.topLeftOffset,
                        bounds.sizeF,
                        CornerRadius(10f * unit),
                        style = if (selected) selectedOutline else outline,
                    )
                }
                if (!state.guideX.isNaN()) {
                    drawLine(Mint, Offset(state.guideX, 0f), Offset(state.guideX, size.height), unit, pathEffect = dash)
                }
                if (!state.guideY.isNaN()) {
                    drawLine(Mint, Offset(0f, state.guideY), Offset(size.width, state.guideY), unit, pathEffect = dash)
                }
                val selected = state.selected
                if (selected != null && state.geometry != null) {
                    drawSelectionLabel(state.bounds(selected), labels[selected.ordinal], unit)
                }
            }
        },
    )
}

private fun DrawScope.drawSelectionLabel(
    bounds: IntRect,
    label: TextLayoutResult,
    unit: Float,
) {
    val width = label.getLineRight(0) + 16f * unit
    val left = bounds.left.toFloat().coerceAtMost(size.width - width)
    val top = if (bounds.top >= 28f * unit) bounds.top - 26f * unit else bounds.top + bounds.height + 4f * unit
    val position = Offset(left, top)
    val labelSize = Size(width, 22f * unit)
    drawRoundRect(Panel, position, labelSize, CornerRadius(6f * unit))
    drawRoundRect(LabelBorder, position, labelSize, CornerRadius(6f * unit), style = Stroke(unit))
    drawText(label, color = LabelText, topLeft = Offset(left + 8f * unit, top + 15f * unit - label.firstBaseline))
    drawMoveHandle(bounds.left + bounds.width - 10f * unit, bounds.top + 10f * unit, unit)
}

private fun DrawScope.drawMoveHandle(
    x: Float,
    y: Float,
    unit: Float,
) {
    drawCircle(Mint, 12f * unit, Offset(x, y))
    val length = 7f * unit
    val stroke = 1.5f * unit
    drawLine(Panel, Offset(x - length, y), Offset(x + length, y), stroke)
    drawLine(Panel, Offset(x, y - length), Offset(x, y + length), stroke)
    for (sign in -1..1 step 2) {
        val tip = length * sign
        val tail = 4f * unit * sign
        drawLine(Panel, Offset(x + tip, y), Offset(x + tail, y - 3f * unit), stroke)
        drawLine(Panel, Offset(x + tip, y), Offset(x + tail, y + 3f * unit), stroke)
        drawLine(Panel, Offset(x, y + tip), Offset(x - 3f * unit, y + tail), stroke)
        drawLine(Panel, Offset(x, y + tip), Offset(x + 3f * unit, y + tail), stroke)
    }
}

private val IntRect.topLeftOffset: Offset
    get() = Offset(left.toFloat(), top.toFloat())

private val IntRect.sizeF: Size
    get() = Size(width.toFloat(), height.toFloat())
