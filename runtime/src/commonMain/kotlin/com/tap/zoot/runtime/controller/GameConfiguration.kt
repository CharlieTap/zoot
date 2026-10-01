package com.tap.zoot.runtime.controller

import com.tap.zoot.graphics.upscaler.Upscaler

data class GameVolumes(
    val master: Float = 1f,
    val music: Float = 1f,
    val effects: Float = 1f,
    val fanfares: Float = 1f,
)

data class GameConfiguration(
    val upscaler: Upscaler,
    val volumes: GameVolumes,
)
