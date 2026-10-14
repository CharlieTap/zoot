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
import com.tap.zoot.graphics.native.oot_gpu_depths
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
import kotlinx.cinterop.reinterpret
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
    private var guest = ByteArray(0)
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
        memory: HostMemory,
        pixels: Int,
        width: Int,
        height: Int,
    ) {
        val bytes = copy(memory, pixels, width * height * 4)
        bytes.usePinned { oot_gpu_texture(gpu, textureId, it.addressOf(0), width, height) }
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
        vertices: Int,
        size: Int,
        vertexCount: Int,
    ) {
        memory.read(this.drawState, drawState, this.drawState.size)
        val bytes = copy(memory, vertices, size)
        this.drawState.usePinned { state ->
            bytes.usePinned { pinned -> oot_gpu_draw(gpu, state.addressOf(0), pinned.addressOf(0), size, vertexCount) }
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

    override fun readDepths(
        targetId: Int,
        memory: HostMemory,
        coordinates: Int,
        count: Int,
        destination: Int,
    ) {
        if (count <= 0) return
        val points = copy(memory, coordinates, count * 8)
        val size = count * 4
        if (readback.size < size) readback = ByteArray(size)
        points.usePinned { input ->
            readback.usePinned { output ->
                oot_gpu_depths(gpu, targetId, input.addressOf(0).reinterpret(), count, output.addressOf(0).reinterpret())
            }
        }
        memory.write(destination, readback, 0, size)
    }

    // Chasm's iOS memory has no borrow API, so guest data is copied into one reused array.
    private fun copy(
        memory: HostMemory,
        pointer: Int,
        size: Int,
    ): ByteArray {
        if (guest.size < size) guest = ByteArray(maxOf(size, guest.size * 2))
        memory.read(guest, pointer, size)
        return guest
    }

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
