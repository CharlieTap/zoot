package com.tap.zoot.ui

import com.tap.crashreporting.CrashReportingConfig
import com.tap.n64.input.N64InputSink
import com.tap.zoot.graphics.upscaler.UpscalerRegistry
import com.tap.zoot.performance.PerformanceTelemetry
import com.tap.zoot.settings.GameSettingsStore
import com.tap.zoot.ui.surface.GameSurface
import dev.zacsweers.metro.Inject

@Inject
class GameScreenDependencies(
    val surface: GameSurface,
    val settings: GameSettingsStore,
    val upscalers: UpscalerRegistry,
    val input: N64InputSink,
    val telemetry: PerformanceTelemetry,
    val crashReporting: CrashReportingConfig,
)
