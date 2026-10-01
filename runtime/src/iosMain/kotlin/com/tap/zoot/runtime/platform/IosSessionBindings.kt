@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.runtime.platform

import at.released.weh.host.EmbedderHost
import com.tap.zoot.audio.AudioOutput
import com.tap.zoot.audio.IosAudioOutput
import com.tap.zoot.runtime.engine.GameSessionScope
import com.tap.zoot.runtime.wasi.wasiHost
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@BindingContainer
@ContributesTo(GameSessionScope::class)
object IosSessionBindings {
    @Provides
    @SingleIn(GameSessionScope::class)
    fun audioOutput(): AudioOutput = IosAudioOutput()

    @Provides
    fun embedderHost(logger: Logger): EmbedderHost {
        val saveDirectory = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String) + "/saves"
        NSFileManager.defaultManager.createDirectoryAtPath(saveDirectory, true, null, null)
        return wasiHost(saveDirectory, logger)
    }
}
