package com.tap.n64.controls

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import com.tap.n64.controls.internal.ControlFace
import com.tap.n64.controls.internal.ControlGeometry
import com.tap.n64.controls.internal.ControlInteraction
import com.tap.n64.controls.internal.rememberControlArtwork
import com.tap.n64.input.N64InputSink
import kotlinx.coroutines.CoroutineScope

/** N64 touch controls. The game reads [input] independently of Compose drawing. */
@Composable
fun N64Controls(
    input: N64InputSink,
    modifier: Modifier = Modifier,
    layout: ControlLayout = ControlLayout.Default,
    enabled: Boolean = true,
    opacity: Float = 1f,
    vibration: Boolean = true,
    description: (ControlId) -> String = { it.label },
) {
    val artwork = rememberControlArtwork()
    val configuration = LocalViewConfiguration.current
    val exactBounds =
        remember(configuration) {
            object : ViewConfiguration by configuration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
    CompositionLocalProvider(LocalViewConfiguration provides exactBounds) {
        Layout(
            modifier = modifier.graphicsLayer { alpha = opacity }.testTag("n64-controls"),
            content = {
                for (id in ControlId.entries) {
                    key(id) {
                        val interaction = remember(input, id) { ControlInteraction(id, input) }
                        val scope = rememberCoroutineScope()
                        val haptics = LocalHapticFeedback.current
                        val vibrate = rememberUpdatedState(vibration)
                        val isButton = id.button != null
                        DisposableEffect(interaction, enabled) {
                            if (!enabled) interaction.release()
                            onDispose { interaction.release() }
                        }
                        ControlFace(
                            id,
                            artwork,
                            Modifier
                                .testTag("n64-${id.storageKey}")
                                .controlSemantics(id, interaction, scope, enabled, isButton, description)
                                .focusable(enabled && isButton)
                                .controlPointerInput(interaction, enabled, isButton, haptics, vibrate),
                            interaction,
                        )
                    }
                }
            },
        ) { children, constraints ->
            val geometry = ControlGeometry(constraints.maxWidth, constraints.maxHeight, density, layout)
            val placeables =
                children.mapIndexed { index, measurable ->
                    val bounds = geometry[ControlId.entries[index]]
                    measurable.measure(Constraints.fixed(bounds.width, bounds.height))
                }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val bounds = geometry[ControlId.entries[index]]
                    placeable.place(bounds.left, bounds.top)
                }
            }
        }
    }
}

private fun Modifier.controlSemantics(
    id: ControlId,
    interaction: ControlInteraction,
    scope: CoroutineScope,
    enabled: Boolean,
    isButton: Boolean,
    description: (ControlId) -> String,
): Modifier =
    semantics {
        contentDescription = description(id)
        if (!enabled) {
            disabled()
            hideFromAccessibility()
        }
        if (isButton) {
            role = Role.Button
            if (enabled) {
                onClick {
                    interaction.click(scope)
                    true
                }
            }
        }
    }

private fun Modifier.controlPointerInput(
    interaction: ControlInteraction,
    enabled: Boolean,
    isButton: Boolean,
    haptics: HapticFeedback,
    vibrate: State<Boolean>,
): Modifier =
    pointerInput(interaction, enabled) {
        if (!enabled) return@pointerInput
        var owner: PointerId? = null
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (owner == null) {
                        val down =
                            event.changes.firstOrNull {
                                it.changedToDown() && it.position.x >= 0 && it.position.y >= 0 &&
                                    it.position.x < size.width && it.position.y < size.height
                            } ?: continue
                        owner = down.id
                        down.consume()
                        interaction.begin(down.position, size)
                        if (isButton && vibrate.value) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    } else {
                        val change = event.changes.firstOrNull { it.id == owner }
                        if (change == null || !change.pressed || change.isConsumed) {
                            interaction.release()
                            owner = null
                        } else {
                            interaction.move(change.position, size)
                        }
                        change?.consume()
                    }
                }
            }
        } finally {
            interaction.release()
        }
    }
