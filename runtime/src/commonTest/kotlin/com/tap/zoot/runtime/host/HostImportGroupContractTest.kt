package com.tap.zoot.runtime.host

import com.tap.zoot.audio.AudioOutput
import com.tap.zoot.runtime.TestRenderer
import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.audio.AudioImports
import com.tap.zoot.runtime.host.graphics.DepthQueryImports
import com.tap.zoot.runtime.host.graphics.GraphicsImports
import com.tap.zoot.runtime.host.profiling.ProfilingImports
import com.tap.zoot.runtime.host.resources.ResourceImports
import com.tap.zoot.runtime.resources.GameAsset
import com.tap.zoot.runtime.resources.GameAssets
import com.tap.zoot.runtime.resources.O2rArchive
import com.tap.zoot.runtime.resources.RawDeflateDecoder
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import kotlin.test.Test
import kotlin.test.assertEquals

class HostImportGroupContractTest {
    @Test
    fun audioImportNamesMatchTheGuestAbi() {
        assertEquals(setOf("oot_audio.submit_pcm"), AudioImports(NoOpAudio, HostTimings()).imports.names())
    }

    @Test
    fun graphicsImportNamesMatchTheGuestAbi() {
        assertEquals(
            setOf(
                "oot_gpu.shader",
                "oot_gpu.texture",
                "oot_gpu.delete_texture",
                "oot_gpu.draw",
                "oot_gpu.frame",
                "oot_gpu.target",
                "oot_gpu.clear",
                "oot_gpu.copy",
                "oot_gpu.read",
                "oot_gpu.depth",
            ),
            GraphicsImports(TestRenderer(), HostTimings()).imports.names(),
        )
        assertEquals(
            setOf("oot_graphics.prepare_depth_query", "oot_graphics.read_depth_query"),
            DepthQueryImports(TestRenderer(), HostTimings()).imports.names(),
        )
        assertEquals(setOf("oot_profile.fast3d"), ProfilingImports(HostTimings()).imports.names())
    }

    @Test
    fun resourceImportNamesMatchTheGuestAbi() {
        assertEquals(
            setOf(
                "oot_resources.resource_info",
                "oot_resources.resource_read",
                "oot_resources.resource_name",
                "oot_resources.resource_list",
            ),
            ResourceImports(emptyArchive(), HostTimings()).imports.names(),
        )
    }

    @Test
    fun importNamesAreUniqueAcrossSubsystems() {
        val imports = hostTestImports()
        assertEquals(imports.size, imports.names().size)
    }

    private fun List<CodegenImport>.names() = map { "${it.moduleName}.${it.entityName}" }.toSet()
}

internal fun hostTestImports(): List<CodegenImport> {
    val timings = HostTimings()
    val renderer = TestRenderer()
    return HostImports(
        graphics = GraphicsImports(renderer, timings),
        depthQueries = DepthQueryImports(renderer, timings),
        profiling = ProfilingImports(timings),
        audio = AudioImports(NoOpAudio, timings),
        resources = ResourceImports(emptyArchive(), timings),
    ).all
}

private fun emptyArchive() =
    O2rArchive(
        assets =
            object : GameAssets {
                override fun read(asset: GameAsset) = error("Archive access is not needed for the import contract")
            },
        decoder =
            object : RawDeflateDecoder {
                override fun decode(
                    source: ByteArray,
                    offset: Int,
                    size: Int,
                    destination: ByteArray,
                    destinationOffset: Int,
                    length: Int,
                ) = error("Archive access is not needed for the import contract")

                override fun close() = Unit
            },
    )

private object NoOpAudio : AudioOutput {
    override var volume = 1f

    override fun submit(
        pcm: ByteArray,
        offset: Int,
        frameCount: Int,
        sampleRate: Int,
    ) = frameCount

    override fun pause() = Unit

    override fun resume() = Unit

    override fun close() = Unit
}
