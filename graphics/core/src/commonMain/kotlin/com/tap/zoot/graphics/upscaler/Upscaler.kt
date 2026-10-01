package com.tap.zoot.graphics.upscaler

import kotlin.jvm.JvmInline

@JvmInline
value class UpscalerId(
    val value: String,
) {
    companion object {
        val Nearest = UpscalerId("nearest")
        val Bilinear = UpscalerId("bilinear")
        val Sgsr1 = UpscalerId("sgsr1")
        val Default = Sgsr1
    }
}

enum class TextureFilter { Nearest, Linear }

/** A single-pass presentation filter. Register implementations in Metro's upscaler set. */
interface Upscaler {
    val id: UpscalerId
    val order: Int
    val name: String
    val filter: TextureFilter
        get() = TextureFilter.Linear

    /** WGSL defining `upscale(uv: vec2f) -> vec4f`, using `image` and `imageSampler`. */
    val shaderSource: String
}
