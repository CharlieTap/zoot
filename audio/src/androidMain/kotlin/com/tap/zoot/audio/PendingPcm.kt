package com.tap.zoot.audio

// The input buffer is reused after submit returns, so retain any unwritten bytes.
internal class PendingPcm {
    var bytes = ByteArray(0)
        private set
    var offset = 0
        private set
    var size = 0
        private set

    val isEmpty: Boolean
        get() = size == 0

    fun consume(count: Int) {
        offset += count
        size -= count
        if (size == 0) offset = 0
    }

    fun append(
        source: ByteArray,
        sourceOffset: Int,
        length: Int,
    ) {
        if (offset + size + length > bytes.size) {
            if (size + length > bytes.size) {
                val grown = ByteArray(maxOf(size + length, bytes.size * 2))
                bytes.copyInto(grown, 0, offset, offset + size)
                bytes = grown
            } else {
                bytes.copyInto(bytes, 0, offset, offset + size)
            }
            offset = 0
        }
        source.copyInto(bytes, offset + size, sourceOffset, sourceOffset + length)
        size += length
    }

    fun clear() {
        offset = 0
        size = 0
    }

    fun release() {
        bytes = ByteArray(0)
        clear()
    }
}
