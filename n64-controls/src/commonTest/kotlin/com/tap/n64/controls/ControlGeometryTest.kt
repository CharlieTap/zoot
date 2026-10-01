package com.tap.n64.controls

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.tap.n64.controls.internal.ControlGeometry
import com.tap.n64.controls.internal.ControlInteraction
import com.tap.n64.input.ControllerState
import com.tap.n64.input.N64Input
import kotlin.test.Test
import kotlin.test.assertEquals

class ControlGeometryTest {
    @Test
    fun defaultBoundsMatchAndroid() {
        val geometry = ControlGeometry(960, 400, 1f, ControlLayout.Default)
        assertEquals(IntRect(849, 276, 927, 354), geometry[ControlId.A])
        assertEquals(IntRect(783, 231, 851, 299), geometry[ControlId.B])
        assertEquals(IntRect(19, 210, 175, 366), geometry[ControlId.Stick])
        assertEquals(IntRect(47, 103, 147, 203), geometry[ControlId.Dpad])
        assertEquals(IntRect(441, 347, 519, 395), geometry[ControlId.Start])
    }

    @Test
    fun sizesTruncateBeforeCentresAreConstrained() {
        val saved = ControlLayout.Default.moved(ControlId.A, ControlPosition(0f, 1f))
        val geometry = ControlGeometry(1000, 413, 3f, saved)
        assertEquals(80, geometry[ControlId.A].width)
        assertEquals(IntRect(0, 333, 80, 413), geometry[ControlId.A])
        assertEquals(ControlId.A, geometry.hit(Offset(0f, 400f)))
        assertEquals(null, geometry.hit(Offset(80f, 400f)))
    }

    @Test
    fun lastPaintedControlWinsOverlaps() {
        var layout = ControlLayout.Default
        for (id in ControlId.entries) layout = layout.moved(id, ControlPosition(0.5f, 0.5f))
        assertEquals(ControlId.Dpad, ControlGeometry(960, 400, 1f, layout).hit(Offset(480f, 200f)))
    }

    @Test
    fun stickVisualTravelAndGuestRangeAgree() {
        val input = N64Input()
        val stick = ControlInteraction(ControlId.Stick, input)
        stick.begin(Offset(78f + 156 * 0.27f, 78f), IntSize(156, 156))
        assertEquals(80, input.state.stickX)
        assertEquals(1f, stick.stickX)
        stick.release()
        assertEquals(ControllerState.Neutral, input.state)
    }
}
