package com.tap.zoot.input

import android.view.KeyEvent
import com.tap.n64.input.N64Button
import com.tap.n64.input.N64InputSink
import dev.zacsweers.metro.Inject

private val ButtonKeys =
    mapOf(
        KeyEvent.KEYCODE_BUTTON_A to N64Button.A,
        KeyEvent.KEYCODE_Z to N64Button.A,
        KeyEvent.KEYCODE_BUTTON_B to N64Button.B,
        KeyEvent.KEYCODE_X to N64Button.B,
        KeyEvent.KEYCODE_BUTTON_START to N64Button.Start,
        KeyEvent.KEYCODE_ENTER to N64Button.Start,
        KeyEvent.KEYCODE_BUTTON_L2 to N64Button.Z,
        KeyEvent.KEYCODE_Q to N64Button.Z,
        KeyEvent.KEYCODE_BUTTON_L1 to N64Button.L,
        KeyEvent.KEYCODE_H to N64Button.L,
        KeyEvent.KEYCODE_BUTTON_R1 to N64Button.R,
        KeyEvent.KEYCODE_E to N64Button.R,
        KeyEvent.KEYCODE_I to N64Button.CUp,
        KeyEvent.KEYCODE_K to N64Button.CDown,
        KeyEvent.KEYCODE_J to N64Button.CLeft,
        KeyEvent.KEYCODE_L to N64Button.CRight,
    )

/** Maps Android keyboard and gamepad events onto one N64 controller input. */
@Inject
class AndroidHardwareInput(
    private val input: N64InputSink,
) {
    private val heldKeys = mutableSetOf<Int>()

    fun handles(keyCode: Int): Boolean = isArrow(keyCode) || keyCode in ButtonKeys

    fun dispatch(
        keyCode: Int,
        pressed: Boolean,
    ) {
        val changed = if (pressed) heldKeys.add(keyCode) else heldKeys.remove(keyCode)
        if (!changed) return
        if (isArrow(keyCode)) {
            input.setHardwareStick(
                stickAxis(KeyEvent.KEYCODE_DPAD_RIGHT) - stickAxis(KeyEvent.KEYCODE_DPAD_LEFT),
                stickAxis(KeyEvent.KEYCODE_DPAD_UP) - stickAxis(KeyEvent.KEYCODE_DPAD_DOWN),
            )
        } else {
            val mask = ButtonKeys.getValue(keyCode).mask
            if (pressed) input.pressButtons(mask) else input.releaseButtons(mask)
        }
    }

    fun reset() {
        heldKeys.clear()
        input.releaseAll()
    }

    private fun stickAxis(keyCode: Int): Int = if (keyCode in heldKeys) STICK_LIMIT else 0

    private fun isArrow(keyCode: Int): Boolean = keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT

    private companion object {
        const val STICK_LIMIT = 80
    }
}
