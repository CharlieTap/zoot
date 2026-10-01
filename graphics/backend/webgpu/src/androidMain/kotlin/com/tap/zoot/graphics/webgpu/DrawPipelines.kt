package com.tap.zoot.graphics.webgpu

import androidx.webgpu.BlendFactor
import androidx.webgpu.CompareFunction
import androidx.webgpu.GPUBindGroupLayout
import androidx.webgpu.GPUBlendComponent
import androidx.webgpu.GPUBlendState
import androidx.webgpu.GPUColorTargetState
import androidx.webgpu.GPUDepthStencilState
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUFragmentState
import androidx.webgpu.GPUPipelineLayoutDescriptor
import androidx.webgpu.GPUPrimitiveState
import androidx.webgpu.GPURenderPipeline
import androidx.webgpu.GPURenderPipelineDescriptor
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

internal class DrawPipelines(
    private val device: GPUDevice,
    bindGroupLayout: GPUBindGroupLayout,
) : AutoCloseable {
    private val layout = device.createPipelineLayout(GPUPipelineLayoutDescriptor(bindGroupLayouts = arrayOf(bindGroupLayout)))
    private val shaders = HashMap<Int, DrawShader>()
    private val pipelines = HashMap<Long, GPURenderPipeline>()

    fun defineShader(
        shaderId: Int,
        wgsl: String,
        vertexStride: Int,
        attributeSizes: IntArray,
    ) {
        var offset = 0L
        val attributes =
            attributeSizes
                .mapIndexed { index, components ->
                    GPUVertexAttribute(
                        format = vertexFormat(components),
                        offset = offset,
                        shaderLocation = index,
                    ).also { offset += components * Float.SIZE_BYTES }
                }.toTypedArray()
        shaders[shaderId] =
            DrawShader(
                device.createShaderModule(GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(wgsl))),
                GPUVertexBufferLayout(arrayStride = vertexStride.toLong(), attributes = attributes),
            )
    }

    operator fun get(
        shaderId: Int,
        flags: Int,
    ): GPURenderPipeline = pipelines.getOrPut((shaderId.toLong() shl 32) or flags.toLong()) { create(shaders.getValue(shaderId), flags) }

    private fun create(
        shader: DrawShader,
        flags: Int,
    ): GPURenderPipeline =
        device.createRenderPipeline(
            GPURenderPipelineDescriptor(
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
            ),
        )

    override fun close() {
        pipelines.values.forEach(GPURenderPipeline::close)
        shaders.values.forEach { it.module.close() }
        layout.close()
    }

    private class DrawShader(
        val module: GPUShaderModule,
        val vertices: GPUVertexBufferLayout,
    )
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
