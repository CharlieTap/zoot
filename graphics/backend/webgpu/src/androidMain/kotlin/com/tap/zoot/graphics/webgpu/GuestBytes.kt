package com.tap.zoot.graphics.webgpu

import io.github.charlietap.chasm.host.ByteArrayHostMemory
import io.github.charlietap.chasm.host.HostMemory
import io.github.charlietap.chasm.host.UnsafeHostApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A view of guest memory for the duration of one host call. Android's Chasm memory is a heap array,
 * which is borrowed rather than copied; any other memory is copied into a reusable array.
 */
internal class GuestBytes {
    private var scratch = ByteArray(0)
    private var viewed = scratch
    private var view: ByteBuffer = ByteBuffer.wrap(scratch).order(ByteOrder.LITTLE_ENDIAN)

    var bytes: ByteArray = scratch
        private set
    var offset = 0
        private set

    @OptIn(UnsafeHostApi::class)
    fun load(
        memory: HostMemory,
        pointer: Int,
        size: Int,
    ): GuestBytes {
        if (memory is ByteArrayHostMemory) {
            require(pointer >= 0 && size >= 0 && pointer.toLong() + size <= memory.byteSize) { "Guest range out of bounds" }
            bytes = memory.unsafeBorrowByteArray()
            offset = pointer
        } else {
            if (scratch.size < size) scratch = ByteArray(maxOf(size, scratch.size * 2))
            memory.read(scratch, pointer, size)
            bytes = scratch
            offset = 0
        }
        return this
    }

    fun wrap(source: ByteArray): GuestBytes {
        bytes = source
        offset = 0
        return this
    }

    fun int(at: Int): Int {
        if (viewed !== bytes) {
            viewed = bytes
            view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        }
        return view.getInt(offset + at)
    }
}
