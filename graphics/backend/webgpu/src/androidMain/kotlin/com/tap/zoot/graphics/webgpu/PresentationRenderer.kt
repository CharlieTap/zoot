package com.tap.zoot.graphics.webgpu

import androidx.webgpu.AddressMode
import androidx.webgpu.FilterMode
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
import androidx.webgpu.GPUSamplerDescriptor
import androidx.webgpu.GPUShaderModuleDescriptor
import androidx.webgpu.GPUShaderSourceWGSL
import androidx.webgpu.GPUTextureView
import androidx.webgpu.GPUVertexState
import androidx.webgpu.LoadOp
import androidx.webgpu.StoreOp
import com.tap.zoot.graphics.upscaler.TextureFilter
import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.graphics.upscaler.presentationShader

/** Samples the game's final texture directly into the surface, without an intermediate target. */
internal class PresentationRenderer(
    private val device: GPUDevice,
    format: Int,
    upscaler: Upscaler,
) : AutoCloseable {
    private val shader =
        device.createShaderModule(
            GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(upscaler.presentationShader())),
        )
    private val pipeline =
        device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                label = upscaler.name,
                vertex = GPUVertexState(shader, "vertexMain"),
                fragment = GPUFragmentState(shader, "fragmentMain", targets = arrayOf(GPUColorTargetState(format))),
            ),
        )
    private val layout = pipeline.getBindGroupLayout(0)
    private val sampler =
        device.createSampler(
            GPUSamplerDescriptor(
                addressModeU = AddressMode.ClampToEdge,
                addressModeV = AddressMode.ClampToEdge,
                minFilter = if (upscaler.filter == TextureFilter.Nearest) FilterMode.Nearest else FilterMode.Linear,
                magFilter = if (upscaler.filter == TextureFilter.Nearest) FilterMode.Nearest else FilterMode.Linear,
            ),
        )
    private var source: GPUTextureView? = null
    private var binding: GPUBindGroup? = null
    private val attachment =
        GPURenderPassColorAttachment(
            clearValue = GPUColor(0.0, 0.0, 0.0, 1.0),
            loadOp = LoadOp.Clear,
            storeOp = StoreOp.Store,
        )
    private val passDescriptor = GPURenderPassDescriptor(colorAttachments = arrayOf(attachment))

    fun draw(
        encoder: GPUCommandEncoder,
        image: GPUTextureView,
        destination: GPUTextureView,
    ) {
        if (source !== image) {
            binding?.close()
            binding =
                device.createBindGroup(
                    GPUBindGroupDescriptor(
                        layout = layout,
                        entries = arrayOf(GPUBindGroupEntry(0, textureView = image), GPUBindGroupEntry(1, sampler = sampler)),
                    ),
                )
            source = image
        }
        attachment.view = destination
        encoder.beginRenderPass(passDescriptor).use { pass ->
            pass.setPipeline(pipeline)
            pass.setBindGroup(0, checkNotNull(binding))
            pass.draw(3)
            pass.end()
        }
    }

    override fun close() {
        binding?.close()
        sampler.close()
        layout.close()
        pipeline.close()
        shader.close()
    }
}
