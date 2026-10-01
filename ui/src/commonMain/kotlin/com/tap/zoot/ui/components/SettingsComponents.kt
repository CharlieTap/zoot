package com.tap.zoot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap.zoot.ui.theme.PanelShape
import com.tap.zoot.ui.theme.ZootColors
import com.tap.zoot.ui.theme.ZootTextStyles
import kotlin.math.roundToInt

private val LevelRange = 0f..1f

@Composable
internal fun SettingsSectionTitle(title: String) {
    BasicText(title, Modifier.padding(bottom = 2.dp).semantics { heading() }, style = ZootTextStyles.Mono)
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(ZootColors.Border.copy(alpha = 0.6f)))
}

@Composable
internal fun SettingsToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = ZootTextStyles.Label)
        ToggleIndicator(checked)
    }
}

@Composable
internal fun ToggleIndicator(checked: Boolean) {
    Canvas(Modifier.size(30.dp, 18.dp)) {
        val color = if (checked) ZootColors.Mint else ZootColors.Muted
        drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
        drawCircle(
            color,
            radius = size.height / 2 - 2.dp.toPx(),
            center = Offset(if (checked) size.width - size.height / 2 else size.height / 2, size.height / 2),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsLevel(
    label: String,
    value: Float,
    onFinish: () -> Unit,
    range: ClosedFloatingPointRange<Float> = LevelRange,
    onChange: (Float) -> Unit,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = ZootTextStyles.Label)
            BasicText("${(value * 100).roundToInt()}%", style = ZootTextStyles.Mono.copy(fontSize = 13.sp))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinish,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label },
            colors =
                SliderDefaults.colors(
                    thumbColor = ZootColors.Mint,
                    activeTrackColor = ZootColors.Mint,
                    inactiveTrackColor = ZootColors.Border,
                ),
            thumb = {
                Box(Modifier.size(16.dp).background(ZootColors.Mint, RoundedCornerShape(50)))
            },
            track = { slider ->
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    drawLine(ZootColors.Border, Offset.Zero, Offset(size.width, 0f), size.height, StrokeCap.Round)
                    val fraction = (slider.value - slider.valueRange.start) / (slider.valueRange.endInclusive - slider.valueRange.start)
                    val start = if (isRtl) size.width else 0f
                    val end = if (isRtl) size.width * (1f - fraction) else size.width * fraction
                    drawLine(ZootColors.Mint, Offset(start, 0f), Offset(end, 0f), size.height, StrokeCap.Round)
                }
            },
        )
    }
}

@Composable
internal fun SettingsIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gear: Boolean = false,
) {
    Box(
        modifier.size(44.dp).semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val iconModifier =
            if (gear) {
                Modifier
                    .size(28.dp)
                    .background(ZootColors.Panel, PanelShape)
                    .border(0.5.dp, ZootColors.Border, PanelShape)
                    .padding(5.dp)
            } else {
                Modifier.size(18.dp)
            }
        Canvas(iconModifier) {
            if (gear) {
                repeat(8) { tooth ->
                    rotate(tooth * 45f) {
                        drawRoundRect(
                            ZootColors.Muted,
                            Offset(size.width * 0.4f, 0f),
                            Size(size.width * 0.2f, size.height * 0.35f),
                            CornerRadius(0.8.dp.toPx()),
                        )
                    }
                }
                drawCircle(ZootColors.Muted, size.minDimension * 0.37f)
                drawCircle(ZootColors.Panel, size.minDimension * 0.2f)
            } else {
                drawLine(
                    ZootColors.Muted,
                    Offset(2.dp.toPx(), 2.dp.toPx()),
                    Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    1.5.dp.toPx(),
                )
                drawLine(
                    ZootColors.Muted,
                    Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
                    Offset(2.dp.toPx(), size.height - 2.dp.toPx()),
                    1.5.dp.toPx(),
                )
            }
        }
    }
}
