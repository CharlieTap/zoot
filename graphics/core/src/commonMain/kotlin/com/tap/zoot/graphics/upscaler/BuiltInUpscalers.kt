package com.tap.zoot.graphics.upscaler

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ContributesIntoSet(AppScope::class, binding<Upscaler>())
@Inject
class NearestUpscaler : Upscaler {
    override val id = UpscalerId.Nearest
    override val order = 0
    override val name = "Nearest"
    override val filter = TextureFilter.Nearest
    override val shaderSource = SAMPLE_SHADER
}

@ContributesIntoSet(AppScope::class, binding<Upscaler>())
@Inject
class BilinearUpscaler : Upscaler {
    override val id = UpscalerId.Bilinear
    override val order = 1
    override val name = "Bilinear"
    override val shaderSource = SAMPLE_SHADER
}

private const val SAMPLE_SHADER = """
fn upscale(uv: vec2f) -> vec4f {
    return textureSampleLevel(image, imageSampler, uv, 0.0);
}
"""
