package com.tap.zoot.graphics.webgpu

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class TextureStaging {
    private var buffer = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.LITTLE_ENDIAN)

    fun stage(
        pixels: ByteArray,
        offset: Int,
        length: Int,
    ): ByteBuffer {
        if (buffer.capacity() < length) {
            buffer = ByteBuffer.allocateDirect(Integer.highestOneBit(length - 1) shl 1).order(ByteOrder.LITTLE_ENDIAN)
        }
        buffer.clear()
        buffer.put(pixels, offset, length)
        buffer.flip()
        return buffer
    }

    private companion object {
        const val INITIAL_CAPACITY = 256 * 1024
    }
}
