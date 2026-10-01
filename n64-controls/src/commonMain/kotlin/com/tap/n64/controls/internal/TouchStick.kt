package com.tap.n64.controls.internal

import com.tap.n64.input.N64Button
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class TouchStick {
    var x = 0f
        private set
    var y = 0f
        private set
    var guestX = 0
        private set
    var guestY = 0
        private set

    fun move(
        horizontal: Float,
        vertical: Float,
    ) {
        val distance = hypot(horizontal, vertical)
        val divisor = distance.coerceAtLeast(1f)
        x = horizontal / divisor
        y = vertical / divisor
        // A small radial dead zone still leaves a continuous range for walking and aiming.
        val magnitude = ((distance.coerceAtMost(1f) - 0.12f) / 0.88f).coerceAtLeast(0f)
        val scale = if (distance > 0f) magnitude * 80f / distance else 0f
        guestX = (horizontal * scale).roundToInt()
        guestY = (-vertical * scale).roundToInt()
    }
}

internal fun dpadButtons(
    x: Float,
    y: Float,
): Int {
    if (abs(x) < 0.25f && abs(y) < 0.25f) return 0
    var buttons = 0
    if (x < -0.35f) buttons = buttons or N64Button.DpadLeft.mask
    if (x > 0.35f) buttons = buttons or N64Button.DpadRight.mask
    if (y < -0.35f) buttons = buttons or N64Button.DpadUp.mask
    if (y > 0.35f) buttons = buttons or N64Button.DpadDown.mask
    return buttons
}
