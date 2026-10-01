package com.tap.zoot.runtime.engine

import at.released.weh.host.EmbedderHost
import com.tap.n64.input.ControllerState
import com.tap.zoot.audio.AudioOutput
import com.tap.zoot.runtime.host.HostImports
import com.tap.zoot.runtime.platform.Logger
import com.tap.zoot.runtime.resources.GameAssets
import com.tap.zoot.runtime.resources.O2rArchive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(GameSessionScope::class)
class ChasmGameEngine internal constructor(
    assets: GameAssets,
    wasiHost: EmbedderHost,
    logger: Logger,
    imports: HostImports,
    private val audio: AudioOutput,
    private val archive: O2rArchive,
    override val timings: HostTimings,
) : GameEngine {
    private val guest = Guest(assets, wasiHost, logger, imports.all)

    fun start() {
        archive.open()
        guest.start()
    }

    override fun step(
        tick: Long,
        input: ControllerState,
    ) {
        guest.writeInput(input)
        timings.reset()
        check(guest.step(tick * GuestAbi.TICK_MICROS) >= 0)
    }

    override fun mixAudio() {
        for (frames in GuestAbi.AUDIO_CHUNK_FRAMES) check(guest.mixAudio(frames) >= 0)
    }

    override fun traceEntrance(entrance: Int) = guest.traceEntrance(entrance)

    override fun setOutputVolume(volume: Float) {
        audio.volume = volume
    }

    override fun setMixVolumes(
        music: Float,
        effects: Float,
        fanfares: Float,
    ) = guest.setMixVolumes(music, effects, fanfares)

    override fun pauseAudio() = audio.pause()

    override fun resumeAudio() = audio.resume()

    override fun close() {
        try {
            guest.close()
        } finally {
            try {
                audio.close()
            } finally {
                archive.close()
            }
        }
    }
}
