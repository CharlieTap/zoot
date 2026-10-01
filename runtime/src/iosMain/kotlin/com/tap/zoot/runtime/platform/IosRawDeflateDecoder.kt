@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.runtime.platform

import com.tap.zoot.runtime.engine.GameSessionScope
import com.tap.zoot.runtime.resources.RawDeflateDecoder
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.inflateReset
import platform.zlib.z_stream
import platform.zlib.zlibVersion

@Inject
@ContributesBinding(GameSessionScope::class)
class IosRawDeflateDecoder : RawDeflateDecoder {
    private var stream: z_stream? = null

    override fun decode(
        source: ByteArray,
        offset: Int,
        size: Int,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ) {
        if (length == 0) return
        val stream = stream ?: openStream().also { stream = it }
        check(inflateReset(stream.ptr) == Z_OK)
        source.usePinned { input ->
            destination.usePinned { output ->
                stream.next_in = input.addressOf(offset).reinterpret()
                stream.avail_in = size.toUInt()
                stream.next_out = output.addressOf(destinationOffset).reinterpret()
                stream.avail_out = length.toUInt()
                check(inflate(stream.ptr, Z_FINISH) == Z_STREAM_END && stream.total_out.toInt() == length) { "Truncated resource" }
            }
        }
    }

    private fun openStream(): z_stream {
        val stream =
            nativeHeap.alloc<z_stream>().apply {
                zalloc = null
                zfree = null
                opaque = null
            }
        if (inflateInit2_(stream.ptr, RAW_DEFLATE_WINDOW_BITS, zlibVersion(), sizeOf<z_stream>().toInt()) != Z_OK) {
            nativeHeap.free(stream.ptr)
            error("Could not initialise zlib")
        }
        return stream
    }

    override fun close() {
        stream?.let {
            inflateEnd(it.ptr)
            nativeHeap.free(it.ptr)
        }
        stream = null
    }

    private companion object {
        const val RAW_DEFLATE_WINDOW_BITS = -15
    }
}
