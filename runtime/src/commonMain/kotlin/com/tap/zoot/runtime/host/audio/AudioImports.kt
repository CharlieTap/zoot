package com.tap.zoot.runtime.host.audio

import com.tap.zoot.audio.AudioOutput
import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.GuestMemoryTransfer
import com.tap.zoot.runtime.host.HostImportGroup
import com.tap.zoot.runtime.host.functionImport
import com.tap.zoot.runtime.host.i32
import com.tap.zoot.runtime.platform.monotonicNanos
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.withMemory
import io.github.charlietap.chasm.host.writeI32

/** Direct PCM callback backed by one reusable guest-memory transfer. */
@Inject
internal class AudioImports(
    private val audio: AudioOutput,
    private val timings: HostTimings,
) : HostImportGroup {
    private val transfer = GuestMemoryTransfer()

    override val imports: List<CodegenImport> =
        listOf(
            functionImport(
                "oot_audio",
                "submit_pcm",
                parameterTypes = { i32(3) },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        withMemory(0) {
                            val start = monotonicNanos()
                            val pcmPointer = parameters.readI32(0)
                            val frameCount = parameters.readI32(1)
                            val sampleRate = parameters.readI32(2)
                            val accepted =
                                transfer.read(this, pcmPointer, frameCount * BYTES_PER_FRAME) { pcm ->
                                    audio.submit(pcm, 0, frameCount, sampleRate)
                                }
                            timings.audioNanos += monotonicNanos() - start
                            results.writeI32(0, accepted)
                        }
                    },
            ),
        )

    private companion object {
        const val BYTES_PER_FRAME = 4
    }
}
