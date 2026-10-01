package com.tap.zoot.graphics.upscaler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class UpscalerRegistryTest {
    @Test
    fun ordersContributionsAndFallsBackToTheDefault() {
        val nearest = FakeUpscaler(UpscalerId.Nearest, 0)
        val default = FakeUpscaler(UpscalerId.Sgsr1, 2)
        val bilinear = FakeUpscaler(UpscalerId.Bilinear, 1)
        val registry = UpscalerRegistry(setOf(default, nearest, bilinear))

        assertEquals(listOf(UpscalerId.Nearest, UpscalerId.Bilinear, UpscalerId.Sgsr1), registry.options.map { it.id })
        assertSame(default, registry[UpscalerId("unknown")])
        assertEquals(UpscalerId.Sgsr1, registry.resolve(UpscalerId("unknown")))
        assertEquals(UpscalerId.Bilinear, registry.resolve(UpscalerId.Bilinear))
    }

    @Test
    fun rejectsDuplicateIds() {
        assertFailsWith<IllegalArgumentException> {
            UpscalerRegistry(setOf(FakeUpscaler(UpscalerId.Sgsr1, 0), FakeUpscaler(UpscalerId.Sgsr1, 1)))
        }
    }

    @Test
    fun requiresTheDefaultUpscaler() {
        assertFailsWith<IllegalArgumentException> {
            UpscalerRegistry(setOf(FakeUpscaler(UpscalerId.Nearest, 0)))
        }
    }

    private class FakeUpscaler(
        override val id: UpscalerId,
        override val order: Int,
    ) : Upscaler {
        override val name = id.value
        override val shaderSource = ""
    }
}
