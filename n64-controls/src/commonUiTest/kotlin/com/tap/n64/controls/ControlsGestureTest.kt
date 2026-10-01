@file:OptIn(ExperimentalTestApi::class)

package com.tap.n64.controls

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tap.n64.input.ControllerState
import com.tap.n64.input.N64Button
import com.tap.n64.input.N64Input
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ControlsGestureTest {
    @Test
    fun controlLegendsKeepTheirSizeWhenSystemFontScaleChanges() =
        runComposeUiTest {
            val input = N64Input()
            val fontScale = mutableStateOf(1f)
            setContent { ControlsFixture(fontScale.value) { N64Controls(input, modifier = Modifier.fillMaxSize(), vibration = false) } }
            val before = onNodeWithTag("fixture").captureToImage()
            runOnIdle { fontScale.value = 2f }
            val after = onNodeWithTag("fixture").captureToImage()
            val a = IntArray(before.width * before.height)
            val b = IntArray(after.width * after.height)
            before.readPixels(a)
            after.readPixels(b)
            assertContentEquals(a, b)
        }

    @Test
    fun movingTheStickDoesNotRecomposeOrRemeasureTheSurface() =
        runComposeUiTest {
            val input = N64Input()
            var compositions = 0
            var measurements = 0
            setContent {
                ControlsFixture {
                    SideEffect { compositions++ }
                    N64Controls(
                        input,
                        vibration = false,
                        modifier =
                            Modifier.fillMaxSize().layout { measurable, constraints ->
                                measurements++
                                val child = measurable.measure(constraints)
                                layout(child.width, child.height) { child.place(0, 0) }
                            },
                    )
                }
            }
            waitForIdle()
            val initialCompositions = compositions
            val initialMeasurements = measurements
            onNodeWithTag("n64-controls").performTouchInput {
                down(Offset(97f, 288f))
                repeat(20) { moveTo(Offset(97f + it * 2, 288f)) }
                up()
            }
            runOnIdle {
                assertEquals(initialCompositions, compositions)
                assertEquals(initialMeasurements, measurements)
            }
        }

    @Test
    fun stickCanStayHeldWhileAnotherFingerRepeatedlyPressesButtons() =
        runComposeUiTest {
            val input = N64Input()
            setContent { ControlsFixture { N64Controls(input, modifier = Modifier.fillMaxSize(), vibration = false) } }
            val controls = onNodeWithTag("n64-controls")
            controls.performTouchInput { down(0, Offset(97f + 156 * 0.27f, 288f)) }
            repeat(3) {
                controls.performTouchInput { down(1, Offset(888f, 315f)) }
                runOnIdle {
                    assertEquals(80, input.state.stickX)
                    assertEquals(N64Button.A.mask, input.state.buttons)
                }
                controls.performTouchInput { up(1) }
                runOnIdle {
                    assertEquals(80, input.state.stickX)
                    assertEquals(0, input.state.buttons)
                }
            }
            controls.performTouchInput { up(0) }
            runOnIdle { assertEquals(ControllerState.Neutral, input.state) }
        }

    @Test
    fun buttonsRemainHeldOutsideTheirBoundsWithoutStealingOtherControls() =
        runComposeUiTest {
            val input = N64Input()
            setContent { ControlsFixture { N64Controls(input, modifier = Modifier.fillMaxSize(), vibration = false) } }
            val controls = onNodeWithTag("n64-controls")
            controls.performTouchInput {
                down(0, Offset(888f, 315f))
                moveTo(0, Offset(817f, 265f))
            }
            runOnIdle { assertEquals(N64Button.A.mask, input.state.buttons) }
            controls.performTouchInput { up(0) }
            runOnIdle {
                assertEquals(ControllerState.Neutral, input.state)
                assertEquals(N64Button.A.mask, input.poll().buttons)
                assertEquals(ControllerState.Neutral, input.poll())
            }
        }

    @Test
    fun disablingReleasesOwnedHoldsWithoutReleasingKeyboardHolds() =
        runComposeUiTest {
            val input = N64Input()
            val enabled = mutableStateOf(true)
            setContent {
                ControlsFixture {
                    N64Controls(
                        input,
                        modifier = Modifier.fillMaxSize(),
                        enabled = enabled.value,
                        vibration = false,
                    )
                }
            }
            runOnIdle { input.pressButtons(N64Button.A.mask) }
            onNodeWithTag("n64-controls").performTouchInput { down(Offset(888f, 315f)) }
            runOnIdle { enabled.value = false }
            waitForIdle()
            runOnIdle {
                assertEquals(N64Button.A.mask, input.state.buttons)
                input.releaseButtons(N64Button.A.mask)
                assertEquals(ControllerState.Neutral, input.state)
            }
        }

    @Test
    fun accessibleActivationProducesAPulse() =
        runComposeUiTest {
            val input = N64Input()
            setContent { ControlsFixture { N64Controls(input, modifier = Modifier.fillMaxSize(), vibration = false) } }
            onNodeWithTag("n64-A").performClick()
            runOnIdle { assertEquals(N64Button.A.mask, input.poll().buttons) }
        }

    @Test
    fun captureIdleAndHeldControls() =
        runComposeUiTest {
            val input = N64Input()
            setContent { ControlsFixture { N64Controls(input, modifier = Modifier.fillMaxSize(), vibration = false) } }
            val fixture = onNodeWithTag("fixture")
            val controls = onNodeWithTag("n64-controls")
            saveCapture("idle", fixture.captureToImage())
            for (id in ControlId.entries) {
                val x = id.defaultX(960f)
                val y = id.defaultY(400f)
                controls.performTouchInput {
                    down(Offset(x, y))
                    if (id == ControlId.Stick) moveTo(Offset(x + 156 * 0.27f, y))
                    if (id == ControlId.Dpad) moveTo(Offset(x + 35f, y - 35f))
                }
                saveCapture("held-${id.name.lowercase()}", fixture.captureToImage())
                controls.performTouchInput { up() }
            }
        }
}
