package com.tap.zoot.runtime.resources

/** Decodes raw DEFLATE streams embedded in O2R archives. */
interface RawDeflateDecoder : AutoCloseable {
    fun decode(
        source: ByteArray,
        offset: Int,
        size: Int,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    )
}
