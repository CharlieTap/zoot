package com.tap.zoot.performance

/** Owned by the UI thread. Kept separate from Compose so timing calculations can be unit tested. */
class FrameHistory {
    internal val times = LongArray(HISTORY_CAPACITY)
    internal val intervals = FloatArray(HISTORY_CAPACITY)
    internal val steps = FloatArray(HISTORY_CAPACITY)
    internal val audio = FloatArray(HISTORY_CAPACITY)
    internal val gpuHost = FloatArray(HISTORY_CAPACITY)
    internal var count = 0
    internal var revision = -1L

    val isEmpty: Boolean
        get() = count == 0

    val newestTime: Long
        get() = times[count - 1]

    fun metrics(nowNanos: Long): PerformanceMetrics {
        if (count == 0) return PerformanceMetrics()
        val ageMs = (nowNanos - times[count - 1]) / 1_000_000f
        if (ageMs > 1_000f) return PerformanceMetrics(frameMs = ageMs)
        var samples = 0
        var frameSum = 0f
        var stepSum = 0f
        var audioSum = 0f
        var hostSum = 0f
        for (index in count - 1 downTo 0) {
            if (samples > 0 && nowNanos - times[index] >= METRICS_WINDOW_NANOS) break
            frameSum += intervals[index]
            stepSum += steps[index]
            audioSum += audio[index]
            hostSum += gpuHost[index]
            samples++
        }
        val frameMs = frameSum / samples
        return PerformanceMetrics(
            fps = if (frameMs > 0f) 1_000f / frameMs else 0f,
            frameMs = frameMs,
            stepMs = stepSum / samples,
            audioMs = audioSum / samples,
            gpuHostMs = hostSum / samples,
        )
    }
}

data class PerformanceMetrics(
    val fps: Float = 0f,
    val frameMs: Float = 0f,
    val stepMs: Float = 0f,
    val audioMs: Float = 0f,
    val gpuHostMs: Float = 0f,
)

internal const val HISTORY_CAPACITY = 120
internal const val METRICS_WINDOW_NANOS = 500_000_000L
