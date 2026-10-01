package com.tap.zoot.benchmark

import android.os.Debug
import android.util.Log
import com.tap.n64.input.ControllerState
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.RendererFrameMetrics
import com.tap.zoot.runtime.benchmark.BenchmarkSession
import com.tap.zoot.runtime.engine.HostTimings

/** Android logcat adapter for the optional release benchmark replay. */
internal class AndroidBenchmarkSession(
    private val configuration: BenchmarkConfiguration,
) : BenchmarkSession {
    private lateinit var metrics: RendererFrameMetrics

    override val entranceTick: Long?
        get() = configuration.entranceTick

    override val entrance = 529

    override fun start(renderer: Renderer) {
        metrics = renderer.frameMetrics ?: error("The Android renderer does not expose benchmark metrics")
    }

    override fun inputFor(
        tick: Long,
        live: ControllerState,
    ): ControllerState {
        val replay = configuration.replay
        return if (replay == null) live else replay.stateAt(tick)
    }

    override fun startFrame(): Long = Debug.threadCpuTimeNanos()

    override fun record(
        tick: Long,
        stepNanos: Long,
        audioNanos: Long,
        totalNanos: Long,
        cpuStartedNanos: Long,
        finishedNanos: Long,
        timings: HostTimings,
    ): Boolean {
        val cpuNanos = Debug.threadCpuTimeNanos() - cpuStartedNanos
        Log.i(
            TAG,
            buildString(256) {
                append(configuration.label)
                append(',').append(tick)
                append(',').append(stepNanos)
                append(',').append(timings.fast3dNanos)
                append(',').append(audioNanos)
                append(',').append(timings.graphicsNanos)
                append(',').append(timings.audioNanos)
                append(',').append(timings.resourcesNanos)
                append(',').append(metrics.acquireNanos)
                append(',').append(metrics.bufferUploadNanos)
                append(',').append(metrics.finishNanos)
                append(',').append(metrics.submitNanos)
                append(',').append(metrics.presentNanos)
                append(',').append(metrics.stagingNanos)
                append(',').append(metrics.draws)
                append(',').append(metrics.uploadBytes)
                append(',').append(totalNanos)
                append(',').append(cpuNanos)
                append(',').append(finishedNanos)
            },
        )
        val complete = tick >= configuration.ticks
        if (complete) Log.i(TAG, "DONE ${configuration.label}")
        return complete
    }

    private companion object {
        const val TAG = "Zoot-PERF"
    }
}
