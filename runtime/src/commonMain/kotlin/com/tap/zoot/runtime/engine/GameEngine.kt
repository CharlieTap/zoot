package com.tap.zoot.runtime.engine

import com.tap.n64.input.ControllerState
import com.tap.zoot.graphics.Renderer

/** Drives one initialised Ocarina of Time guest instance. */
interface GameEngine : AutoCloseable {
    val timings: HostTimings

    fun step(
        tick: Long,
        input: ControllerState,
    )

    fun mixAudio()

    fun traceEntrance(entrance: Int)

    fun setOutputVolume(volume: Float)

    fun setMixVolumes(
        music: Float,
        effects: Float,
        fanfares: Float,
    )

    fun pauseAudio()

    fun resumeAudio()

    fun interface Factory {
        fun create(renderer: Renderer): GameEngine
    }
}
