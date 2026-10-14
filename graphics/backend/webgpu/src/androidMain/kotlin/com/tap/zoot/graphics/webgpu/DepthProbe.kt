package com.tap.zoot.graphics.webgpu

import androidx.webgpu.BufferUsage
import androidx.webgpu.GPUBindGroup
import androidx.webgpu.GPUBindGroupDescriptor
import androidx.webgpu.GPUBindGroupEntry
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUCommandEncoder
import androidx.webgpu.GPUComputePassDescriptor
import androidx.webgpu.GPUComputePipelineDescriptor
import androidx.webgpu.GPUComputeState
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUShaderModuleDescriptor
import androidx.webgpu.GPUShaderSourceWGSL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

/**
 * Reads single depth texels. WebGPU only copies whole depth subresources, so a compute pass loads
 * the requested texels into a small storage buffer instead, with the coordinates as immediates.
 */
internal class DepthProbe(
    private val device: GPUDevice,
) : AutoCloseable {
    private val module = device.createShaderModule(GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(SHADER)))
    private val pipeline = device.createComputePipeline(GPUComputePipelineDescriptor(compute = GPUComputeState(module, "main")))
    private val layout = pipeline.getBindGroupLayout(0)
    private val output =
        device.createBuffer(GPUBufferDescriptor(usage = BufferUsage.Storage or BufferUsage.CopySrc, size = BATCH * 4L))
    val results =
        device.createBuffer(GPUBufferDescriptor(usage = BufferUsage.MapRead or BufferUsage.CopyDst, size = BATCH * 4L))
    private val immediates = ByteBuffer.allocateDirect(IMMEDIATE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private val groups = IdentityHashMap<GpuImage, GPUBindGroup>()
    private val passDescriptor = GPUComputePassDescriptor()
    private val columns = IntArray(BATCH)
    private val rows = IntArray(BATCH)

    fun point(
        index: Int,
        column: Int,
        row: Int,
    ) {
        columns[index] = column
        rows[index] = row
    }

    /** Encodes reads of the first [count] points, up to [BATCH]; results land in [results] as f32s. */
    fun encode(
        encoder: GPUCommandEncoder,
        depth: GpuImage,
        count: Int,
    ) {
        val group =
            groups.getOrPut(depth) {
                device.createBindGroup(
                    GPUBindGroupDescriptor(
                        layout = layout,
                        entries = arrayOf(GPUBindGroupEntry(0, textureView = depth.view), GPUBindGroupEntry(1, buffer = output)),
                    ),
                )
            }
        encoder.beginComputePass(passDescriptor).use { pass ->
            pass.setPipeline(pipeline)
            pass.setBindGroup(0, group, NO_OFFSETS)
            var base = 0
            while (base < count) {
                val size = minOf(PER_DISPATCH, count - base)
                immediates.clear()
                immediates
                    .putInt(size)
                    .putInt(base)
                    .putInt(0)
                    .putInt(0)
                for (index in 0 until PER_DISPATCH) {
                    val point = base + minOf(index, size - 1)
                    immediates.putInt(columns[point]).putInt(rows[point])
                }
                immediates.flip()
                pass.setImmediates(0, immediates)
                pass.dispatchWorkgroups(1)
                base += size
            }
            pass.end()
        }
        encoder.copyBufferToBuffer(output, 0, results, 0, count * 4L)
    }

    fun forget(depth: GpuImage) {
        groups.remove(depth)?.close()
    }

    override fun close() {
        groups.values.forEach(GPUBindGroup::close)
        groups.clear()
        output.close()
        results.close()
        layout.close()
        pipeline.close()
        module.close()
    }

    companion object {
        const val BATCH = 64
        private const val PER_DISPATCH = 6
        private const val IMMEDIATE_SIZE = 64

        private const val SHADER = """
struct Probe { header: vec4u, first: vec4u, second: vec4u, third: vec4u }
var<immediate> probe: Probe;
@group(0) @binding(0) var depth: texture_depth_2d;
@group(0) @binding(1) var<storage, read_write> depths: array<f32, 64>;
@compute @workgroup_size(8) fn main(@builtin(local_invocation_index) index: u32) {
    if (index < probe.header.x) {
        var pair = probe.third;
        if (index < 2u) { pair = probe.first; } else if (index < 4u) { pair = probe.second; }
        let point = select(pair.xy, pair.zw, (index & 1u) == 1u);
        depths[probe.header.y + index] = textureLoad(depth, point, 0);
    }
}
"""
    }
}
