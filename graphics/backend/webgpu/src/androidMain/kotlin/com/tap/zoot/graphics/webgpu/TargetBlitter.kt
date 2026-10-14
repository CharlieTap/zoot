package com.tap.zoot.graphics.webgpu

import androidx.webgpu.GPUBindGroup
import androidx.webgpu.GPUBindGroupDescriptor
import androidx.webgpu.GPUBindGroupEntry
import androidx.webgpu.GPUColor
import androidx.webgpu.GPUColorTargetState
import androidx.webgpu.GPUCommandEncoder
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUFragmentState
import androidx.webgpu.GPURenderPassColorAttachment
import androidx.webgpu.GPURenderPassDescriptor
import androidx.webgpu.GPURenderPipelineDescriptor
import androidx.webgpu.GPUShaderModuleDescriptor
import androidx.webgpu.GPUShaderSourceWGSL
import androidx.webgpu.GPUTextureView
import androidx.webgpu.GPUVertexState
import androidx.webgpu.LoadOp
import androidx.webgpu.StoreOp
import androidx.webgpu.TextureFormat
import java.util.IdentityHashMap

internal class TargetBlitter(
    private val device: GPUDevice,
    private val samplers: Samplers,
) : AutoCloseable {
    private val shader = device.createShaderModule(GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(BLIT_SHADER)))
    private val pipeline =
        device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                vertex = GPUVertexState(shader, "vertexMain"),
                fragment = GPUFragmentState(shader, "fragmentMain", targets = arrayOf(GPUColorTargetState(TextureFormat.RGBA8Unorm))),
            ),
        )
    private val layout = pipeline.getBindGroupLayout(0)
    private val groups = IdentityHashMap<GpuImage, GPUBindGroup>()
    private val attachment =
        GPURenderPassColorAttachment(
            clearValue = GPUColor(0.0, 0.0, 0.0, 1.0),
            loadOp = LoadOp.Clear,
            storeOp = StoreOp.Store,
        )
    private val passDescriptor = GPURenderPassDescriptor(colorAttachments = arrayOf(attachment))

    /** Draws [source] over all of [destination], an RGBA8 view, replacing its contents. */
    fun blit(
        encoder: GPUCommandEncoder,
        source: GpuImage,
        destination: GPUTextureView,
    ) {
        val group =
            groups.getOrPut(source) {
                device.createBindGroup(
                    GPUBindGroupDescriptor(
                        layout = layout,
                        entries =
                            arrayOf(
                                GPUBindGroupEntry(0, textureView = source.view),
                                GPUBindGroupEntry(1, sampler = samplers[Samplers.CLAMPED]),
                            ),
                    ),
                )
            }
        attachment.view = destination
        encoder.beginRenderPass(passDescriptor).use { pass ->
            pass.setPipeline(pipeline)
            pass.setBindGroup(0, group, NO_OFFSETS)
            pass.draw(3)
            pass.end()
        }
    }

    fun forget(source: GpuImage) {
        groups.remove(source)?.close()
    }

    override fun close() {
        groups.values.forEach(GPUBindGroup::close)
        layout.close()
        pipeline.close()
        shader.close()
    }
}

private const val BLIT_SHADER = """
@group(0) @binding(0) var image: texture_2d<f32>;
@group(0) @binding(1) var imageSampler: sampler;
struct Vout { @builtin(position) position: vec4f, @location(0) uv: vec2f };
@vertex fn vertexMain(@builtin(vertex_index) index: u32) -> Vout {
    let uv=vec2f(f32((index<<1u)&2u),f32(index&2u));
    return Vout(vec4f(uv*vec2f(2.0,-2.0)+vec2f(-1.0,1.0),0.0,1.0),uv);
}
@fragment fn fragmentMain(v: Vout) -> @location(0) vec4f {
    return textureSample(image,imageSampler,v.uv);
}
"""
