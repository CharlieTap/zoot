package com.tap.zoot.runtime.platform

import com.tap.zoot.runtime.engine.GameSessionScope
import com.tap.zoot.runtime.resources.RawDeflateDecoder
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.util.zip.Inflater

@Inject
@ContributesBinding(GameSessionScope::class)
class AndroidRawDeflateDecoder : RawDeflateDecoder {
    private val inflater = Inflater(true)

    override fun decode(
        source: ByteArray,
        offset: Int,
        size: Int,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ) {
        inflater.reset()
        inflater.setInput(source, offset, size)
        var written = 0
        while (!inflater.finished() && written < length) {
            val count = inflater.inflate(destination, destinationOffset + written, length - written)
            check(count > 0) { "Truncated resource" }
            written += count
        }
        check(written == length && inflater.finished())
    }

    override fun close() = inflater.end()
}
