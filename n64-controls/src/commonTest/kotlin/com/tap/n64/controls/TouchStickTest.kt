package com.tap.n64.controls

import com.tap.n64.controls.internal.TouchStick
import com.tap.n64.controls.internal.dpadButtons
import com.tap.n64.input.N64Button
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TouchStickTest {
    @Test
    fun analogHasDeadZoneWalkingRangeAndCorrectVerticalDirection() {
        val stick = TouchStick()
        stick.move(0.05f, -0.05f)
        assertEquals(0, stick.guestX)
        assertEquals(0, stick.guestY)
        stick.move(0f, -0.5f)
        assertEquals(0, stick.guestX)
        assertTrue(stick.guestY in 1..79)
        stick.move(0f, -1f)
        assertEquals(80, stick.guestY)
        stick.move(-3f, 0f)
        assertEquals(-80, stick.guestX)
        assertEquals(-1f, stick.x, 0f)
        stick.move(0f, 0f)
        assertEquals(0, stick.guestX)
        assertEquals(0, stick.guestY)
    }

    @Test
    fun analogDiagonalsStayInsideTheGate() {
        val stick = TouchStick()
        stick.move(4f, -4f)
        assertEquals(57, stick.guestX)
        assertEquals(57, stick.guestY)
        assertEquals(1f, hypot(stick.x, stick.y), 0.0001f)
    }

    @Test
    fun dpadSupportsDiagonalsAndSlidingThroughNeutral() {
        assertEquals(0, dpadButtons(0f, 0f))
        assertEquals(N64Button.DpadUp.mask, dpadButtons(0f, -0.8f))
        assertEquals(N64Button.DpadLeft.mask, dpadButtons(-0.8f, 0f))
        assertEquals(N64Button.DpadDown.mask or N64Button.DpadRight.mask, dpadButtons(0.8f, 0.8f))
    }
}
