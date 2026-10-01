package com.tap.zoot.runtime.controller

import com.tap.n64.input.ControllerState
import com.tap.n64.input.N64Input
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.graphics.upscaler.TextureFilter
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.graphics.upscaler.UpscalerId
import com.tap.zoot.runtime.TestRenderer
import com.tap.zoot.runtime.benchmark.BenchmarkSession
import com.tap.zoot.runtime.engine.GameEngine
import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.platform.GameThread
import com.tap.zoot.runtime.platform.Logger
import com.tap.zoot.runtime.platform.MonotonicClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGameControllerTest {
    @Test
    fun drivesOneSessionAndPausesAudioWhenInactive() =
        runTest {
            val engine = FakeEngine()
            val renderer = TestRenderer()
            val controller = controller(engine)
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { renderer })
                controller.setActive(true)

                runCurrent()
                advanceTimeBy(151)
                runCurrent()

                assertTrue(engine.steps >= 3)
                assertEquals(engine.steps, engine.audioMixes)
                assertEquals(GameState.Running, controller.state.value)

                controller.setActive(false)
                advanceTimeBy(60)
                runCurrent()

                assertEquals(1, engine.pauses)
                assertEquals(GameState.Paused, controller.state.value)
            } finally {
                controller.close()
                runCurrent()
            }
            assertTrue(engine.closed)
            assertTrue(renderer.closed)
        }

    @Test
    fun optionalBenchmarkAppliesReplayAndStopsAfterRequestedTick() =
        runTest {
            val engine = FakeEngine()
            val benchmark = FakeBenchmarkSession()
            val controller = controller(engine)
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.useBenchmark(benchmark)
                controller.attach(RendererFactory { TestRenderer() })
                controller.setActive(true)

                runCurrent()
                advanceTimeBy(100)
                runCurrent()

                assertEquals(2, engine.steps)
                assertEquals(0x8000, engine.firstButtons)
                assertEquals(0x123, engine.entrance)
                assertEquals(2, benchmark.frames)
                assertEquals(GameState.Paused, controller.state.value)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun startupFailureIsReportedAsState() =
        runTest {
            val failure = IllegalStateException("No guest")
            val controller = controller(engineFactory = { throw failure })
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                runCurrent()
                assertEquals(GameState.Failed(failure), controller.state.value)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    private fun TestScope.controller(
        engine: GameEngine = FakeEngine(),
        engineFactory: GameEngine.Factory = GameEngine.Factory { engine },
    ): DefaultGameController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DefaultGameController(
            engineFactory = engineFactory,
            gameThread = TestGameThread(dispatcher),
            input = N64Input(),
            telemetry = TestTelemetry,
            logger = TestLogger,
            clock = MonotonicClock { testScheduler.currentTime * 1_000_000 },
        )
    }

    private class TestGameThread(
        override val dispatcher: CoroutineDispatcher,
    ) : GameThread {
        override fun close() = Unit
    }

    private class FakeEngine : GameEngine {
        override val timings = HostTimings()
        var steps = 0
        var audioMixes = 0
        var pauses = 0
        var closed = false
        var firstButtons = -1
        var entrance = -1

        override fun step(
            tick: Long,
            input: ControllerState,
        ) {
            if (steps == 0) firstButtons = input.buttons
            steps++
        }

        override fun mixAudio() {
            audioMixes++
        }

        override fun traceEntrance(entrance: Int) {
            this.entrance = entrance
        }

        override fun setOutputVolume(volume: Float) = Unit

        override fun setMixVolumes(
            music: Float,
            effects: Float,
            fanfares: Float,
        ) = Unit

        override fun pauseAudio() {
            pauses++
        }

        override fun resumeAudio() = Unit

        override fun close() {
            closed = true
        }
    }

    private class FakeBenchmarkSession : BenchmarkSession {
        override val entranceTick = 1L
        override val entrance = 0x123
        var frames = 0

        override fun start(renderer: Renderer) = Unit

        override fun inputFor(
            tick: Long,
            live: ControllerState,
        ): ControllerState = if (tick == 1L) ControllerState(0x8000) else live

        override fun startFrame(): Long = 0

        override fun record(
            tick: Long,
            stepNanos: Long,
            audioNanos: Long,
            totalNanos: Long,
            cpuStartedNanos: Long,
            finishedNanos: Long,
            timings: HostTimings,
        ): Boolean {
            frames++
            return tick == 2L
        }
    }

    private object TestUpscaler : Upscaler {
        override val id = UpscalerId("test")
        override val order = 0
        override val name = "Test"
        override val filter = TextureFilter.Nearest
        override val shaderSource = ""
    }

    private object TestTelemetry : FrameTelemetry {
        override fun reset() = Unit

        override fun record(
            finishedNanos: Long,
            stepNanos: Long,
            audioNanos: Long,
            graphicsNanos: Long,
        ) = Unit
    }

    private object TestLogger : Logger {
        override fun info(message: String) = Unit

        override fun warning(
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun error(
            message: String,
            throwable: Throwable?,
        ) = Unit
    }
}
