package com.tap.zoot.graphics.webgpu

import androidx.webgpu.BlendFactor
import androidx.webgpu.CompareFunction
import androidx.webgpu.GPUBindGroupLayout
import androidx.webgpu.GPUBlendComponent
import androidx.webgpu.GPUBlendState
import androidx.webgpu.GPUColorTargetState
import androidx.webgpu.GPUDepthStencilState
import androidx.webgpu.GPUFragmentState
import androidx.webgpu.GPUPipelineLayoutDescriptor
import androidx.webgpu.GPUPrimitiveState
import androidx.webgpu.GPURenderPipeline
import androidx.webgpu.GPURenderPipelineDescriptor
import androidx.webgpu.GPURequestCallback
import androidx.webgpu.GPUShaderModule
import androidx.webgpu.GPUShaderModuleDescriptor
import androidx.webgpu.GPUShaderSourceWGSL
import androidx.webgpu.GPUVertexAttribute
import androidx.webgpu.GPUVertexBufferLayout
import androidx.webgpu.GPUVertexState
import androidx.webgpu.OptionalBool
import androidx.webgpu.PrimitiveTopology
import androidx.webgpu.TextureFormat
import androidx.webgpu.VertexFormat
import com.tap.zoot.graphics.DrawFlags
import com.tap.zoot.graphics.DrawState

/**
 * Pipelines belong to shader content, not the guest's shader ids. Shaders from earlier sessions are
 * rebuilt a few per frame and their pipelines compiled on Dawn's worker threads, ahead of the guest
 * defining them just before their first draw.
 */
internal class DrawPipelines(
    private val context: AndroidWebGpuContext,
    bindGroupLayout: GPUBindGroupLayout,
    private val history: PipelineHistory,
    private val counters: FrameCounters,
) : AutoCloseable {
    private val device = context.device
    private val layout =
        device.createPipelineLayout(
            GPUPipelineLayoutDescriptor(bindGroupLayouts = arrayOf(bindGroupLayout), immediateSize = DrawState.UNIFORMS_SIZE),
        )
    private val library = HashMap<Long, DrawShader>()
    private val shaders = HashMap<Int, DrawShader>()
    private val unwarmed = ArrayDeque(history.load().entries)

    fun defineShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    ) {
        val key = PipelineHistory.key(wgsl, vertexStride)
        val known = library[key]?.takeIf { it.entry.wgsl == wgsl }
        shaders[shaderId] = known ?: build(key, PipelineHistory.Entry(wgsl, vertexStride, attributeSizes, HashSet())).also {
            history.changed(key, it.entry)
        }
    }

    fun stride(shaderId: Int): Int = shaders.getValue(shaderId).entry.vertexStride

    operator fun get(
        shaderId: Int,
        flags: Int,
    ): GPURenderPipeline {
        val shader = shaders.getValue(shaderId)
        shader.pipelines[flags]?.let { return it }
        val start = System.nanoTime()
        shader.warming[flags]?.let { warm -> context.await { warm.done } }
        val pipeline =
            shader.pipelines[flags] ?: device.createRenderPipeline(descriptor(shader, flags)).also {
                shader.pipelines[flags] = it
                if (shader.entry.flags.add(flags)) history.changed(shader.key, shader.entry)
            }
        counters.pipelinesCreated++
        counters.pipelineNanos += System.nanoTime() - start
        return pipeline
    }

    /** Rebuilds a few remembered shaders and starts their pipelines compiling. Called once per frame. */
    fun warm() {
        var built = 0
        while (built < WARM_PER_FRAME) {
            val (key, entry) = unwarmed.removeFirstOrNull() ?: return
            if (library.containsKey(key)) continue
            val shader = build(key, entry)
            built++
            for (flags in entry.flags) {
                val warm = Warming(shader, flags)
                shader.warming[flags] = warm
                device.createRenderPipelineAsync(descriptor(shader, flags), context.executor, warm)
            }
        }
    }

    fun saveHistory() = history.save()

    private fun build(
        key: Long,
        entry: PipelineHistory.Entry,
    ): DrawShader {
        var offset = 0L
        val attributes =
            entry.attributeSizes
                .mapIndexed { index, components ->
                    GPUVertexAttribute(
                        format = vertexFormat(components),
                        offset = offset,
                        shaderLocation = index,
                    ).also { offset += components * Float.SIZE_BYTES }
                }.toTypedArray()
        return DrawShader(
            key,
            entry,
            device.createShaderModule(GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(entry.wgsl))),
            GPUVertexBufferLayout(arrayStride = entry.vertexStride.toLong(), attributes = attributes),
        ).also { library[key] = it }
    }

    private fun descriptor(
        shader: DrawShader,
        flags: Int,
    ) = GPURenderPipelineDescriptor(
        layout = layout,
        vertex = GPUVertexState(shader.module, "vertexMain", buffers = arrayOf(shader.vertices)),
        primitive = GPUPrimitiveState(topology = PrimitiveTopology.TriangleList),
        depthStencil = depthState(flags),
        fragment =
            GPUFragmentState(
                shader.module,
                "fragmentMain",
                targets = arrayOf(GPUColorTargetState(TextureFormat.RGBA8Unorm, blend = blendState(flags))),
            ),
    )

    override fun close() {
        for (shader in library.values) {
            shader.pipelines.values.forEach(GPURenderPipeline::close)
            shader.module.close()
        }
        layout.close()
    }

    private class Warming(
        private val shader: DrawShader,
        private val flags: Int,
    ) : GPURequestCallback<GPURenderPipeline> {
        var done = false

        override fun onResult(result: GPURenderPipeline) {
            done = true
            shader.warming.remove(flags)
            if (shader.pipelines.putIfAbsent(flags, result) != null) result.close()
        }

        override fun onError(exception: Exception) {
            done = true
            shader.warming.remove(flags)
        }
    }

    private class DrawShader(
        val key: Long,
        val entry: PipelineHistory.Entry,
        val module: GPUShaderModule,
        val vertices: GPUVertexBufferLayout,
    ) {
        val pipelines = HashMap<Int, GPURenderPipeline>()
        val warming = HashMap<Int, Warming>()
    }

    private companion object {
        const val WARM_PER_FRAME = 4
    }
}

private fun vertexFormat(components: Int): Int =
    when (components) {
        1 -> VertexFormat.Float32
        2 -> VertexFormat.Float32x2
        3 -> VertexFormat.Float32x3
        else -> VertexFormat.Float32x4
    }

private fun blendState(flags: Int): GPUBlendState? =
    if (flags and DrawFlags.BLEND != 0) {
        GPUBlendState(
            color = GPUBlendComponent(srcFactor = BlendFactor.SrcAlpha, dstFactor = BlendFactor.OneMinusSrcAlpha),
            alpha = GPUBlendComponent(srcFactor = BlendFactor.One, dstFactor = BlendFactor.OneMinusSrcAlpha),
        )
    } else {
        null
    }

internal fun depthState(flags: Int): GPUDepthStencilState {
    val decal = flags and DrawFlags.DECAL != 0
    return GPUDepthStencilState(
        format = TextureFormat.Depth32Float,
        depthWriteEnabled = if (flags and DrawFlags.DEPTH_WRITE != 0) OptionalBool.True else OptionalBool.False,
        depthCompare =
            when {
                flags and DrawFlags.DEPTH_TEST == 0 -> CompareFunction.Always
                decal -> CompareFunction.LessEqual
                else -> CompareFunction.Less
            },
        // Match Shipwright's offset for paths and shadows on sloping ground.
        depthBias = if (decal) -2 else 0,
        depthBiasSlopeScale = if (decal) -2f else 0f,
    )
}
