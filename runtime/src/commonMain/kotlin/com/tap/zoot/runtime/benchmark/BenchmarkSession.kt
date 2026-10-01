package com.tap.zoot.runtime.benchmark

import com.tap.n64.input.ControllerState
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.runtime.engine.HostTimings

/** Optional release-benchmark hook. The ordinary game loop only takes predictable null branches. */
interface BenchmarkSession {
    val entranceTick: Long?

    val entrance: Int

    fun start(renderer: Renderer)

    fun inputFor(
        tick: Long,
        live: ControllerState,
    ): ControllerState

    fun startFrame(): Long

    /** Records one completed frame and returns true after the requested final frame. */
    fun record(
        tick: Long,
        stepNanos: Long,
        audioNanos: Long,
        totalNanos: Long,
        cpuStartedNanos: Long,
        finishedNanos: Long,
        timings: HostTimings,
    ): Boolean
}
