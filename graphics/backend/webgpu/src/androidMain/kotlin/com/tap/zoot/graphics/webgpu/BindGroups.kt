package com.tap.zoot.graphics.webgpu

import androidx.webgpu.GPUBindGroup
import androidx.webgpu.GPUBindGroupDescriptor
import androidx.webgpu.GPUBindGroupEntry
import androidx.webgpu.GPUBindGroupLayoutDescriptor
import androidx.webgpu.GPUBindGroupLayoutEntry
import androidx.webgpu.GPUBuffer
import androidx.webgpu.GPUBufferBindingLayout
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUSamplerBindingLayout
import androidx.webgpu.GPUTextureBindingLayout
import androidx.webgpu.ShaderStage
import com.tap.zoot.graphics.DrawState
import io.github.charlietap.chasm.host.HostMemory

internal class BindGroups(
    private val device: GPUDevice,
    private val samplers: Samplers,
    private val uniforms: GPUBuffer,
) : AutoCloseable {
    val layout =
        device.createBindGroupLayout(
            GPUBindGroupLayoutDescriptor(
                entries =
                    buildList {
                        add(
                            GPUBindGroupLayoutEntry(
                                UNIFORM_BINDING,
                                ShaderStage.Fragment,
                                buffer = GPUBufferBindingLayout(hasDynamicOffset = true, minBindingSize = DrawState.UNIFORMS_SIZE.toLong()),
                            ),
                        )
                        repeat(DrawState.TEXTURE_SLOTS) { slot ->
                            add(GPUBindGroupLayoutEntry(textureBinding(slot), ShaderStage.Fragment, texture = GPUTextureBindingLayout()))
                            add(GPUBindGroupLayoutEntry(samplerBinding(slot), ShaderStage.Fragment, sampler = GPUSamplerBindingLayout()))
                        }
                    }.toTypedArray(),
            ),
        )
    private val groups = HashMap<BindingKey, GPUBindGroup>()
    private val probeValues = IntArray(DrawState.TEXTURE_SLOTS * 2)
    private val probe = BindingKey(probeValues)

    fun find(
        memory: HostMemory,
        drawState: Int,
    ): GPUBindGroup? {
        for (index in probeValues.indices) probeValues[index] = memory.readI32(drawState + DrawState.TEXTURES + index * Int.SIZE_BYTES)
        return groups[probe]
    }

    fun createForProbe(image: (textureId: Int) -> GpuImage): GPUBindGroup {
        val key = BindingKey(probeValues.copyOf())
        val entries = ArrayList<GPUBindGroupEntry>(1 + DrawState.TEXTURE_SLOTS * 2)
        entries += GPUBindGroupEntry(UNIFORM_BINDING, buffer = uniforms, size = DrawState.UNIFORMS_SIZE.toLong())
        repeat(DrawState.TEXTURE_SLOTS) { slot ->
            entries += GPUBindGroupEntry(textureBinding(slot), textureView = image(key.textureId(slot)).view)
            entries += GPUBindGroupEntry(samplerBinding(slot), sampler = samplers[key.samplerWrap(slot)])
        }
        return device.createBindGroup(GPUBindGroupDescriptor(layout = layout, entries = entries.toTypedArray())).also { groups[key] = it }
    }

    fun clear() {
        groups.values.forEach(GPUBindGroup::close)
        groups.clear()
    }

    override fun close() {
        clear()
        layout.close()
    }

    private class BindingKey(
        private val values: IntArray,
    ) {
        fun textureId(slot: Int): Int = values[slot]

        fun samplerWrap(slot: Int): Int = values[DrawState.TEXTURE_SLOTS + slot]

        override fun hashCode() = values.contentHashCode()

        override fun equals(other: Any?) = other is BindingKey && values.contentEquals(other.values)
    }

    private companion object {
        const val UNIFORM_BINDING = 0

        fun textureBinding(slot: Int) = 1 + slot * 2

        fun samplerBinding(slot: Int) = 2 + slot * 2
    }
}
