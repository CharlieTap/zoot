package com.tap.zoot.runtime.engine

import at.released.weh.host.EmbedderHost
import com.tap.n64.input.ControllerState
import com.tap.zoot.runtime.generated.OotWasmModule
import com.tap.zoot.runtime.generated.ootWasmModule
import com.tap.zoot.runtime.platform.Logger
import com.tap.zoot.runtime.platform.monotonicNanos
import com.tap.zoot.runtime.resources.GameAsset
import com.tap.zoot.runtime.resources.GameAssets
import com.tap.zoot.runtime.resources.writeI32
import io.github.charlietap.chasm.config.RuntimeConfig
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.embedding.dropStore
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.runtime.value.NumberValue

/** Owns the generated guest, its WASI environment and input buffer. */
internal class Guest(
    private val assets: GameAssets,
    private val wasiHost: EmbedderHost,
    private val logger: Logger,
    private val imports: List<CodegenImport>,
) : AutoCloseable {
    private var store: Store? = null
    private lateinit var instance: Instance
    private lateinit var module: OotWasmModule
    private var inputPointer = 0
    private val input = ByteArray(GuestAbi.INPUT_SIZE)

    fun start() {
        val started = monotonicNanos()
        module =
            ootWasmModule(
                binary = assets.read(GameAsset.Wasm),
                wasiHost = wasiHost,
                imports = imports,
                instanceFactory = { store, decodedModule, imports ->
                    this.store = store
                    instance(store, decodedModule, imports, RuntimeConfig(debugInfo = true)).expect("Instantiate guest").also {
                        instance =
                            it
                    }
                },
            )
        check(module.ootAbiVersion() == GuestAbi.VERSION)
        val language =
            assets
                .read(GameAsset.Language)
                .decodeToString()
                .trim()
                .toInt()
        check(module.ootSetLanguage(language) == 0)
        check(module.ootStart() == 0)
        inputPointer = module.ootInputBuffer()
        logger.info("Guest initialised in ${(monotonicNanos() - started) / 1e6} ms; Chasm direct hosts")
    }

    fun writeInput(state: ControllerState) {
        input.writeI32(GuestAbi.INPUT_VERSION, GuestAbi.VERSION)
        input.writeI32(GuestAbi.INPUT_LENGTH, GuestAbi.INPUT_SIZE)
        input.writeI32(GuestAbi.INPUT_BUTTONS, state.buttons)
        input.writeI32(GuestAbi.INPUT_STICK, (state.stickX and 0xffff) or (state.stickY shl 16))
        module.memory.write(pointer = inputPointer, buffer = input)
    }

    fun step(timestampMicros: Long): Int = module.ootStep(timestampMicros)

    fun mixAudio(frames: Int): Int = module.ootAudioStep(frames)

    fun setMixVolumes(
        music: Float,
        effects: Float,
        fanfares: Float,
    ) = module.ootSetAudioVolumes(music, effects, fanfares)

    // This export exists only in profiling guests, so it is not part of the generated release interface.
    fun traceEntrance(entrance: Int) {
        invoke(checkNotNull(store), instance, "oot_trace_entrance", listOf(NumberValue.I32(entrance)))
            .expect("oot_trace_entrance")
    }

    override fun close() {
        try {
            store?.let(::dropStore)
        } finally {
            wasiHost.close()
        }
    }
}
