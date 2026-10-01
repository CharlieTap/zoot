package com.tap.zoot.ui.settings

import androidx.compose.runtime.Composable
import com.tap.zoot.settings.ControlsSettings
import com.tap.zoot.ui.components.SettingsLevel
import com.tap.zoot.ui.components.SettingsSectionTitle
import com.tap.zoot.ui.components.SettingsToggle

private val OpacityRange = 0.2f..1f

@Composable
internal fun ControlsSettingsSection(
    controls: ControlsSettings,
    onCommit: (ControlsSettings) -> Unit,
    onPreview: (ControlsSettings) -> Unit,
    onPreviewFinished: () -> Unit,
    onEditLayout: () -> Unit,
) {
    SettingsSectionTitle("CONTROLS")
    SettingsToggle("Touch controls", controls.touchControls) { onCommit(controls.copy(touchControls = it)) }
    EditLayoutEntry(onEditLayout)
    SettingsToggle("Button vibration", controls.buttonVibration) { onCommit(controls.copy(buttonVibration = it)) }
    SettingsLevel(
        label = "Control opacity",
        value = controls.controlOpacity,
        onFinish = onPreviewFinished,
        range = OpacityRange,
    ) { onPreview(controls.copy(controlOpacity = it)) }
}
