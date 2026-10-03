package com.tap.zoot.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.performance.PerformanceTelemetry
import com.tap.zoot.settings.GameSettings
import com.tap.zoot.ui.GameBackHandler
import com.tap.zoot.ui.GameScreenState
import com.tap.zoot.ui.components.SettingsIconButton
import com.tap.zoot.ui.performance.PerformanceOverlay
import com.tap.zoot.ui.settings.SettingsPanel
import com.tap.zoot.ui.theme.ZootColors

@Composable
internal fun GameOverlay(
    screen: GameScreenState,
    settings: GameSettings,
    upscalers: List<Upscaler>,
    telemetry: PerformanceTelemetry,
    modifier: Modifier = Modifier,
) {
    GameBackHandler(screen.menuOpen, screen::closeMenu)
    Box(modifier.fillMaxSize()) {
        if (screen.menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ZootColors.Scrim)
                    .pointerInput(screen) { detectTapGestures { screen.closeMenu() } }
                    .clearAndSetSemantics {},
            )
            SettingsPanel(
                settings = settings,
                upscalers = upscalers,
                editor = screen.settings,
                onEditLayout = screen::editLayout,
                onClose = screen::closeMenu,
            )
        }
        val gameBounds = screen.gameBounds
        with(LocalDensity.current) {
            Box(Modifier.offset { gameBounds.topLeft }.size(gameBounds.width.toDp(), gameBounds.height.toDp())) {
                if (settings.diagnostics.performanceOverlay) {
                    PerformanceOverlay(
                        telemetry,
                        Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        paused = screen.menuOpen,
                    )
                }
                SettingsIconButton(
                    label = "Settings",
                    onClick = screen::toggleMenu,
                    modifier = Modifier.align(Alignment.TopEnd),
                    gear = true,
                )
            }
        }
    }
}
