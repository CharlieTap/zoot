@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.graphics.webgpu

import com.tap.zoot.graphics.DrawState
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.native.oot_gpu_begin
import com.tap.zoot.graphics.native.oot_gpu_clear
import com.tap.zoot.graphics.native.oot_gpu_copy
import com.tap.zoot.graphics.native.oot_gpu_create
import com.tap.zoot.graphics.native.oot_gpu_delete_texture
import com.tap.zoot.graphics.native.oot_gpu_depth
import com.tap.zoot.graphics.native.oot_gpu_destroy
import com.tap.zoot.graphics.native.oot_gpu_draw
import com.tap.zoot.graphics.native.oot_gpu_end
import com.tap.zoot.graphics.native.oot_gpu_read
import com.tap.zoot.graphics.native.oot_gpu_shader
import com.tap.zoot.graphics.native.oot_gpu_target
import com.tap.zoot.graphics.native.oot_gpu_texture
import com.tap.zoot.graphics.native.oot_gpu_upscaler
import com.tap.zoot.graphics.upscaler.TextureFilter
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.graphics.upscaler.presentationShader
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.charlietap.chasm.host.HostMemory
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.usePinned
import platform.QuartzCore.CAMetalLayer

@AssistedInject
class IosWebGpuRenderer(
    @Assisted private val layer: CAMetalLayer,
    @Assisted width: Int,
    @Assisted height: Int,
    @Assisted upscaler: Upscaler,
) : Renderer {
    private val drawState = ByteArray(DrawState.SIZE)
    private var readback = ByteArray(0)
    private val gpu =
        checkNotNull(
            oot_gpu_create(interpretCPointer<COpaque>(layer.objcPtr()), width, height),
        ) { "Could not create the Metal WebGPU device" }

    init {
        setUpscaler(upscaler)
    }

    override fun createShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    ) {
        attributeSizes.usePinned {
            oot_gpu_shader(
                gpu,
                shaderId,
                wgsl,
                vertexStride,
                if (attributeSizes.isEmpty()) null else it.addressOf(0),
                attributeSizes.size,
            )
        }
    }

    override fun uploadTexture(
        textureId: Int,
        pixels: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
    ) {
        pixels.usePinned { oot_gpu_texture(gpu, textureId, it.addressOf(offset), width, height) }
    }

    override fun deleteTexture(textureId: Int) = oot_gpu_delete_texture(gpu, textureId)

    override fun resizeTarget(
        targetId: Int,
        width: Int,
        height: Int,
    ) = oot_gpu_target(gpu, targetId, width, height)

    override fun beginFrame() = oot_gpu_begin(gpu)

    override fun clearTarget(
        targetId: Int,
        clearColour: Boolean,
        clearDepth: Boolean,
    ) = oot_gpu_clear(gpu, targetId, if (clearColour) 1 else 0, if (clearDepth) 1 else 0)

    override fun draw(
        memory: HostMemory,
        drawState: Int,
        vertices: ByteArray,
        offset: Int,
        size: Int,
        vertexCount: Int,
    ) {
        memory.read(this.drawState, drawState, this.drawState.size)
        this.drawState.usePinned { state ->
            vertices.usePinned { pinned -> oot_gpu_draw(gpu, state.addressOf(0), pinned.addressOf(offset), size, vertexCount) }
        }
    }

    override fun copyTarget(
        destinationId: Int,
        sourceId: Int,
    ) = oot_gpu_copy(gpu, destinationId, sourceId)

    override fun setUpscaler(upscaler: Upscaler) =
        oot_gpu_upscaler(gpu, upscaler.presentationShader(), if (upscaler.filter == TextureFilter.Linear) 1 else 0)

    override fun endFrame() = oot_gpu_end(gpu)

    override fun readTargetRgba16(
        targetId: Int,
        width: Int,
        height: Int,
        memory: HostMemory,
        destination: Int,
    ) {
        val size = width * height * 2
        if (readback.size < size) readback = ByteArray(size)
        readback.usePinned { oot_gpu_read(gpu, targetId, width, height, it.addressOf(0)) }
        memory.write(destination, readback, 0, size)
    }

    override fun readDepth(
        targetId: Int,
        x: Float,
        y: Float,
    ): Int = oot_gpu_depth(gpu, targetId, x, y)

    override fun close() = oot_gpu_destroy(gpu)

    @AssistedFactory
    fun interface Factory {
        fun create(
            layer: CAMetalLayer,
            width: Int,
            height: Int,
            upscaler: Upscaler,
        ): IosWebGpuRenderer
    }
}
