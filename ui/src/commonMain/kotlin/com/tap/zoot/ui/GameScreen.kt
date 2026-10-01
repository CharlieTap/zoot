package com.tap.zoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.roundToIntRect
import com.tap.zoot.runtime.controller.GameConfiguration
import com.tap.zoot.runtime.controller.GameController
import com.tap.zoot.ui.controls.GameControls
import com.tap.zoot.ui.overlay.GameOverlay
import com.tap.zoot.ui.theme.ZootColors

/** Complete shared game screen. Platform shells provide only lifecycle and a native graphics surface. */
@Composable
fun GameScreen(
    controller: GameController,
    dependencies: GameScreenDependencies,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val settings by dependencies.settings.state.collectAsState()
    val gameState by controller.state.collectAsState()
    val screen = rememberGameScreenState(dependencies.settings)
    val gameActive = screen.isGameActive(active)

    LaunchedEffect(settings.display.upscaler, settings.volumes, dependencies.upscalers) {
        controller.configure(GameConfiguration(dependencies.upscalers[settings.display.upscaler], settings.volumes))
    }
    LaunchedEffect(screen.rendererFactory) {
        screen.rendererFactory?.let(controller::attach) ?: controller.detach()
    }
    LaunchedEffect(gameActive) {
        controller.setActive(gameActive)
    }
    DisposableEffect(controller, dependencies.settings) {
        onDispose {
            controller.setActive(false)
            controller.detach()
            dependencies.settings.persist()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(ZootColors.Background)) {
        val gameHeight = minOf(maxHeight, maxWidth * 3 / 4)
        dependencies.surface.Content(
            onRendererFactoryChanged = { screen.rendererFactory = it },
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(gameHeight * 4 / 3, gameHeight)
                    .onGloballyPositioned { screen.gameBounds = it.boundsInRoot().roundToIntRect() },
        )
        GameControls(
            input = dependencies.input,
            controls = settings.controls,
            enabled = active && !screen.menuOpen,
            editor = screen.editor,
            onFinishEditing = screen::finishEditing,
        )
        if (screen.editor == null) {
            GameOverlay(
                screen = screen,
                settings = settings,
                gameState = gameState,
                upscalers = dependencies.upscalers.options,
                telemetry = dependencies.telemetry,
            )
        }
    }
}
