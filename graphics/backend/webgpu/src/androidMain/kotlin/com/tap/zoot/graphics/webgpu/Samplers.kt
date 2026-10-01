package com.tap.zoot.graphics.webgpu

import androidx.webgpu.AddressMode
import androidx.webgpu.FilterMode
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUSampler
import androidx.webgpu.GPUSamplerDescriptor
import com.tap.zoot.graphics.SamplerWrap

internal class Samplers(
    private val device: GPUDevice,
) : AutoCloseable {
    private val cache = HashMap<Int, GPUSampler>()

    operator fun get(wrap: Int): GPUSampler =
        cache.getOrPut(wrap) {
            device.createSampler(
                GPUSamplerDescriptor(
                    addressModeU = addressMode(wrap),
                    addressModeV = addressMode(wrap shr SamplerWrap.AXIS_BITS),
                    magFilter = FilterMode.Nearest,
                    minFilter = FilterMode.Nearest,
                ),
            )
        }

    private fun addressMode(wrap: Int): Int =
        when {
            wrap and SamplerWrap.CLAMP != 0 -> AddressMode.ClampToEdge
            wrap and SamplerWrap.MIRROR != 0 -> AddressMode.MirrorRepeat
            else -> AddressMode.Repeat
        }

    override fun close() {
        cache.values.forEach(GPUSampler::close)
        cache.clear()
    }

    companion object {
        const val CLAMPED = SamplerWrap.CLAMP or (SamplerWrap.CLAMP shl SamplerWrap.AXIS_BITS)
    }
}
