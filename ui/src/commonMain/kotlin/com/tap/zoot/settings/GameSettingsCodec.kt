package com.tap.zoot.settings

import com.tap.n64.controls.ControlLayout
import com.tap.zoot.graphics.upscaler.UpscalerId
import com.tap.zoot.runtime.controller.GameVolumes

internal object GameSettingsCodec {
    fun decode(read: (String) -> String?): GameSettings {
        val defaults = GameSettings()
        return GameSettings(
            display =
                DisplaySettings(
                    upscaler = UpscalerId(read(Keys.UPSCALER).orDefault(defaults.display.upscaler.value)),
                ),
            volumes =
                GameVolumes(
                    master = read(Keys.MASTER_VOLUME).toFloatInOr(defaults.volumes.master, VOLUME_RANGE),
                    music = read(Keys.MUSIC_VOLUME).toFloatInOr(defaults.volumes.music, VOLUME_RANGE),
                    effects = read(Keys.EFFECTS_VOLUME).toFloatInOr(defaults.volumes.effects, VOLUME_RANGE),
                    fanfares = read(Keys.FANFARE_VOLUME).toFloatInOr(defaults.volumes.fanfares, VOLUME_RANGE),
                ),
            controls =
                ControlsSettings(
                    touchControls = read(Keys.TOUCH_CONTROLS).toBooleanOr(defaults.controls.touchControls),
                    buttonVibration = read(Keys.BUTTON_VIBRATION).toBooleanOr(defaults.controls.buttonVibration),
                    controlOpacity = read(Keys.CONTROL_OPACITY).toFloatInOr(defaults.controls.controlOpacity, CONTROL_OPACITY_RANGE),
                    controlLayout = ControlLayout.decode(read(Keys.CONTROL_LAYOUT).orEmpty()),
                ),
            diagnostics =
                DiagnosticsSettings(
                    performanceOverlay = read(Keys.PERFORMANCE_OVERLAY).toBooleanOr(defaults.diagnostics.performanceOverlay),
                ),
        )
    }

    fun encode(settings: GameSettings): Map<String, String> =
        mapOf(
            Keys.UPSCALER to settings.display.upscaler.value,
            Keys.PERFORMANCE_OVERLAY to settings.diagnostics.performanceOverlay.toString(),
            Keys.TOUCH_CONTROLS to settings.controls.touchControls.toString(),
            Keys.BUTTON_VIBRATION to settings.controls.buttonVibration.toString(),
            Keys.CONTROL_OPACITY to settings.controls.controlOpacity.toString(),
            Keys.CONTROL_LAYOUT to settings.controls.controlLayout.encode(),
            Keys.MASTER_VOLUME to settings.volumes.master.toString(),
            Keys.MUSIC_VOLUME to settings.volumes.music.toString(),
            Keys.EFFECTS_VOLUME to settings.volumes.effects.toString(),
            Keys.FANFARE_VOLUME to settings.volumes.fanfares.toString(),
        )

    private fun String?.orDefault(default: String): String = this?.takeUnless(String::isBlank) ?: default

    private fun String?.toBooleanOr(default: Boolean): Boolean =
        when (this?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> default
        }

    private fun String?.toFloatInOr(
        default: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float = this?.toFloatOrNull()?.takeIf { it.isFinite() && it in range } ?: default

    private object Keys {
        const val UPSCALER = "upscaler"
        const val PERFORMANCE_OVERLAY = "performance_overlay"
        const val TOUCH_CONTROLS = "touch_controls"
        const val BUTTON_VIBRATION = "button_vibration"
        const val CONTROL_OPACITY = "control_opacity"
        const val CONTROL_LAYOUT = "control_layout"
        const val MASTER_VOLUME = "master_volume"
        const val MUSIC_VOLUME = "music_volume"
        const val EFFECTS_VOLUME = "effects_volume"
        const val FANFARE_VOLUME = "fanfare_volume"
    }

    private val CONTROL_OPACITY_RANGE = 0.2f..1f
    private val VOLUME_RANGE = 0f..1f
}
