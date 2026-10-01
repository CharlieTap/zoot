package com.tap.zoot.runtime.wasi

import at.released.weh.filesystem.stdio.StdioSink
import at.released.weh.host.EmbedderHost
import com.tap.zoot.runtime.platform.Logger

internal fun wasiHost(
    saveDirectory: String,
    logger: Logger,
): EmbedderHost =
    EmbedderHost {
        fileSystem { addPreopenedDirectory(saveDirectory, "/saves") }
        stdout = StdioSink.Provider { WasiLogSink(logger::info) }
        stderr = StdioSink.Provider { WasiLogSink { message -> logger.warning(message) } }
    }
