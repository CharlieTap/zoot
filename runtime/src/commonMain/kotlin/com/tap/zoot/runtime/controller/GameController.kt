package com.tap.zoot.runtime.controller

import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.runtime.benchmark.BenchmarkSession
import kotlinx.coroutines.flow.StateFlow

sealed interface GameState {
    data object Detached : GameState

    data object Starting : GameState

    data object Running : GameState

    data object Paused : GameState

    data class Failed(
        val cause: Throwable,
    ) : GameState
}

interface GameController : AutoCloseable {
    val state: StateFlow<GameState>

    fun attach(rendererFactory: RendererFactory)

    fun detach()

    fun setActive(active: Boolean)

    fun configure(configuration: GameConfiguration)

    fun useBenchmark(session: BenchmarkSession?)
}
