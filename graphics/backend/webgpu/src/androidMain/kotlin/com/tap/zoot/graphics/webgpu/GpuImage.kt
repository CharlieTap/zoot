package com.tap.zoot.graphics.webgpu

import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPUTexture
import androidx.webgpu.GPUTextureDescriptor
import androidx.webgpu.GPUTextureView
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
