package com.tap.zoot.performance

import com.tap.zoot.runtime.controller.FrameTelemetry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** Bounded hand-off from the game thread. Recording a frame does not allocate. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PerformanceTelemetry : FrameTelemetry {
    private val lock = SynchronizedObject()
    private val times = LongArray(HISTORY_CAPACITY)
    private val intervals = FloatArray(HISTORY_CAPACITY)
    private val steps = FloatArray(HISTORY_CAPACITY)
    private val audio = FloatArray(HISTORY_CAPACITY)
    private val gpuHost = FloatArray(HISTORY_CAPACITY)
    private var next = 0
    private var count = 0
    private var revision = 0L
    private var previousFinish = 0L

    override fun reset() {
        synchronized(lock) {
            next = 0
            count = 0
            previousFinish = 0
            revision++
        }
    }

    override fun record(
        finishedNanos: Long,
        stepNanos: Long,
        audioNanos: Long,
        graphicsNanos: Long,
    ) = synchronized(lock) {
        if (previousFinish != 0L) {
            times[next] = finishedNanos
            intervals[next] = (finishedNanos - previousFinish) / 1_000_000f
            steps[next] = stepNanos / 1_000_000f
            audio[next] = audioNanos / 1_000_000f
            gpuHost[next] = graphicsNanos / 1_000_000f
            next = (next + 1) % HISTORY_CAPACITY
            count = (count + 1).coerceAtMost(HISTORY_CAPACITY)
            revision++
        }
        previousFinish = finishedNanos
    }

    /** Copy only new samples into a reusable UI-owned buffer; never draw while holding the lock. */
    fun copyInto(history: FrameHistory): Boolean =
        synchronized(lock) {
            if (history.revision == revision) return false
            val first = (next - count + HISTORY_CAPACITY) % HISTORY_CAPACITY
            for (index in 0 until count) {
                val source = (first + index) % HISTORY_CAPACITY
                history.times[index] = times[source]
                history.intervals[index] = intervals[source]
                history.steps[index] = steps[source]
                history.audio[index] = audio[source]
                history.gpuHost[index] = gpuHost[source]
            }
            history.count = count
            history.revision = revision
            return true
        }
}
