package com.tap.n64.controls.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import com.tap.n64.controls.ControlId
import com.tap.n64.controls.internal.ControlFace
import com.tap.n64.controls.internal.rememberControlArtwork

private val Scrim = Color(0x70000000)

/** Moves a draft layout without sending controller input to the game. */
@Composable
fun N64ControlsEditor(
    state: N64ControlsEditorState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val artwork = rememberControlArtwork()
    // Match Android's original 8 dp drag threshold on both targets.
    val slop = LocalDensity.current.density * 8f
    DisposableEffect(state, enabled) {
        if (!enabled) state.cancelDrag()
        onDispose { state.cancelDrag() }
    }
    Layout(
        modifier =
            modifier
                .background(Scrim)
                .testTag("n64-editor")
                .editorDragInput(state, enabled, slop),
        content = {
            for (id in ControlId.entries) {
                key(id) {
                    ControlFace(
                        id,
                        artwork,
                        Modifier
                            .testTag("n64-edit-${id.storageKey}")
                            .editorSemantics(id, state, enabled)
                            .onPreviewKeyEvent { isControlActivationKey(it.key) }
                            .focusable(enabled),
                    )
                }
            }
            EditorDecoration(state, artwork)
        },
    ) { children, constraints ->
        val geometry = state.measure(constraints.maxWidth, constraints.maxHeight, density)
        val controls =
            children.take(ControlId.entries.size).mapIndexed { index, child ->
                val bounds = geometry[ControlId.entries[index]]
                child.measure(Constraints.fixed(bounds.width, bounds.height))
            }
        val decoration = children.last().measure(Constraints.fixed(geometry.width, geometry.height))
        layout(geometry.width, geometry.height) {
            controls.forEachIndexed { index, placeable ->
                val bounds = state.bounds(ControlId.entries[index])
                placeable.place(bounds.left, bounds.top)
            }
            decoration.place(0, 0)
        }
    }
}

internal fun isControlActivationKey(key: Key): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.DirectionCenter || key == Key.Spacebar || key == Key.ButtonA

private fun Modifier.editorSemantics(
    id: ControlId,
    state: N64ControlsEditorState,
    enabled: Boolean,
): Modifier =
    semantics {
        contentDescription = "${id.label}, drag to move"
        if (enabled) {
            onClick {
                state.select(id)
                true
            }
            customActions =
                listOf(
                    nudgeAction("Move left", state, id, -1f, 0f),
                    nudgeAction("Move right", state, id, 1f, 0f),
                    nudgeAction("Move up", state, id, 0f, -1f),
                    nudgeAction("Move down", state, id, 0f, 1f),
                )
        }
    }

private fun nudgeAction(
    label: String,
    state: N64ControlsEditorState,
    id: ControlId,
    dx: Float,
    dy: Float,
) = CustomAccessibilityAction(label) {
    state.nudge(id, dx, dy)
    true
}

private fun Modifier.editorDragInput(
    state: N64ControlsEditorState,
    enabled: Boolean,
    slop: Float,
): Modifier =
    pointerInput(state, enabled, slop) {
        if (!enabled) return@pointerInput
        var owner: PointerId? = null
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (owner == null) {
                        val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
                        owner = down.id
                        state.begin(down.position)
                        down.consume()
                    } else {
                        val change = event.changes.firstOrNull { it.id == owner }
                        if (change == null || change.isConsumed) {
                            state.cancelDrag()
                            owner = null
                        } else if (!change.pressed) {
                            if (event.changes.any { it.pressed }) state.cancelDrag() else state.finish(change.position, slop)
                            owner = null
                        } else {
                            state.drag(change.position, slop)
                        }
                        change?.consume()
                    }
                }
            }
        } finally {
            state.cancelDrag()
        }
    }
