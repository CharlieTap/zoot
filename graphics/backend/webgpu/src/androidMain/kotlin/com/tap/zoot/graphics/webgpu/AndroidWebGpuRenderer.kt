package com.tap.zoot.graphics.webgpu

import android.view.Surface
import androidx.webgpu.GPUBuffer
import androidx.webgpu.GPUCommandBuffer
import androidx.webgpu.GPUCommandEncoder
import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPURenderPassEncoder
import androidx.webgpu.GPUTexelCopyBufferInfo
import androidx.webgpu.GPUTexelCopyBufferLayout
import androidx.webgpu.GPUTexelCopyTextureInfo
import androidx.webgpu.MapMode
import com.tap.zoot.graphics.DrawState
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.RendererFrameMetrics
import com.tap.zoot.graphics.upscaler.Upscaler
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.charlietap.chasm.host.HostMemory
import java.nio.ByteBuffer

/** Translates already-decoded draw batches into WebGPU calls. No N64 commands enter this class. */
@AssistedInject
class AndroidWebGpuRenderer(
    @Assisted surface: Surface,
    @Assisted width: Int,
    @Assisted height: Int,
    @Assisted upscaler: Upscaler,
    settings: WebGpuSettings,
) : Renderer {
    private val context = AndroidWebGpuContext(surface, width, height, settings)
    private val device = context.device
    private val queue = context.queue
    private val counters = FrameCounters()
    private val samplers = Samplers(device)
    private val uploads = VertexUploads(device)
    private val textureStaging = TextureStaging()
    private val bindGroups = BindGroups(device, samplers, counters)
    private val pipelines = DrawPipelines(context, bindGroups.layout, PipelineHistory(settings.pipelineHistory), counters)
    private val blitter = TargetBlitter(device, samplers)
    private val readbacks = ReadbackBuffer(device)
    private var depthProbe: DepthProbe? = null
    private var presentation = PresentationRenderer(device, context.format, upscaler)
    private val presentations = mutableMapOf(upscaler to presentation)
    private val textures = HashMap<Int, GpuImage>()
    private val targets = HashMap<Int, RenderTarget>()
    private val clears = PendingClears()
    private val bindings = PassBindings()
    private val drawState = GuestBytes()
    private val guestData = GuestBytes()
    private var encoder: GPUCommandEncoder? = null
    private var submission: Array<GPUCommandBuffer>? = null
    private var pass: GPURenderPassEncoder? = null
    private var passTarget = NO_TARGET

    override val frameMetrics: RendererFrameMetrics
        get() = counters

    override val deviceDescription: String
        get() = context.description

    init {
        upload(WHITE_TEXTURE, guestData.wrap(byteArrayOf(-1, -1, -1, -1)), 1, 1)
    }

    override fun createShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    ) = pipelines.defineShader(shaderId, wgsl, vertexStride, attributeSizes)

    override fun uploadTexture(
        textureId: Int,
        memory: HostMemory,
        pixels: Int,
        width: Int,
        height: Int,
    ) {
        val start = System.nanoTime()
        upload(textureId, guestData.load(memory, pixels, width * height * 4), width, height)
        counters.stagingNanos += System.nanoTime() - start
        counters.uploadBytes += width * height * 4
        counters.textureUploads++
    }

    private fun upload(
        textureId: Int,
        source: GuestBytes,
        width: Int,
        height: Int,
    ) {
        val image =
            textures[textureId]?.takeIf { it.hasSize(width, height) } ?: device.createImage(width, height).also {
                bindGroups.evictTexture(textureId)
                textures.put(textureId, it)?.close()
            }
        textureStaging.upload(queue, image.texture, source, width, height)
    }

    override fun deleteTexture(textureId: Int) {
        bindGroups.evictTexture(textureId)
        textures.remove(textureId)?.close()
    }

    override fun resizeTarget(
        targetId: Int,
        width: Int,
        height: Int,
    ) {
        if (targets[targetId]?.color?.hasSize(width, height) == true) return
        if (passTarget == targetId) endPass()
        bindGroups.evictTarget(targetId)
        targets.remove(targetId)?.let {
            blitter.forget(it.color)
            depthProbe?.forget(it.depth)
            it.close()
        }
        targets[targetId] = device.createTarget(width, height)
    }

    override fun beginFrame() {
        context.checkDevice()
        check(encoder == null)
        counters.reset()
        uploads.reset()
        encoder = device.createCommandEncoder()
    }

    /** Clears are folded into the target's next pass as its load operations. */
    override fun clearTarget(
        targetId: Int,
        clearColour: Boolean,
        clearDepth: Boolean,
    ) {
        if (passTarget == targetId) endPass()
        clears.request(targetId, clearColour, clearDepth)
    }

    override fun draw(
        memory: HostMemory,
        drawState: Int,
        vertices: Int,
        size: Int,
        vertexCount: Int,
    ) {
        val state = this.drawState.load(memory, drawState, DrawState.SIZE)
        val targetId = state.int(DrawState.TARGET)
        val target = targets.getValue(targetId).color
        val scissorHeight = state.int(DrawState.SCISSOR_HEIGHT)
        val clipX = state.int(DrawState.SCISSOR_X).coerceIn(0, target.width)
        val clipY = (target.height - state.int(DrawState.SCISSOR_Y) - scissorHeight).coerceIn(0, target.height)
        val clipWidth = state.int(DrawState.SCISSOR_WIDTH).coerceIn(0, target.width - clipX)
        val clipHeight = scissorHeight.coerceIn(0, target.height - clipY)
        if (clipWidth == 0 || clipHeight == 0 || vertexCount == 0) return
        val shaderId = state.int(DrawState.SHADER)
        val stagingStart = System.nanoTime()
        val data = guestData.load(memory, vertices, size)
        val firstVertex = uploads.stage(data.bytes, data.offset, size, pipelines.stride(shaderId))
        counters.stagingNanos += System.nanoTime() - stagingStart
        counters.uploadBytes += size
        val pipeline = pipelines[shaderId, state.int(DrawState.FLAGS)]
        val bindGroup = bindGroups.find(state) ?: bindGroups.createForProbe(::bindingImage)
        clearSampledTargets()
        if (passTarget != targetId) {
            endPass()
            beginPass(targetId)
        }
        val pass = checkNotNull(pass)
        bindings.pipeline(pass, pipeline)
        bindings.bindGroup(pass, bindGroup)
        bindings.vertices(pass, uploads.buffer)
        bindings.params(pass, state)
        bindings.viewport(pass, state, target.height)
        bindings.scissor(pass, clipX, clipY, clipWidth, clipHeight)
        pass.draw(vertexCount, 1, firstVertex, 0)
        counters.draws++
    }

    // A target sampled by this draw must have its pending clear applied before the draw's pass.
    private fun clearSampledTargets() {
        for (slot in 0 until DrawState.TEXTURE_SLOTS) {
            val texture = bindGroups.probedTexture(slot)
            if (texture < 0) flushClear(texture and Int.MAX_VALUE)
        }
    }

    override fun copyTarget(
        destinationId: Int,
        sourceId: Int,
    ) {
        endPass()
        flushClear(sourceId)
        // The blit replaces all of the destination's colour, so only a pending depth clear survives.
        clears.dropColour(destinationId)
        blitter.blit(checkNotNull(encoder), targets.getValue(sourceId).color, targets.getValue(destinationId).color.view)
    }

    override fun setUpscaler(upscaler: Upscaler) {
        presentation = presentations.getOrPut(upscaler) { PresentationRenderer(device, context.format, upscaler) }
    }

    override fun endFrame() {
        endPass()
        for (targetId in clears.targets) flushClear(targetId)
        val acquireStart = System.nanoTime()
        val current = context.surface.getCurrentTexture()
        counters.acquireNanos += System.nanoTime() - acquireStart
        val texture = checkNotNull(current.texture) { "Surface unavailable: ${current.status}" }
        texture.createView().use { view -> presentation.draw(checkNotNull(encoder), targets.getValue(SCREEN_TARGET).color.view, view) }
        submit()
        val presentStart = System.nanoTime()
        context.surface.present()
        counters.presentNanos += System.nanoTime() - presentStart
        texture.close()
        context.processEvents()
        pipelines.warm()
        pipelines.saveHistory()
    }

    override fun readTargetRgba16(
        targetId: Int,
        width: Int,
        height: Int,
        memory: HostMemory,
        destination: Int,
    ) {
        val image = targets.getValue(targetId).color
        val stride = (image.width * 4 + 255) and -256
        val size = stride * image.height
        val buffer = readbacks.buffer(size)
        val pixels =
            roundTrip(targetId, buffer, size) { encoder ->
                encoder.copyTextureToBuffer(
                    GPUTexelCopyTextureInfo(image.texture),
                    GPUTexelCopyBufferInfo(buffer, GPUTexelCopyBufferLayout(bytesPerRow = stride, rowsPerImage = image.height)),
                    GPUExtent3D(image.width, image.height),
                )
            }
        for (y in 0 until height) {
            val row = (y * image.height / height) * stride
            for (x in 0 until width) {
                val pixel = pixels.getInt(row + (x * image.width / width) * 4)
                memory.writeI16(destination + (y * width + x) * 2, rgba5551(pixel).toShort())
            }
        }
    }

    override fun readDepth(
        targetId: Int,
        x: Float,
        y: Float,
    ): Int {
        val image = targets[targetId]?.depth ?: return NO_DEPTH
        val probe = depthProbe()
        probe.point(0, column(image, x), row(image, y))
        return encodeDepth(probe(probe, targetId, image, 1).getFloat(0))
    }

    override fun readDepths(
        targetId: Int,
        memory: HostMemory,
        coordinates: Int,
        count: Int,
        destination: Int,
    ) {
        val image = targets[targetId]?.depth
        if (image == null) {
            repeat(count) { memory.writeI32(destination + it * 4, NO_DEPTH) }
            return
        }
        val probe = depthProbe()
        for (start in 0 until count step DepthProbe.BATCH) {
            val batch = minOf(DepthProbe.BATCH, count - start)
            for (index in 0 until batch) {
                val point = coordinates + (start + index) * 8
                probe.point(index, column(image, memory.readF32(point)), row(image, memory.readF32(point + 4)))
            }
            val depths = probe(probe, targetId, image, batch)
            for (index in 0 until batch) memory.writeI32(destination + (start + index) * 4, encodeDepth(depths.getFloat(index * 4)))
        }
    }

    // Created on the first depth query; many scenes never make one.
    private fun depthProbe(): DepthProbe = depthProbe ?: DepthProbe(device).also { depthProbe = it }

    private fun probe(
        probe: DepthProbe,
        targetId: Int,
        image: GpuImage,
        count: Int,
    ): ByteBuffer = roundTrip(targetId, probe.results, count * 4) { probe.encode(it, image, count) }

    private fun bindingImage(textureId: Int): GpuImage =
        if (textureId < 0) {
            targets.getValue(textureId and Int.MAX_VALUE).color
        } else {
            textures[textureId] ?: textures.getValue(WHITE_TEXTURE)
        }

    /** Submits work so far plus [encode], then waits for [buffer] and returns its first [size] bytes. */
    private inline fun roundTrip(
        targetId: Int,
        buffer: GPUBuffer,
        size: Int,
        encode: (GPUCommandEncoder) -> Unit,
    ): ByteBuffer {
        val start = System.nanoTime()
        val inFrame = encoder != null
        endPass()
        if (!inFrame) encoder = device.createCommandEncoder()
        flushClear(targetId)
        encode(checkNotNull(encoder))
        submit()
        context.request<Unit> { callback -> buffer.mapAsync(MapMode.Read, 0, size.toLong(), context.executor, callback) }
        val result = readbacks.read(buffer, size)
        buffer.unmap()
        if (inFrame) encoder = device.createCommandEncoder()
        counters.readbacks++
        counters.readbackNanos += System.nanoTime() - start
        return result
    }

    private fun beginPass(targetId: Int) {
        pass = checkNotNull(encoder).beginRenderPass(targets.getValue(targetId).pass(clears.take(targetId)))
        passTarget = targetId
        bindings.reset()
        counters.passes++
    }

    private fun endPass() {
        pass?.let {
            it.end()
            it.close()
        }
        pass = null
        passTarget = NO_TARGET
    }

    /** Applies a pending clear with an otherwise empty pass, for targets used before they are drawn to. */
    private fun flushClear(targetId: Int) {
        if (!clears.has(targetId) || !targets.containsKey(targetId)) return
        endPass()
        beginPass(targetId)
        endPass()
    }

    private fun submit() {
        val uploadStart = System.nanoTime()
        uploads.flush(queue)
        counters.bufferUploadNanos += System.nanoTime() - uploadStart
        val finishStart = System.nanoTime()
        val commands = checkNotNull(encoder).finish()
        counters.finishNanos += System.nanoTime() - finishStart
        val submitStart = System.nanoTime()
        val submission = submission?.also { it[0] = commands } ?: arrayOf(commands).also { submission = it }
        queue.submit(submission)
        commands.close()
        counters.submitNanos += System.nanoTime() - submitStart
        encoder?.close()
        encoder = null
    }

    override fun close() {
        endPass()
        encoder?.close()
        pipelines.saveHistory()
        presentations.values.forEach(PresentationRenderer::close)
        targets.values.forEach(RenderTarget::close)
        textures.values.forEach(GpuImage::close)
        pipelines.close()
        blitter.close()
        depthProbe?.close()
        readbacks.close()
        samplers.close()
        uploads.close()
        bindGroups.close()
        context.close()
    }

    @AssistedFactory
    fun interface Factory {
        fun create(
            surface: Surface,
            width: Int,
            height: Int,
            upscaler: Upscaler,
        ): AndroidWebGpuRenderer
    }

    private companion object {
        const val WHITE_TEXTURE = 0
        const val SCREEN_TARGET = 0
        const val NO_TARGET = -1
        const val NO_DEPTH = 65532

        fun column(
            image: GpuImage,
            x: Float,
        ) = x.toInt().coerceIn(0, image.width - 1)

        fun row(
            image: GpuImage,
            y: Float,
        ) = (image.height - 1 - y.toInt()).coerceIn(0, image.height - 1)

        fun rgba5551(pixel: Int): Int =
            ((pixel and 255) shr 3 shl 11) or (((pixel ushr 8) and 255) shr 3 shl 6) or (((pixel ushr 16) and 255) shr 3 shl 1) or
                ((pixel ushr 31) and 1)

        fun encodeDepth(depth: Float): Int = ((depth.coerceIn(0f, 1f) * 0xffffff).toInt() ushr 10) shl 2
    }
}
