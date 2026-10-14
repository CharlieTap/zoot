package com.tap.zoot.graphics.webgpu

import androidx.webgpu.BufferUsage
import androidx.webgpu.GPUBuffer
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A reused map-read buffer and the direct memory reads are copied into, both grown only when a larger read arrives. */
internal class ReadbackBuffer(
    private val device: GPUDevice,
) : AutoCloseable {
    private var gpu: GPUBuffer? = null
    private var cpu: ByteBuffer = ByteBuffer.allocateDirect(0)

    fun buffer(size: Int): GPUBuffer {
        val current = gpu
        if (current != null && current.size >= size) return current
        current?.close()
        return device
            .createBuffer(GPUBufferDescriptor(usage = BufferUsage.MapRead or BufferUsage.CopyDst, size = size.toLong()))
            .also { gpu = it }
    }

    /** Copies the first [size] mapped bytes of [buffer] into reused direct memory. */
    fun read(
        buffer: GPUBuffer,
        size: Int,
    ): ByteBuffer {
        if (cpu.capacity() < size) cpu = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        cpu.clear().limit(size)
        // The JNI binding reads up to the capacity, so hand it an exact-size view.
        buffer.readMappedRange(0, cpu.slice())
        return cpu
    }

    override fun close() {
        gpu?.close()
    }
}
