package com.tap.zoot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.settings.GameSettings
import com.tap.zoot.settings.SettingsEditor
import com.tap.zoot.ui.components.SettingsDivider
import com.tap.zoot.ui.components.SettingsIconButton
import com.tap.zoot.ui.theme.PanelShape
import com.tap.zoot.ui.theme.ZootColors
import com.tap.zoot.ui.theme.ZootTextStyles

private val MenuColors = darkColorScheme(primary = ZootColors.Mint, surface = ZootColors.Panel, onSurface = ZootColors.Text)

@Composable
internal fun SettingsPanel(
    settings: GameSettings,
    upscalers: List<Upscaler>,
    editor: SettingsEditor,
    onEditLayout: () -> Unit,
    onClose: () -> Unit,
) {
    MaterialTheme(colorScheme = MenuColors) {
        Box(Modifier.fillMaxSize().padding(top = 52.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 540.dp, height = 420.dp)
                    .clip(PanelShape)
                    .background(ZootColors.Panel)
                    .border(0.5.dp, ZootColors.Border, PanelShape)
                    .pointerInput(Unit) { detectTapGestures {} }
                    .semantics { paneTitle = "Game settings" },
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        "Settings",
                        Modifier.weight(1f).semantics { heading() },
                        style = ZootTextStyles.Label.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    )
                    SettingsIconButton("Close settings", onClose)
                }
                SettingsDivider()
                Row(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp).drawBehind {
                        drawLine(
                            ZootColors.Border.copy(alpha = 0.6f),
                            Offset(size.width / 2, 0f),
                            Offset(size.width / 2, size.height),
                            0.5.dp.toPx(),
                        )
                    },
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        DisplaySettingsSection(
                            upscaler = settings.display.upscaler,
                            upscalers = upscalers,
                            performanceOverlay = settings.diagnostics.performanceOverlay,
                            onUpscalerChange = { id -> editor.commit { it.copy(display = it.display.copy(upscaler = id)) } },
                            onPerformanceOverlayChange = { enabled ->
                                editor.commit { it.copy(diagnostics = it.diagnostics.copy(performanceOverlay = enabled)) }
                            },
                        )
                        SettingsDivider(Modifier.padding(vertical = 2.dp))
                        ControlsSettingsSection(
                            controls = settings.controls,
                            onCommit = { controls -> editor.commit { it.copy(controls = controls) } },
                            onPreview = { controls -> editor.preview { it.copy(controls = controls) } },
                            onPreviewFinished = editor::persist,
                            onEditLayout = onEditLayout,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        AudioSettingsSection(
                            volumes = settings.volumes,
                            onPreview = { volumes -> editor.preview { it.copy(volumes = volumes) } },
                            onPreviewFinished = editor::persist,
                        )
                    }
                }
            }
        }
    }
}
