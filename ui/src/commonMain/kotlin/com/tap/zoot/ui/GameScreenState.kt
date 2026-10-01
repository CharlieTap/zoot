package com.tap.zoot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import com.tap.n64.controls.ControlLayout
import com.tap.n64.controls.editor.N64ControlsEditorState
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.settings.GameSettingsStore

@Stable
class GameScreenState internal constructor(
    internal val settings: GameSettingsStore,
) {
    var menuOpen by mutableStateOf(false)
        private set
    var editor by mutableStateOf<N64ControlsEditorState?>(null)
        private set
    var rendererFactory by mutableStateOf<RendererFactory?>(null)
        internal set
    var gameBounds by mutableStateOf(IntRect.Zero)
        internal set

    fun isGameActive(foreground: Boolean): Boolean = foreground && !menuOpen && editor == null

    fun toggleMenu() = if (menuOpen) closeMenu() else openMenu()

    fun openMenu() {
        menuOpen = true
    }

    fun closeMenu() {
        menuOpen = false
        settings.persist()
    }

    fun editLayout() {
        settings.persist()
        editor = N64ControlsEditorState(settings.state.value.controls.controlLayout)
        menuOpen = false
    }

    fun finishEditing(layout: ControlLayout?) {
        if (layout != null) settings.commit { it.copy(controls = it.controls.copy(controlLayout = layout)) }
        editor = null
    }
}

@Composable
fun rememberGameScreenState(settings: GameSettingsStore): GameScreenState = remember(settings) { GameScreenState(settings) }
