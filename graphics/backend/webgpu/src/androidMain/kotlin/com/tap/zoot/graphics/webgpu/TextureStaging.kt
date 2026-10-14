package com.tap.zoot.graphics.webgpu

import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPUQueue
import androidx.webgpu.GPUTexelCopyBufferLayout
import androidx.webgpu.GPUTexelCopyTextureInfo
import androidx.webgpu.GPUTexture
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Copies guest texels once into direct memory and uploads them with reused copy descriptors. */
internal class TextureStaging {
    private var buffer = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.LITTLE_ENDIAN)
    private var destination: GPUTexelCopyTextureInfo? = null
    private val extent = GPUExtent3D(1, 1)
    private val layout = GPUTexelCopyBufferLayout()

    fun upload(
        queue: GPUQueue,
        texture: GPUTexture,
        source: GuestBytes,
        width: Int,
        height: Int,
    ) {
        val length = width * height * 4
        if (buffer.capacity() < length) {
            buffer = ByteBuffer.allocateDirect(Integer.highestOneBit(length - 1) shl 1).order(ByteOrder.LITTLE_ENDIAN)
        }
        buffer.clear()
        buffer.put(source.bytes, source.offset, length)
        buffer.flip()
        val destination = destination?.apply { this.texture = texture } ?: GPUTexelCopyTextureInfo(texture).also { destination = it }
        extent.width = width
        extent.height = height
        layout.bytesPerRow = width * 4
        layout.rowsPerImage = height
        queue.writeTexture(destination, buffer, extent, layout)
    }

    private companion object {
        const val INITIAL_CAPACITY = 256 * 1024
    }
}
