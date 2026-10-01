package com.tap.zoot.settings

import com.tap.zoot.graphics.upscaler.UpscalerRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SettingsEditor {
    /** Applies a transient change, such as a slider position while it is being dragged. */
    fun preview(transform: (GameSettings) -> GameSettings)

    /** Applies and persists a discrete settings change. */
    fun commit(transform: (GameSettings) -> GameSettings)

    /** Persists the current in-memory settings. */
    fun persist()
}

@Inject
@SingleIn(AppScope::class)
class GameSettingsStore(
    private val storage: SettingsStorage,
    upscalers: UpscalerRegistry,
) : SettingsEditor {
    private val mutableState =
        MutableStateFlow(
            GameSettingsCodec.decode(storage::read).let { settings ->
                settings.copy(display = settings.display.copy(upscaler = upscalers.resolve(settings.display.upscaler)))
            },
        )
    val state: StateFlow<GameSettings> = mutableState.asStateFlow()

    override fun preview(transform: (GameSettings) -> GameSettings) {
        mutableState.value = transform(mutableState.value)
    }

    override fun commit(transform: (GameSettings) -> GameSettings) {
        val updated = transform(mutableState.value)
        if (updated == mutableState.value) return
        mutableState.value = updated
        persist()
    }

    override fun persist() = storage.write(GameSettingsCodec.encode(mutableState.value))
}
