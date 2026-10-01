package com.tap.zoot.runtime.platform

import android.app.Application
import at.released.weh.host.EmbedderHost
import com.tap.zoot.audio.AndroidAudioOutput
import com.tap.zoot.audio.AudioOutput
import com.tap.zoot.runtime.engine.GameSessionScope
import com.tap.zoot.runtime.wasi.wasiHost
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(GameSessionScope::class)
object AndroidSessionBindings {
    @Provides
    @SingleIn(GameSessionScope::class)
    fun audioOutput(): AudioOutput = AndroidAudioOutput()

    @Provides
    fun embedderHost(
        application: Application,
        logger: Logger,
    ): EmbedderHost = wasiHost(application.filesDir.absolutePath, logger)
}
