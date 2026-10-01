package com.tap.zoot.ui.settings

import androidx.compose.runtime.Composable
import com.tap.zoot.runtime.controller.GameVolumes
import com.tap.zoot.ui.components.SettingsLevel
import com.tap.zoot.ui.components.SettingsSectionTitle

@Composable
internal fun AudioSettingsSection(
    volumes: GameVolumes,
    onPreview: (GameVolumes) -> Unit,
    onPreviewFinished: () -> Unit,
) {
    SettingsSectionTitle("AUDIO")
    SettingsLevel("Master volume", volumes.master, onPreviewFinished) { onPreview(volumes.copy(master = it)) }
    SettingsLevel("Music", volumes.music, onPreviewFinished) { onPreview(volumes.copy(music = it)) }
    SettingsLevel("Sound effects", volumes.effects, onPreviewFinished) { onPreview(volumes.copy(effects = it)) }
    SettingsLevel("Fanfares", volumes.fanfares, onPreviewFinished) { onPreview(volumes.copy(fanfares = it)) }
}
