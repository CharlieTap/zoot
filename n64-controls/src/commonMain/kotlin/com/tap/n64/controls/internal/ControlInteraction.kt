package com.tap.n64.controls.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.tap.n64.controls.ControlId
import com.tap.n64.input.N64InputSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ControlInteraction(
    private val id: ControlId,
    private val input: N64InputSink,
) {
    var heldButtons by mutableIntStateOf(0)
        private set
    var stickX by mutableFloatStateOf(0f)
        private set
    var stickY by mutableFloatStateOf(0f)
        private set
    private val stick = TouchStick()
    private val buttonMask = id.button?.mask ?: 0
    private var stickHeld = false
    private var pulse: Job? = null

    fun begin(
        point: Offset,
        size: IntSize,
    ) {
        pulse?.cancel()
        pulse = null
        move(point, size)
    }

    fun move(
        point: Offset,
        size: IntSize,
    ) {
        when (id) {
            ControlId.Stick -> {
                val travel = size.width * STICK_TRAVEL
                stick.move((point.x - size.width / 2f) / travel, (point.y - size.height / 2f) / travel)
                stickX = stick.x
                stickY = stick.y
                stickHeld = true
                input.setTouchStick(stick.guestX, stick.guestY)
            }

            ControlId.Dpad -> {
                hold(dpadButtons(point.x / size.width * 2 - 1, point.y / size.height * 2 - 1))
            }

            else -> {
                hold(buttonMask)
            }
        }
    }

    fun click(scope: CoroutineScope) {
        pulse?.cancel()
        hold(buttonMask)
        pulse =
            scope.launch {
                delay(PULSE_MILLIS)
                hold(0)
                pulse = null
            }
    }

    fun release() {
        pulse?.cancel()
        pulse = null
        hold(0)
        if (stickHeld) {
            stickHeld = false
            input.releaseTouchStick()
        }
        stickX = 0f
        stickY = 0f
    }

    private fun hold(buttons: Int) {
        if (buttons == heldButtons) return
        input.releaseButtons(heldButtons and buttons.inv())
        input.pressButtons(buttons and heldButtons.inv())
        heldButtons = buttons
    }

    companion object {
        const val STICK_TRAVEL = 0.27f
        private const val PULSE_MILLIS = 100L
    }
}
