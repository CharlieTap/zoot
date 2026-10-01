package com.tap.zoot.runtime.engine

internal object GuestAbi {
    const val VERSION = 3
    const val INPUT_SIZE = 20
    const val INPUT_VERSION = 0
    const val INPUT_LENGTH = 4
    const val INPUT_BUTTONS = 8
    const val INPUT_STICK = 12
    const val TICK_MICROS = 50_000L
    const val TICK_NANOS = TICK_MICROS * 1_000
    val AUDIO_CHUNK_FRAMES = intArrayOf(544, 528, 528)
}
