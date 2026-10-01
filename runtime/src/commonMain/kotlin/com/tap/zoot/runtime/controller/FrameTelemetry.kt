package com.tap.zoot.runtime.controller

interface FrameTelemetry {
    fun reset()

    fun record(
        finishedNanos: Long,
        stepNanos: Long,
        audioNanos: Long,
        graphicsNanos: Long,
    )
}
