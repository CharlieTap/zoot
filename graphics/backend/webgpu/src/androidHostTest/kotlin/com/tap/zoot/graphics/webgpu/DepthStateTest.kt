package com.tap.zoot.graphics.webgpu

import androidx.webgpu.CompareFunction
import androidx.webgpu.OptionalBool
import androidx.webgpu.TextureFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthStateTest {
    @Test
    fun depthFlagsMatchShipwright() {
        for (flags in 0..15) {
            val state = depthState(flags)
            val compare =
                when {
                    flags and 1 == 0 -> CompareFunction.Always
                    flags and 4 == 0 -> CompareFunction.Less
                    else -> CompareFunction.LessEqual
                }
            assertEquals(TextureFormat.Depth32Float, state.format)
            assertEquals(compare, state.depthCompare)
            assertEquals(if (flags and 2 == 0) OptionalBool.False else OptionalBool.True, state.depthWriteEnabled)
            assertEquals(if (flags and 4 == 0) 0 else -2, state.depthBias)
            assertEquals(if (flags and 4 == 0) 0f else -2f, state.depthBiasSlopeScale, 0f)
        }
    }
}
