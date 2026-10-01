package com.tap.zoot.runtime.host

import io.github.charlietap.chasm.host.HostMemory

/** Copies guest memory through one reusable buffer, starting at offset zero. */
internal class GuestMemoryTransfer {
    private var staging = ByteArray(0)

    private fun buffer(size: Int): ByteArray {
        if (staging.size < size) staging = ByteArray(maxOf(size, staging.size * 2))
        return staging
    }

    inline fun <T> read(
        memory: HostMemory,
        pointer: Int,
        size: Int,
        block: (ByteArray) -> T,
    ): T {
        val bytes = buffer(size)
        memory.read(bytes, pointer, size)
        return block(bytes)
    }

    inline fun write(
        memory: HostMemory,
        pointer: Int,
        size: Int,
        block: (ByteArray) -> Int,
    ): Int {
        val bytes = buffer(size)
        val written = block(bytes)
        if (written > 0) memory.write(pointer, bytes, 0, written)
        return written
    }
}
