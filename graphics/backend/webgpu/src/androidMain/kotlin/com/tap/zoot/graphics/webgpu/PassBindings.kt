package com.tap.zoot.graphics.webgpu

import androidx.webgpu.Constants
import androidx.webgpu.GPUBindGroup
import androidx.webgpu.GPUBuffer
import androidx.webgpu.GPURenderPassEncoder
import androidx.webgpu.GPURenderPipeline
import com.tap.zoot.graphics.DrawState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What the open render pass has bound, so state a draw repeats costs no JNI call. */
internal class PassBindings {
    private var pipeline: GPURenderPipeline? = null
    private var group: GPUBindGroup? = null
    private var vertices: GPUBuffer? = null
    private val params = BoundInts()
    private val viewport = BoundInts()
    private val scissor = BoundInts()
    private val paramBytes = ByteBuffer.allocateDirect(DrawState.UNIFORMS_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    fun reset() {
        pipeline = null
        group = null
        vertices = null
        params.invalidate()
        viewport.invalidate()
        scissor.invalidate()
    }

    fun pipeline(
        pass: GPURenderPassEncoder,
        pipeline: GPURenderPipeline,
    ) {
        if (pipeline === this.pipeline) return
        pass.setPipeline(pipeline)
        this.pipeline = pipeline
    }

    fun bindGroup(
        pass: GPURenderPassEncoder,
        group: GPUBindGroup,
    ) {
        if (group === this.group) return
        pass.setBindGroup(0, group, NO_OFFSETS)
        this.group = group
    }

    fun vertices(
        pass: GPURenderPassEncoder,
        buffer: GPUBuffer,
    ) {
        if (buffer === vertices) return
        pass.setVertexBuffer(0, buffer, 0, Constants.WHOLE_SIZE)
        vertices = buffer
    }

    /** The draw's parameters (depth, frame, noise scale, filtering) go to the shader as immediates. */
    fun params(
        pass: GPURenderPassEncoder,
        state: GuestBytes,
    ) {
        val offset = DrawState.UNIFORMS
        if (!params.update(state.int(offset), state.int(offset + 4), state.int(offset + 8), state.int(offset + 12))) return
        paramBytes.clear()
        params.writeTo(paramBytes)
        paramBytes.flip()
        pass.setImmediates(0, paramBytes)
    }

    /** The guest's viewport has a bottom-left origin; WebGPU's is top-left. */
    fun viewport(
        pass: GPURenderPassEncoder,
        state: GuestBytes,
        targetHeight: Int,
    ) {
        val x = state.int(DrawState.VIEWPORT_X)
        val y = state.int(DrawState.VIEWPORT_Y)
        val width = state.int(DrawState.VIEWPORT_WIDTH)
        val height = state.int(DrawState.VIEWPORT_HEIGHT)
        if (!viewport.update(x, y, width, height)) return
        pass.setViewport(x.toFloat(), (targetHeight - y - height).toFloat(), width.toFloat(), height.toFloat(), 0f, 1f)
    }

    fun scissor(
        pass: GPURenderPassEncoder,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (scissor.update(x, y, width, height)) pass.setScissorRect(x, y, width, height)
    }

    private class BoundInts {
        private val values = IntArray(4)
        private var valid = false

        fun invalidate() {
            valid = false
        }

        /** Stores the four values, returning true if they differ from what is bound. */
        fun update(
            first: Int,
            second: Int,
            third: Int,
            fourth: Int,
        ): Boolean {
            if (valid && values[0] == first && values[1] == second && values[2] == third && values[3] == fourth) return false
            values[0] = first
            values[1] = second
            values[2] = third
            values[3] = fourth
            valid = true
            return true
        }

        fun writeTo(buffer: ByteBuffer) {
            for (value in values) buffer.putInt(value)
        }
    }
}

// setBindGroup's default argument allocates a new empty array per call.
internal val NO_OFFSETS = IntArray(0)
