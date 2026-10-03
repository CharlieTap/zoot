package com.tap.zoot.runtime.controller

import com.tap.crashreporting.CrashEnvironment
import com.tap.crashreporting.CrashReportingConfig
import com.tap.n64.input.ControllerState
import com.tap.n64.input.N64Input
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.graphics.upscaler.TextureFilter
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.graphics.upscaler.UpscalerId
import com.tap.zoot.runtime.TestRenderer
import com.tap.zoot.runtime.benchmark.BenchmarkSession
import com.tap.zoot.runtime.diagnostics.CrashEnvironmentProvider
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            val controller = controller(engineFactory = { throw IllegalStateException("No guest") })
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                runCurrent()
                val failed = assertIs<GameState.Failed>(controller.state.value)
                assertTrue(failed.duringStartup)
                assertEquals("Crash: IllegalStateException", failed.report.title)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun stepFailureReleasesResourcesAndInput() =
        runTest {
            val engine = FakeEngine(failStepAt = 2)
            val renderer = TestRenderer()
            val input = N64Input()
            val controller = controller(engine, input = input)
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { renderer })
                controller.setActive(true)
                input.pressButtons(0x8000)
                runCurrent()
                advanceTimeBy(100)
                runCurrent()

                val failed = assertIs<GameState.Failed>(controller.state.value)
                assertFalse(failed.duringStartup)
                assertTrue(engine.closed)
                assertTrue(renderer.closed)
                assertEquals(0, input.poll().buttons)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun audioFailureIsReportedAsState() =
        runTest {
            val engine = FakeEngine(failAudio = true)
            val controller = controller(engine)
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                controller.setActive(true)
                runCurrent()

                assertIs<GameState.Failed>(controller.state.value)
                assertTrue(engine.closed)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun detachingARunningGameIsNotAFailure() =
        runTest {
            val engine = FakeEngine()
            val controller = controller(engine)
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                controller.setActive(true)
                runCurrent()

                controller.detach()
                runCurrent()

                assertEquals(GameState.Detached, controller.state.value)
                assertTrue(engine.closed)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun failureSurvivesDetachAndReattach() =
        runTest {
            var sessions = 0
            val controller =
                controller(engineFactory = {
                    sessions++
                    FakeEngine(failStepAt = 1)
                })
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                controller.setActive(true)
                runCurrent()
                val failed = assertIs<GameState.Failed>(controller.state.value)

                controller.detach()
                controller.attach(RendererFactory { TestRenderer() })
                runCurrent()

                assertEquals(failed, controller.state.value)
                assertEquals(1, sessions)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun restartStartsExactlyOneFreshSession() =
        runTest {
            val engines = mutableListOf<FakeEngine>()
            val controller = controller(engineFactory = { FakeEngine(failStepAt = if (engines.isEmpty()) 1 else 0).also(engines::add) })
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                controller.setActive(true)
                runCurrent()
                assertIs<GameState.Failed>(controller.state.value)

                controller.restart()
                controller.restart()
                runCurrent()

                assertEquals(2, engines.size)
                assertTrue(engines.first().closed)
                assertEquals(GameState.Running, controller.state.value)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    @Test
    fun restartWithoutASurfaceWaitsForAttach() =
        runTest {
            var sessions = 0
            val controller = controller(engineFactory = { FakeEngine(failStepAt = if (sessions++ == 0) 1 else 0) })
            try {
                controller.configure(GameConfiguration(TestUpscaler, GameVolumes()))
                controller.attach(RendererFactory { TestRenderer() })
                controller.setActive(true)
                runCurrent()
                controller.detach()

                controller.restart()
                runCurrent()
                assertEquals(GameState.Detached, controller.state.value)
                assertEquals(1, sessions)

                controller.attach(RendererFactory { TestRenderer() })
                runCurrent()
                assertEquals(2, sessions)
                assertEquals(GameState.Running, controller.state.value)
            } finally {
                controller.close()
                runCurrent()
            }
        }

    private fun TestScope.controller(
        engine: GameEngine = FakeEngine(),
        engineFactory: GameEngine.Factory = GameEngine.Factory { engine },
        input: N64Input = N64Input(),
    ): DefaultGameController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DefaultGameController(
            engineFactory = engineFactory,
            gameThread = TestGameThread(dispatcher),
            input = input,
            telemetry = TestTelemetry,
            logger = TestLogger,
            clock = MonotonicClock { testScheduler.currentTime * 1_000_000 },
            crashEnvironment = TestCrashEnvironment,
            crashReporting = CrashReportingConfig("game", "https://github.com/owner/game"),
        )
    }

    private class TestGameThread(
        override val dispatcher: CoroutineDispatcher,
    ) : GameThread {
        override fun close() = Unit
    }

    private class FakeEngine(
        private val failStepAt: Int = 0,
        private val failAudio: Boolean = false,
    ) : GameEngine {
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
            check(steps != failStepAt) { "Step failed" }
        }

        override fun mixAudio() {
            audioMixes++
            check(!failAudio) { "Audio failed" }
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

    private object TestCrashEnvironment : CrashEnvironmentProvider {
        override fun environment() = CrashEnvironment("1.0", "2.2.0", null, "Test", null, null)
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
