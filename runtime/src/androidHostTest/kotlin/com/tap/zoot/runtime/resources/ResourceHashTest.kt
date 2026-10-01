package com.tap.zoot.runtime.resources

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceHashTest {
    @Test
    fun crcUsesTheShipwrightInitialValueWithoutFinalXor() {
        assertEquals(0x9D13A61C0E5B0FF5uL.toLong(), resourceId("123456789"))
    }

    @Test
    fun otrNamesAndArchivePathsHaveTheSameId() {
        assertEquals(resourceId("textures/example"), resourceId("__OTR__textures/example"))
    }
}
