package com.tap.zoot.runtime

import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.upscaler.Upscaler
import io.github.charlietap.chasm.host.HostMemory

internal class TestRenderer : Renderer {
    var closed = false

    override fun createShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    ) = Unit

    override fun uploadTexture(
        textureId: Int,
        pixels: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
    ) = Unit

    override fun deleteTexture(textureId: Int) = Unit

    override fun resizeTarget(
        targetId: Int,
        width: Int,
        height: Int,
    ) = Unit

    override fun beginFrame() = Unit

    override fun clearTarget(
        targetId: Int,
        clearColour: Boolean,
        clearDepth: Boolean,
    ) = Unit

    override fun draw(
        memory: HostMemory,
        drawState: Int,
        vertices: ByteArray,
        offset: Int,
        size: Int,
        vertexCount: Int,
    ) = Unit

    override fun copyTarget(
        destinationId: Int,
        sourceId: Int,
    ) = Unit

    override fun setUpscaler(upscaler: Upscaler) = Unit

    override fun endFrame() = Unit

    override fun readTargetRgba16(
        targetId: Int,
        width: Int,
        height: Int,
        memory: HostMemory,
        destination: Int,
    ) = Unit

    override fun readDepth(
        targetId: Int,
        x: Float,
        y: Float,
    ): Int = 0

    override fun close() {
        closed = true
    }
}
