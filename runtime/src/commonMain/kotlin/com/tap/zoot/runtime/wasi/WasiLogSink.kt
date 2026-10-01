package com.tap.zoot.runtime.wasi

import at.released.weh.filesystem.stdio.StdioSink
import kotlinx.io.Buffer
import kotlinx.io.readString

internal class WasiLogSink(
    private val log: (String) -> Unit,
) : StdioSink {
    override fun write(
        source: Buffer,
        byteCount: Long,
    ) {
        log(source.readString(byteCount))
    }

    override fun flush() = Unit

    override fun close() = Unit
}
