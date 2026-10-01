package com.tap.zoot.graphics

object DrawState {
    const val SHADER = 0
    const val FLAGS = 4
    const val TARGET = 8
    const val VIEWPORT_X = 12
    const val VIEWPORT_Y = 16
    const val VIEWPORT_WIDTH = 20
    const val VIEWPORT_HEIGHT = 24
    const val SCISSOR_X = 28
    const val SCISSOR_Y = 32
    const val SCISSOR_WIDTH = 36
    const val SCISSOR_HEIGHT = 40
    const val TEXTURES = 44
    const val SAMPLERS = 68
    const val UNIFORMS = 92
    const val UNIFORMS_SIZE = 16
    const val TEXTURE_SLOTS = 6
    const val SIZE = 108
}

object DrawFlags {
    const val DEPTH_TEST = 1
    const val DEPTH_WRITE = 2
    const val DECAL = 4
    const val BLEND = 8
}

object SamplerWrap {
    const val MIRROR = 1
    const val CLAMP = 2
    const val AXIS_BITS = 2
}
