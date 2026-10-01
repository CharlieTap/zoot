package com.tap.n64.input

import kotlin.test.Test
import kotlin.test.assertEquals

class N64InputTest {
    @Test
    fun producerAndConsumerUseSeparateViewsOfTheSameInput() {
        val input = N64Input()
        val sink: N64InputSink = input
        val source: N64InputSource = input

        sink.pressButtons(N64Button.B.mask)

        assertEquals(N64Button.B.mask, source.poll().buttons)
    }

    @Test
    fun shortTapSurvivesUntilOneGameTick() {
        val input = N64Input()
        input.pressButtons(N64Button.A.mask)
        input.releaseButtons(N64Button.A.mask)
        assertEquals(ControllerState.Neutral, input.state)
        assertEquals(N64Button.A.mask, input.poll().buttons)
        assertEquals(ControllerState.Neutral, input.poll())
    }

    @Test
    fun pollingDoesNotReleaseHeldButtonsOrChangeSignedAxes() {
        val input = N64Input()
        input.setTouchStick(-80, -40)
        input.pressButtons(N64Button.Z.mask)
        repeat(3) {
            val state = input.poll()
            assertEquals(N64Button.Z.mask, state.buttons)
            assertEquals(-80, state.stickX)
            assertEquals(-40, state.stickY)
        }
        input.releaseButtons(N64Button.Z.mask)
        assertEquals(0, input.poll().buttons)
    }

    @Test
    fun focusLossAlsoClearsUnconsumedTaps() {
        val input = N64Input()
        input.pressButtons(N64Button.Start.mask)
        input.releaseButtons(N64Button.Start.mask)
        input.releaseAll()
        assertEquals(ControllerState.Neutral, input.poll())
    }

    @Test
    fun buttonsMatchLibultraWithoutSharingBits() {
        assertEquals(
            listOf(0x8000, 0x4000, 0x2000, 0x1000, 0x0800, 0x0400, 0x0200, 0x0100, 0x0020, 0x0010, 8, 4, 2, 1),
            N64Button.entries.map { it.mask },
        )
        val allButtons = N64Button.entries.fold(0) { mask, button -> mask or button.mask }
        assertEquals(0xff3f, allButtons)
    }

    @Test
    fun stickAndMultipleButtonsArePublishedTogether() {
        val input = N64Input()
        input.pressButtons(N64Button.A.mask)
        input.pressButtons(N64Button.Z.mask or N64Button.R.mask)
        input.setTouchStick(-80, 80)
        val state = input.state
        assertEquals(0xa010, state.buttons)
        assertEquals(-80, state.stickX)
        assertEquals(80, state.stickY)
        input.setTouchStick(80, -80)
        assertEquals(80, input.state.stickX)
        assertEquals(-80, input.state.stickY)
    }

    @Test
    fun releasingOneSourceDoesNotReleaseAnother() {
        val input = N64Input()
        input.pressButtons(N64Button.A.mask)
        input.pressButtons(N64Button.A.mask)
        input.releaseButtons(N64Button.A.mask)
        assertEquals(N64Button.A.mask, input.state.buttons)
        input.releaseButtons(N64Button.A.mask)
        assertEquals(ControllerState.Neutral, input.state)
    }

    @Test
    fun cButtonsAndDpadAreIndependent() {
        val input = N64Input()
        input.pressButtons(N64Button.CUp.mask or N64Button.DpadUp.mask)
        input.releaseButtons(N64Button.DpadUp.mask)
        assertEquals(N64Button.CUp.mask, input.state.buttons)
    }

    @Test
    fun touchTemporarilyTakesOverFromHardwareStick() {
        val input = N64Input()
        input.setHardwareStick(80, 0)
        input.setTouchStick(0, 40)
        assertEquals(0, input.state.stickX)
        assertEquals(40, input.state.stickY)
        input.releaseTouchStick()
        assertEquals(80, input.state.stickX)
        assertEquals(0, input.state.stickY)
    }

    @Test
    fun losingFocusClearsEveryHeldInput() {
        val input = N64Input()
        input.pressButtons(0xff3f)
        input.setHardwareStick(80, 80)
        input.setTouchStick(-40, 20)
        input.releaseAll()
        assertEquals(ControllerState.Neutral, input.state)
        input.pressButtons(N64Button.B.mask)
        input.releaseButtons(N64Button.B.mask)
        assertEquals(ControllerState.Neutral, input.state)
    }

    @Test
    fun packedStateRoundTripsSignedAxes() {
        val state = ControllerState.of(N64Button.A.mask, -128, 127)
        assertEquals(N64Button.A.mask, state.buttons)
        assertEquals(-128, state.stickX)
        assertEquals(127, state.stickY)
    }
}
