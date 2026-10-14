package com.tap.zoot.graphics.webgpu

import androidx.webgpu.BufferUsage
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUQueue
import java.nio.ByteBuffer

/**
 * Per-frame vertex storage. Guest vertices are copied once into direct memory and uploaded with one
 * `writeBuffer` per submission; draws address them by first vertex, so the buffer is bound once per pass.
 */
internal class VertexUploads(
    device: GPUDevice,
) : AutoCloseable {
    val buffer =
        device.createBuffer(GPUBufferDescriptor(usage = BufferUsage.Vertex or BufferUsage.CopyDst, size = CAPACITY.toLong()))
    private val staging = ByteBuffer.allocateDirect(CAPACITY)
    private var flushed = 0
    private var offset = 0

    /** Copies [size] bytes and returns the first vertex of the copy, aligning to [stride]. */
    fun stage(
        source: ByteArray,
        at: Int,
        size: Int,
        stride: Int,
    ): Int {
        val start = (offset + stride - 1) / stride * stride
        check(start + size <= CAPACITY) { "Frame vertex capacity exceeded" }
        staging.clear().position(start)
        staging.put(source, at, size)
        offset = start + size
        return start / stride
    }

    /** Uploads everything staged since the last flush, ahead of a submission. */
    fun flush(queue: GPUQueue) {
        if (offset == flushed) return
        // Dawn's JNI binding uses capacity, not limit. Slice without copying the bytes.
        staging.clear().position(flushed).limit(offset)
        queue.writeBuffer(buffer, flushed.toLong(), staging.slice())
        flushed = offset
    }

    fun reset() {
        offset = 0
        flushed = 0
    }

    override fun close() {
        buffer.close()
    }

    private companion object {
        const val CAPACITY = 16 * 1024 * 1024
    }
}
