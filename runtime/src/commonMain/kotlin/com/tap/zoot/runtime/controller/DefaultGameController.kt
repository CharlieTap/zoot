package com.tap.zoot.runtime.controller

import com.tap.n64.input.N64Input
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.runtime.benchmark.BenchmarkSession
import com.tap.zoot.runtime.engine.GameEngine
import com.tap.zoot.runtime.engine.GuestAbi
import com.tap.zoot.runtime.platform.GameThread
import com.tap.zoot.runtime.platform.Logger
import com.tap.zoot.runtime.platform.MonotonicClock
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.nanoseconds

/** Owns one serial guest loop and exposes lifecycle commands to shared UI. */
@Inject
class DefaultGameController(
    private val engineFactory: GameEngine.Factory,
    private val gameThread: GameThread,
    private val input: N64Input,
    private val telemetry: FrameTelemetry,
    private val logger: Logger,
    private val clock: MonotonicClock,
) : GameController {
    private val scope = CoroutineScope(SupervisorJob() + gameThread.dispatcher)
    private val mutableState = MutableStateFlow<GameState>(GameState.Detached)
    private val active = MutableStateFlow(false)
    private val configuration = MutableStateFlow<GameConfiguration?>(null)
    private var session: Job? = null
    private var benchmark: BenchmarkSession? = null

    override val state: StateFlow<GameState> = mutableState.asStateFlow()

    override fun attach(rendererFactory: RendererFactory) {
        detach()
        session = scope.launch { runSession(rendererFactory) }
    }

    override fun detach() {
        session?.cancel()
        session = null
        input.releaseAll()
        mutableState.value = GameState.Detached
    }

    override fun setActive(active: Boolean) {
        this.active.value = active
        if (!active) input.releaseAll()
    }

    override fun configure(configuration: GameConfiguration) {
        this.configuration.value = configuration
    }

    override fun useBenchmark(session: BenchmarkSession?) {
        benchmark = session
    }

    private suspend fun runSession(rendererFactory: RendererFactory) {
        mutableState.value = GameState.Starting
        telemetry.reset()
        try {
            val initial = configuration.filterNotNull().first()
            rendererFactory.create(initial.upscaler).use { renderer ->
                engineFactory.create(renderer).use { engine ->
                    SessionLoop(engine, renderer, benchmark).run()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.error("Game failed", failure)
            mutableState.value = GameState.Failed(failure)
        }
    }

    override fun close() {
        session?.cancel()
        scope.cancel()
        gameThread.close()
        input.releaseAll()
    }

    private inner class SessionLoop(
        private val engine: GameEngine,
        private val renderer: Renderer,
        private val benchmark: BenchmarkSession?,
    ) {
        private var tick = 0L
        private var applied: GameConfiguration? = null
        private var deadline = clock.nowNanos()

        suspend fun run() {
            benchmark?.start(renderer)
            while (currentCoroutineContext().isActive) {
                applyConfiguration(checkNotNull(configuration.value))
                if (!active.value) {
                    pauseUntilActive()
                    continue
                }
                mutableState.value = GameState.Running
                if (runFrame()) {
                    mutableState.value = GameState.Paused
                    return
                }
                awaitNextTick()
            }
        }

        private fun applyConfiguration(current: GameConfiguration) {
            val previous = applied
            if (current === previous) return
            if (current.upscaler !== previous?.upscaler) renderer.setUpscaler(current.upscaler)
            val volumes = current.volumes
            val before = previous?.volumes
            if (volumes.master != before?.master) engine.setOutputVolume(volumes.master)
            if (before == null || volumes.music != before.music || volumes.effects != before.effects ||
                volumes.fanfares != before.fanfares
            ) {
                engine.setMixVolumes(volumes.music, volumes.effects, volumes.fanfares)
            }
            applied = current
        }

        private suspend fun pauseUntilActive() {
            engine.pauseAudio()
            mutableState.value = GameState.Paused
            active.first { it }
            engine.resumeAudio()
            telemetry.reset()
            deadline = clock.nowNanos()
        }

        private fun runFrame(): Boolean {
            val benchmark = benchmark
            val frameStarted = if (benchmark == null) 0L else clock.nowNanos()
            val cpuStarted = benchmark?.startFrame() ?: 0L
            tick++
            val liveInput = input.poll()
            val frameInput = if (benchmark == null) liveInput else benchmark.inputFor(tick, liveInput)
            if (benchmark != null && benchmark.entranceTick == tick) engine.traceEntrance(benchmark.entrance)
            val stepStarted = clock.nowNanos()
            engine.step(tick, frameInput)
            val audioStarted = clock.nowNanos()
            engine.mixAudio()
            val finished = clock.nowNanos()
            val stepNanos = audioStarted - stepStarted
            val audioNanos = finished - audioStarted
            telemetry.record(finished, stepNanos, audioNanos, engine.timings.graphicsNanos)
            return benchmark?.record(tick, stepNanos, audioNanos, finished - frameStarted, cpuStarted, finished, engine.timings) == true
        }

        private suspend fun awaitNextTick() {
            deadline += GuestAbi.TICK_NANOS
            val remaining = deadline - clock.nowNanos()
            if (remaining > 0) {
                delay(remaining.nanoseconds)
            } else {
                deadline = clock.nowNanos()
            }
        }
    }
}
