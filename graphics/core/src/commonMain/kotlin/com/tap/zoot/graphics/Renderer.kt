package com.tap.zoot.graphics

import com.tap.zoot.graphics.upscaler.Upscaler
import io.github.charlietap.chasm.host.HostMemory

/** Renders the guest's decoded draw batches. Fast3D processing stays in Wasm. */
interface Renderer : AutoCloseable {
    val frameMetrics: RendererFrameMetrics?
        get() = null

    fun createShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    )

    fun uploadTexture(
        textureId: Int,
        pixels: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
    )

    fun deleteTexture(textureId: Int)

    fun resizeTarget(
        targetId: Int,
        width: Int,
        height: Int,
    )

    fun beginFrame()

    fun clearTarget(
        targetId: Int,
        clearColour: Boolean,
        clearDepth: Boolean,
    )

    fun draw(
        memory: HostMemory,
        drawState: Int,
        vertices: ByteArray,
        offset: Int,
        size: Int,
        vertexCount: Int,
    )

    fun copyTarget(
        destinationId: Int,
        sourceId: Int,
    )

    fun setUpscaler(upscaler: Upscaler)

    fun endFrame()

    fun readTargetRgba16(
        targetId: Int,
        width: Int,
        height: Int,
        memory: HostMemory,
        destination: Int,
    )

    fun readDepth(
        targetId: Int,
        x: Float,
        y: Float,
    ): Int
}

fun interface RendererFactory {
    fun create(upscaler: Upscaler): Renderer
}
