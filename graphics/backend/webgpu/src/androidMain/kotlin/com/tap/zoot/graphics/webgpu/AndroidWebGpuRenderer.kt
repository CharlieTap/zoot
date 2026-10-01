package com.tap.zoot.graphics.webgpu

import android.view.Surface
import androidx.webgpu.BufferUsage
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUColor
import androidx.webgpu.GPUCommandEncoder
import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPURenderPassColorAttachment
import androidx.webgpu.GPURenderPassDepthStencilAttachment
import androidx.webgpu.GPURenderPassDescriptor
import androidx.webgpu.GPURenderPassEncoder
import androidx.webgpu.GPUTexelCopyBufferInfo
import androidx.webgpu.GPUTexelCopyBufferLayout
import androidx.webgpu.GPUTexelCopyTextureInfo
import androidx.webgpu.LoadOp
import androidx.webgpu.MapMode
import androidx.webgpu.StoreOp
import androidx.webgpu.TextureFormat
import com.tap.zoot.graphics.DrawState
import com.tap.zoot.graphics.Renderer
import com.tap.zoot.graphics.RendererFrameMetrics
import com.tap.zoot.graphics.upscaler.Upscaler
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.charlietap.chasm.host.HostMemory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Translates already-decoded draw batches into WebGPU calls. No N64 commands enter this class. */
@AssistedInject
class AndroidWebGpuRenderer(
    @Assisted surface: Surface,
    @Assisted width: Int,
    @Assisted height: Int,
    @Assisted upscaler: Upscaler,
) : Renderer {
    private val context = AndroidWebGpuContext(surface, width, height)
    private val device = context.device
    private val queue = context.queue
    private val counters = FrameCounters()
    private val samplers = Samplers(device)
    private val uploads = FrameUploads(device)
    private val bindGroups = BindGroups(device, samplers, uploads.uniformBuffer)
    private val pipelines = DrawPipelines(device, bindGroups.layout)
    private val textureStaging = TextureStaging()
    private val blitter = TargetBlitter(device, samplers)
    private var presentation = PresentationRenderer(device, context.format, upscaler)
    private val presentations = mutableMapOf(upscaler to presentation)
    private val textures = HashMap<Int, GpuImage>()
    private val targets = HashMap<Int, RenderTarget>()
    private val dynamicOffset = IntArray(1)
    private var encoder: GPUCommandEncoder? = null
    private var pass: GPURenderPassEncoder? = null
    private var passTarget = NO_TARGET

    override val frameMetrics: RendererFrameMetrics
        get() = counters

    init {
        uploadTexture(WHITE_TEXTURE, byteArrayOf(-1, -1, -1, -1), 0, 1, 1)
    }

    override fun createShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    ) = pipelines.defineShader(shaderId, wgsl, vertexStride, attributeSizes)

    override fun uploadTexture(
        textureId: Int,
        pixels: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
    ) {
        val image =
            textures[textureId]?.takeIf { it.hasSize(width, height) } ?: device.createImage(width, height).also {
                bindGroups.clear()
                textures.put(textureId, it)?.close()
            }
        val length = width * height * 4
        val stagingStart = System.nanoTime()
        val staged = textureStaging.stage(pixels, offset, length)
        counters.uploadBytes += length
        counters.stagingNanos += System.nanoTime() - stagingStart
        queue.writeTexture(
            GPUTexelCopyTextureInfo(image.texture),
            staged,
            GPUExtent3D(width, height),
            GPUTexelCopyBufferLayout(bytesPerRow = width * 4, rowsPerImage = height),
        )
    }

    override fun deleteTexture(textureId: Int) {
        bindGroups.clear()
        textures.remove(textureId)?.close()
    }

    override fun resizeTarget(
        targetId: Int,
        width: Int,
        height: Int,
    ) {
        if (targets[targetId]?.color?.hasSize(width, height) == true) return
        endPass()
        bindGroups.clear()
        targets.remove(targetId)?.close()
        targets[targetId] = RenderTarget(device.createImage(width, height), device.createImage(width, height, TextureFormat.Depth32Float))
    }

    override fun beginFrame() {
        check(encoder == null)
        counters.reset()
        uploads.reset()
        encoder = device.createCommandEncoder()
    }

    override fun clearTarget(
        targetId: Int,
        clearColour: Boolean,
        clearDepth: Boolean,
    ) {
        endPass()
        beginPass(targetId, clearColour, clearDepth)
        endPass()
    }

    override fun draw(
        memory: HostMemory,
        drawState: Int,
        vertices: ByteArray,
        offset: Int,
        size: Int,
        vertexCount: Int,
    ) {
        val shaderId = memory.readI32(drawState + DrawState.SHADER)
        val flags = memory.readI32(drawState + DrawState.FLAGS)
        val targetId = memory.readI32(drawState + DrawState.TARGET)
        val target = targets.getValue(targetId).color
        val scissorHeight = memory.readI32(drawState + DrawState.SCISSOR_HEIGHT)
        val clipX = memory.readI32(drawState + DrawState.SCISSOR_X).coerceIn(0, target.width)
        val clipY = (target.height - memory.readI32(drawState + DrawState.SCISSOR_Y) - scissorHeight).coerceIn(0, target.height)
        val clipWidth = memory.readI32(drawState + DrawState.SCISSOR_WIDTH).coerceIn(0, target.width - clipX)
        val clipHeight = scissorHeight.coerceIn(0, target.height - clipY)
        if (clipWidth == 0 || clipHeight == 0 || vertexCount == 0) return
        check(uploads.fits(size))
        val stagingStart = System.nanoTime()
        uploads.stage(memory, drawState, vertices, offset, size)
        counters.stagingNanos += System.nanoTime() - stagingStart
        counters.uploadBytes += size + DrawState.UNIFORMS_SIZE
        val pipeline = pipelines[shaderId, flags]
        val bindGroup = bindGroups.find(memory, drawState) ?: bindGroups.createForProbe(::bindingImage)
        if (passTarget != targetId) {
            endPass()
            beginPass(targetId)
        }
        val pass = checkNotNull(pass)
        pass.setPipeline(pipeline)
        dynamicOffset[0] = uploads.uniformOffset.toInt()
        pass.setBindGroup(0, bindGroup, dynamicOffset)
        pass.setVertexBuffer(0, uploads.vertexBuffer, uploads.vertexOffset, size.toLong())
        val viewportX = memory.readI32(drawState + DrawState.VIEWPORT_X).toFloat()
        val viewportY = memory.readI32(drawState + DrawState.VIEWPORT_Y).toFloat()
        val viewportWidth = memory.readI32(drawState + DrawState.VIEWPORT_WIDTH).toFloat()
        val viewportHeight = memory.readI32(drawState + DrawState.VIEWPORT_HEIGHT).toFloat()
        pass.setViewport(viewportX, target.height - viewportY - viewportHeight, viewportWidth, viewportHeight, 0f, 1f)
        pass.setScissorRect(clipX, clipY, clipWidth, clipHeight)
        pass.draw(vertexCount)
        uploads.advance(size)
        counters.draws++
    }

    override fun copyTarget(
        destinationId: Int,
        sourceId: Int,
    ) {
        endPass()
        blitter.blit(
            checkNotNull(encoder),
            targets.getValue(sourceId).color,
            targets.getValue(destinationId).color.view,
            TextureFormat.RGBA8Unorm,
        )
    }

    override fun setUpscaler(upscaler: Upscaler) {
        presentation = presentations.getOrPut(upscaler) { PresentationRenderer(device, context.format, upscaler) }
    }

    override fun endFrame() {
        endPass()
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
    }

    override fun readTargetRgba16(
        targetId: Int,
        width: Int,
        height: Int,
        memory: HostMemory,
        destination: Int,
    ) {
        val image = targets.getValue(targetId).color
        val pixels = readback(image)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels.getInt(((y * image.height / height) * image.width + x * image.width / width) * 4)
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
        // Readback is an explicit device operation, not Fast3D decoding.
        val depths = readback(image)
        val column = x.toInt().coerceIn(0, image.width - 1)
        val row = (image.height - 1 - y.toInt()).coerceIn(0, image.height - 1)
        return encodeDepth(depths.getFloat((row * image.width + column) * 4))
    }

    private fun bindingImage(textureId: Int): GpuImage =
        if (textureId < 0) {
            targets.getValue(textureId and Int.MAX_VALUE).color
        } else {
            textures[textureId] ?: textures.getValue(WHITE_TEXTURE)
        }

    private fun readback(image: GpuImage): ByteBuffer {
        val inFrame = encoder != null
        endPass()
        val encoder = encoder ?: device.createCommandEncoder().also { encoder = it }
        val stride = (image.width * 4 + 255) and -256
        val size = stride * image.height
        val buffer = device.createBuffer(GPUBufferDescriptor(usage = BufferUsage.CopyDst or BufferUsage.MapRead, size = size.toLong()))
        encoder.copyTextureToBuffer(
            GPUTexelCopyTextureInfo(image.texture),
            GPUTexelCopyBufferInfo(buffer, GPUTexelCopyBufferLayout(bytesPerRow = stride, rowsPerImage = image.height)),
            GPUExtent3D(image.width, image.height),
        )
        submit()
        context.request<Unit> { callback -> buffer.mapAsync(MapMode.Read, 0, size.toLong(), context.executor, callback) }
        val mapped = buffer.getConstMappedRange(0, size.toLong())
        val output = ByteBuffer.allocate(image.width * image.height * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(image.height) { row ->
            mapped.position(row * stride)
            mapped.limit(row * stride + image.width * 4)
            output.put(mapped)
            mapped.limit(size)
        }
        buffer.unmap()
        buffer.close()
        if (inFrame) this.encoder = device.createCommandEncoder()
        return output
    }

    private fun beginPass(
        targetId: Int,
        clearColour: Boolean = false,
        clearDepth: Boolean = false,
    ) {
        val target = targets.getValue(targetId)
        pass =
            checkNotNull(encoder).beginRenderPass(
                GPURenderPassDescriptor(
                    colorAttachments =
                        arrayOf(
                            GPURenderPassColorAttachment(
                                view = target.color.view,
                                loadOp = if (clearColour) LoadOp.Clear else LoadOp.Load,
                                storeOp = StoreOp.Store,
                                clearValue = GPUColor(0.0, 0.0, 0.0, 1.0),
                            ),
                        ),
                    depthStencilAttachment =
                        GPURenderPassDepthStencilAttachment(
                            view = target.depth.view,
                            depthLoadOp = if (clearDepth) LoadOp.Clear else LoadOp.Load,
                            depthStoreOp = StoreOp.Store,
                            depthClearValue = 1f,
                        ),
                ),
            )
        passTarget = targetId
    }

    private fun endPass() {
        pass?.let {
            it.end()
            it.close()
        }
        pass = null
        passTarget = NO_TARGET
    }

    private fun submit() {
        val uploadStart = System.nanoTime()
        uploads.flush(queue)
        counters.bufferUploadNanos += System.nanoTime() - uploadStart
        val finishStart = System.nanoTime()
        val commands = checkNotNull(encoder).finish()
        counters.finishNanos += System.nanoTime() - finishStart
        val submitStart = System.nanoTime()
        commands.use { queue.submit(arrayOf(it)) }
        counters.submitNanos += System.nanoTime() - submitStart
        encoder?.close()
        encoder = null
    }

    override fun close() {
        endPass()
        encoder?.close()
        bindGroups.clear()
        presentations.values.forEach(PresentationRenderer::close)
        targets.values.forEach(RenderTarget::close)
        textures.values.forEach(GpuImage::close)
        pipelines.close()
        blitter.close()
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

        fun rgba5551(pixel: Int): Int =
            ((pixel and 255) shr 3 shl 11) or (((pixel ushr 8) and 255) shr 3 shl 6) or (((pixel ushr 16) and 255) shr 3 shl 1) or
                ((pixel ushr 31) and 1)

        fun encodeDepth(depth: Float): Int = ((depth.coerceIn(0f, 1f) * 0xffffff).toInt() ushr 10) shl 2
    }
}
