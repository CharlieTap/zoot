package com.tap.zoot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.graphics.upscaler.UpscalerId
import com.tap.zoot.ui.components.SettingsSectionTitle
import com.tap.zoot.ui.components.SettingsToggle
import com.tap.zoot.ui.theme.PanelShape
import com.tap.zoot.ui.theme.ZootColors
import com.tap.zoot.ui.theme.ZootTextStyles

@Composable
internal fun DisplaySettingsSection(
    upscaler: UpscalerId,
    upscalers: List<Upscaler>,
    performanceOverlay: Boolean,
    onUpscalerChange: (UpscalerId) -> Unit,
    onPerformanceOverlayChange: (Boolean) -> Unit,
) {
    SettingsSectionTitle("DISPLAY")
    UpscalerSelector(upscaler, upscalers, onUpscalerChange)
    SettingsToggle("Performance overlay", performanceOverlay, onPerformanceOverlayChange)
}

@Composable
private fun UpscalerSelector(
    selectedId: UpscalerId,
    upscalers: List<Upscaler>,
    onChange: (UpscalerId) -> Unit,
) {
    BasicText("Upscaler", style = ZootTextStyles.Label)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (upscaler in upscalers) {
            val selected = upscaler.id == selectedId
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(PanelShape)
                    .background(if (selected) ZootColors.Mint else Color.White.copy(alpha = 0.04f))
                    .border(0.5.dp, if (selected) ZootColors.Mint else ZootColors.Border, PanelShape)
                    .selectable(selected, role = Role.RadioButton) { onChange(upscaler.id) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    upscaler.name,
                    style = ZootTextStyles.Label.copy(color = if (selected) Color(0xff141719) else ZootColors.Text, fontSize = 12.sp),
                )
            }
        }
    }
}
