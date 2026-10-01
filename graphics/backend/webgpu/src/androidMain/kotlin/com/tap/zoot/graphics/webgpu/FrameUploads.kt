package com.tap.zoot.graphics.webgpu

import androidx.webgpu.BufferUsage
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUQueue
import com.tap.zoot.graphics.DrawState
import io.github.charlietap.chasm.host.HostMemory
import java.nio.ByteBuffer

internal class FrameUploads(
    device: GPUDevice,
) : AutoCloseable {
    val vertexBuffer =
        device.createBuffer(
            GPUBufferDescriptor(usage = BufferUsage.Vertex or BufferUsage.CopyDst, size = VERTEX_CAPACITY.toLong()),
        )
    val uniformBuffer =
        device.createBuffer(
            GPUBufferDescriptor(usage = BufferUsage.Uniform or BufferUsage.CopyDst, size = UNIFORM_CAPACITY.toLong()),
        )
    private val vertexStaging = ByteBuffer.allocateDirect(VERTEX_CAPACITY)
    private val uniformStaging = ByteBuffer.allocateDirect(UNIFORM_CAPACITY)
    private val uniformBytes = ByteArray(DrawState.UNIFORMS_SIZE)

    var vertexOffset = 0L
        private set
    var uniformOffset = 0L
        private set

    fun fits(vertexBytes: Int): Boolean =
        vertexOffset + vertexBytes <= VERTEX_CAPACITY && uniformOffset + DrawState.UNIFORMS_SIZE <= UNIFORM_CAPACITY

    fun stage(
        memory: HostMemory,
        drawState: Int,
        vertices: ByteArray,
        offset: Int,
        size: Int,
    ) {
        vertexStaging.clear().position(vertexOffset.toInt())
        vertexStaging.put(vertices, offset, size)
        uniformStaging.clear().position(uniformOffset.toInt())
        memory.read(uniformBytes, drawState + DrawState.UNIFORMS, uniformBytes.size)
        uniformStaging.put(uniformBytes)
    }

    fun advance(vertexBytes: Int) {
        vertexOffset += vertexBytes
        uniformOffset += UNIFORM_STRIDE
    }

    // Upload once per submission, not twice for every draw.
    fun flush(queue: GPUQueue) {
        if (vertexOffset == 0L) return
        // Dawn's JNI binding uses capacity, not limit. Slice without copying the bytes.
        vertexStaging.clear().limit(vertexOffset.toInt())
        queue.writeBuffer(vertexBuffer, 0, vertexStaging.slice())
        uniformStaging.clear().limit(uniformOffset.toInt())
        queue.writeBuffer(uniformBuffer, 0, uniformStaging.slice())
    }

    fun reset() {
        vertexOffset = 0
        uniformOffset = 0
    }

    override fun close() {
        vertexBuffer.close()
        uniformBuffer.close()
    }

    private companion object {
        const val VERTEX_CAPACITY = 16 * 1024 * 1024
        const val UNIFORM_CAPACITY = 4 * 1024 * 1024
        const val UNIFORM_STRIDE = 256
    }
}
