@file:OptIn(ExperimentalCoroutinesApi::class)

package com.tap.n64.controls

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.tap.n64.controls.internal.ControlInteraction
import com.tap.n64.input.ControllerState
import com.tap.n64.input.N64Button
import com.tap.n64.input.N64Input
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ControlInteractionTest {
    @Test
    fun accessiblePulseLastsOneHundredMilliseconds() =
        runTest {
            val input = N64Input()
            val button = ControlInteraction(ControlId.A, input)
            button.click(backgroundScope)
            runCurrent()
            assertEquals(N64Button.A.mask, input.state.buttons)
            advanceTimeBy(99)
            runCurrent()
            assertEquals(N64Button.A.mask, input.state.buttons)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(ControllerState.Neutral, input.state)
        }

    @Test
    fun touchReplacesThePulseWithoutAStaleRelease() =
        runTest {
            val input = N64Input()
            val button = ControlInteraction(ControlId.A, input)
            button.click(backgroundScope)
            runCurrent()
            button.begin(Offset.Zero, IntSize(78, 78))
            advanceTimeBy(200)
            runCurrent()
            assertEquals(N64Button.A.mask, input.state.buttons)
            button.release()
            assertEquals(ControllerState.Neutral, input.state)
        }

    @Test
    fun releaseCancelsThePulseAndPreservesAnotherOwner() =
        runTest {
            val input = N64Input()
            val button = ControlInteraction(ControlId.A, input)
            input.pressButtons(N64Button.A.mask)
            button.click(backgroundScope)
            runCurrent()
            button.release()
            advanceTimeBy(200)
            runCurrent()
            assertEquals(N64Button.A.mask, input.state.buttons)
            input.releaseButtons(N64Button.A.mask)
            assertEquals(ControllerState.Neutral, input.state)
        }
}
