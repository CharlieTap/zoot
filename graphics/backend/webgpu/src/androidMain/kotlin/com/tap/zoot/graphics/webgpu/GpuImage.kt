package com.tap.zoot.graphics.webgpu

import androidx.webgpu.GPUColor
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPURenderPassColorAttachment
import androidx.webgpu.GPURenderPassDepthStencilAttachment
import androidx.webgpu.GPURenderPassDescriptor
import androidx.webgpu.GPUTexture
import androidx.webgpu.GPUTextureDescriptor
import androidx.webgpu.GPUTextureView
import androidx.webgpu.LoadOp
import androidx.webgpu.StoreOp
import androidx.webgpu.TextureFormat
import androidx.webgpu.TextureUsage

internal class GpuImage(
    val texture: GPUTexture,
    val view: GPUTextureView,
    val width: Int,
    val height: Int,
) : AutoCloseable {
    fun hasSize(
        width: Int,
        height: Int,
    ): Boolean = this.width == width && this.height == height

    override fun close() {
        view.close()
        texture.close()
    }
}

internal class RenderTarget(
    val color: GpuImage,
    val depth: GpuImage,
) : AutoCloseable {
    private val colourAttachment =
        GPURenderPassColorAttachment(clearValue = GPUColor(0.0, 0.0, 0.0, 1.0), view = color.view, storeOp = StoreOp.Store)
    private val depthAttachment = GPURenderPassDepthStencilAttachment(view = depth.view, depthStoreOp = StoreOp.Store, depthClearValue = 1f)
    private val passDescriptor =
        GPURenderPassDescriptor(colorAttachments = arrayOf(colourAttachment), depthStencilAttachment = depthAttachment)

    /** This target's reused pass descriptor, loading or clearing each attachment. */
    fun pass(clears: PendingClears.Clears): GPURenderPassDescriptor {
        colourAttachment.loadOp = if (clears.colour) LoadOp.Clear else LoadOp.Load
        depthAttachment.depthLoadOp = if (clears.depth) LoadOp.Clear else LoadOp.Load
        return passDescriptor
    }

    override fun close() {
        color.close()
        depth.close()
    }
}

internal fun GPUDevice.createImage(
    width: Int,
    height: Int,
    format: Int = TextureFormat.RGBA8Unorm,
): GpuImage {
    val texture =
        createTexture(
            GPUTextureDescriptor(
                usage = TextureUsage.TextureBinding or TextureUsage.CopyDst or TextureUsage.CopySrc or TextureUsage.RenderAttachment,
                size = GPUExtent3D(width, height),
                format = format,
            ),
        )
    return GpuImage(texture, texture.createView(), width, height)
}

internal fun GPUDevice.createTarget(
    width: Int,
    height: Int,
) = RenderTarget(createImage(width, height), createImage(width, height, TextureFormat.Depth32Float))
