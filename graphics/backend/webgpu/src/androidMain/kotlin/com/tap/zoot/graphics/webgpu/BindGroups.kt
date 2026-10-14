package com.tap.zoot.graphics.webgpu

import androidx.webgpu.GPUBindGroup
import androidx.webgpu.GPUBindGroupDescriptor
import androidx.webgpu.GPUBindGroupEntry
import androidx.webgpu.GPUBindGroupLayoutDescriptor
import androidx.webgpu.GPUBindGroupLayoutEntry
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUSamplerBindingLayout
import androidx.webgpu.GPUTextureBindingLayout
import androidx.webgpu.ShaderStage
import com.tap.zoot.graphics.DrawState

/** Texture and sampler groups keyed by the guest's six texture ids and wrap modes. Draw parameters are immediates. */
internal class BindGroups(
    private val device: GPUDevice,
    private val samplers: Samplers,
    private val counters: FrameCounters,
) : AutoCloseable {
    val layout =
        device.createBindGroupLayout(
            GPUBindGroupLayoutDescriptor(
                entries =
                    buildList {
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

    fun find(state: GuestBytes): GPUBindGroup? {
        for (index in probeValues.indices) probeValues[index] = state.int(DrawState.TEXTURES + index * Int.SIZE_BYTES)
        return groups[probe]
    }

    fun probedTexture(slot: Int): Int = probeValues[slot]

    fun createForProbe(image: (textureId: Int) -> GpuImage): GPUBindGroup {
        val key = BindingKey(probeValues.copyOf())
        val entries =
            Array(DrawState.TEXTURE_SLOTS * 2) { index ->
                val slot = index / 2
                if (index % 2 == 0) {
                    GPUBindGroupEntry(textureBinding(slot), textureView = image(key.textureId(slot)).view)
                } else {
                    GPUBindGroupEntry(samplerBinding(slot), sampler = samplers[key.samplerWrap(slot)])
                }
            }
        counters.bindGroupsCreated++
        return device.createBindGroup(GPUBindGroupDescriptor(layout = layout, entries = entries)).also { groups[key] = it }
    }

    /** Drops groups that use [textureId], including groups that fell back to white while it was missing. */
    fun evictTexture(textureId: Int) = evict(textureId)

    fun evictTarget(targetId: Int) = evict(targetReference(targetId))

    private fun evict(reference: Int) {
        val iterator = groups.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.references(reference)) {
                entry.value.close()
                iterator.remove()
            }
        }
    }

    override fun close() {
        groups.values.forEach(GPUBindGroup::close)
        groups.clear()
        layout.close()
    }

    private class BindingKey(
        private val values: IntArray,
    ) {
        fun textureId(slot: Int): Int = values[slot]

        fun samplerWrap(slot: Int): Int = values[DrawState.TEXTURE_SLOTS + slot]

        fun references(textureId: Int): Boolean {
            for (slot in 0 until DrawState.TEXTURE_SLOTS) if (values[slot] == textureId) return true
            return false
        }

        override fun hashCode() = values.contentHashCode()

        override fun equals(other: Any?) = other is BindingKey && values.contentEquals(other.values)
    }

    companion object {
        fun targetReference(targetId: Int) = targetId or Int.MIN_VALUE

        private fun textureBinding(slot: Int) = 1 + slot * 2

        private fun samplerBinding(slot: Int) = 2 + slot * 2
    }
}
