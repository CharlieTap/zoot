package com.tap.zoot.runtime.engine

import at.released.weh.filesystem.stdio.StdioSink
import at.released.weh.host.EmbedderHost
import com.tap.n64.input.ControllerState
import com.tap.zoot.runtime.generated.ootWasmModule
import com.tap.zoot.runtime.platform.Logger
import com.tap.zoot.runtime.resources.GameAsset
import com.tap.zoot.runtime.resources.GameAssets
import com.tap.zoot.runtime.wasi.WasiLogSink
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.embedding.codegen.FunctionImport
import io.github.charlietap.chasm.embedding.dropStore
import io.github.charlietap.chasm.embedding.dsl.FunctionTypeBuilder
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.withMemory
import io.github.charlietap.chasm.host.writeI32
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GeneratedGuestModuleTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun generatedGuestPreservesStartupInputAudioSettingsAndWasi() {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val host = host(stdout, stderr)
        val observedInput = mutableListOf<Int>()
        var initialisations = 0
        val imports =
            imports(
                initialise = {
                    initialisations++
                    0
                },
                input =
                    HostFunction { parameters, results ->
                        withMemory(0) {
                            val pointer = parameters.readI32(0)
                            observedInput.clear()
                            repeat(5) { observedInput.add(readI32(pointer + it * 4)) }
                            results.writeI32(0, readI32(pointer + 8))
                        }
                    },
            )
        guest(host, imports).use { guest ->
            guest.start()
            assertEquals(1, initialisations)
            guest.writeInput(ControllerState.of(buttons = 0x8000, stickX = -40, stickY = 60))
            assertEquals(0x8000 + 123, guest.step(123))
            assertEquals(listOf(3, 20, 0x8000, (60 shl 16) or 0xffd8, 0), observedInput)
            guest.writeInput(ControllerState.of(buttons = 2, stickX = 1, stickY = -2))
            assertEquals(9, guest.step(7))
            assertEquals(listOf(3, 20, 2, (-2 shl 16) or 1, 0), observedInput)
            assertEquals(544, guest.mixAudio(544))
            assertEquals(528, guest.mixAudio(528))
            guest.setMixVolumes(0.2f, 0.4f, 0.6f)
            assertEquals(246, guest.step(-1))
            assertFails { guest.traceEntrance(529) }
        }
        assertEquals(1, host.closes)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), File(temporary.root, "codegen-test.bin").readBytes())
        assertTrue(stdout.joinToString("").contains("guest stdout"))
        assertTrue(stderr.joinToString("").contains("guest stderr"))
    }

    @Test
    fun profilingGuestSupportsTheOptionalImportAndDiagnosticExport() {
        val phases = mutableListOf<Int>()
        val profiler =
            FunctionImport(
                "oot_profile",
                "fast3d",
                FunctionTypeBuilder().apply { params { i32() } }.build(),
                HostFunction { parameters, _ -> phases.add(parameters.readI32(0)) },
            )
        guest(host(), imports() + profiler, fixture("guest-profile.wasm")).use { guest ->
            guest.start()
            guest.traceEntrance(529)
            assertEquals(529, guest.step(-2))
            assertEquals(listOf(0, 1), phases)
        }
    }

    @Test
    fun generatedMemoryReusesBuffersAndHonoursOffsets() {
        var store: Store? = null
        host().use { host ->
            try {
                val module =
                    ootWasmModule(
                        binary = fixture("guest.wasm"),
                        wasiHost = host,
                        imports = imports(),
                        instanceFactory = { owner, decoded, imports ->
                            store = owner
                            instance(owner, decoded, imports).expect("Instantiate fixture")
                        },
                    )
                val pointer = module.ootInputBuffer()
                val source = byteArrayOf(9, 1, 2, 3, 9)
                module.memory.write(pointer, source, bufferPointer = 1, bytesToWrite = 3)
                val destination = ByteArray(5) { 8 }
                assertSame(destination, module.memory.read(destination, pointer, bufferPointer = 1, bytesToRead = 3))
                assertContentEquals(byteArrayOf(8, 1, 2, 3, 8), destination)
            } finally {
                store?.let(::dropStore)
            }
        }
    }

    @Test
    fun decodeFailureStillClosesTheWasiHost() = assertStartupFailure(byteArrayOf(0))

    @Test
    fun wasiLinkFailureStillClosesTheWasiHost() = assertStartupFailure(fixture("unsupported-wasi.wasm"))

    @Test
    fun missingCustomImportStillClosesTheWasiHost() = assertStartupFailure(imports = emptyList())

    @Test
    fun initializerTrapStillClosesTheWasiHost() = assertStartupFailure(imports = imports(initialise = { 1 }))

    private fun assertStartupFailure(
        binary: ByteArray = fixture("guest.wasm"),
        imports: List<CodegenImport> = imports(),
    ) {
        val host = host()
        assertFails {
            guest(host, imports, binary).use { it.start() }
        }
        assertEquals(1, host.closes)
    }

    private fun host(
        stdoutMessages: MutableList<String> = mutableListOf(),
        stderrMessages: MutableList<String> = mutableListOf(),
    ) = ClosingHost(
        EmbedderHost {
            fileSystem { addPreopenedDirectory(temporary.root.absolutePath, "/saves") }
            stdout = StdioSink.Provider { WasiLogSink(stdoutMessages::add) }
            stderr = StdioSink.Provider { WasiLogSink(stderrMessages::add) }
        },
    )

    private fun guest(
        host: EmbedderHost,
        imports: List<CodegenImport>,
        binary: ByteArray = fixture("guest.wasm"),
    ) = Guest(
        assets =
            object : GameAssets {
                override fun read(asset: GameAsset): ByteArray =
                    when (asset) {
                        GameAsset.Wasm -> binary
                        GameAsset.Language -> "1".encodeToByteArray()
                        else -> error("Fixture has no archive")
                    }
            },
        wasiHost = host,
        logger =
            object : Logger {
                override fun info(message: String) = Unit

                override fun warning(
                    message: String,
                    throwable: Throwable?,
                ) = Unit

                override fun error(
                    message: String,
                    throwable: Throwable?,
                ) = Unit
            },
        imports = imports,
    )

    private fun imports(
        initialise: () -> Int = { 0 },
        input: HostFunction = HostFunction { _, results -> results.writeI32(0, 0) },
    ): List<CodegenImport> =
        listOf(
            FunctionImport(
                "test",
                "initialise",
                FunctionTypeBuilder().apply { results { i32() } }.build(),
                HostFunction { _, results -> results.writeI32(0, initialise()) },
            ),
            FunctionImport(
                "test",
                "input",
                FunctionTypeBuilder()
                    .apply {
                        params { i32() }
                        results { i32() }
                    }.build(),
                input,
            ),
        )

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/codegen/$name")).use { it.readBytes() }

    private class ClosingHost(
        private val delegate: EmbedderHost,
    ) : EmbedderHost by delegate {
        var closes = 0

        override fun close() {
            closes++
            delegate.close()
        }
    }
}
