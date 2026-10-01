package com.tap.zoot.settings

import com.tap.n64.controls.ControlLayout
import com.tap.zoot.graphics.upscaler.UpscalerId
import com.tap.zoot.runtime.controller.GameVolumes

data class GameSettings(
    val display: DisplaySettings = DisplaySettings(),
    val volumes: GameVolumes = GameVolumes(),
    val controls: ControlsSettings = ControlsSettings(),
    val diagnostics: DiagnosticsSettings = DiagnosticsSettings(),
)

data class DisplaySettings(
    val upscaler: UpscalerId = UpscalerId.Default,
)

data class ControlsSettings(
    val touchControls: Boolean = true,
    val buttonVibration: Boolean = true,
    val controlOpacity: Float = 1f,
    val controlLayout: ControlLayout = ControlLayout.Default,
)

data class DiagnosticsSettings(
    val performanceOverlay: Boolean = true,
)
