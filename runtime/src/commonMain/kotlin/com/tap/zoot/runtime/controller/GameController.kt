package com.tap.zoot.runtime.controller

import com.tap.crashreporting.CrashReport
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.runtime.benchmark.BenchmarkSession
import kotlinx.coroutines.flow.StateFlow

sealed interface GameState {
    data object Detached : GameState

    data object Starting : GameState

    data object Running : GameState

    data object Paused : GameState

    /** Kept until [GameController.restart], including across [GameController.detach]. */
    data class Failed(
        val report: CrashReport,
        val duringStartup: Boolean,
    ) : GameState
}

interface GameController : AutoCloseable {
    val state: StateFlow<GameState>

    fun attach(rendererFactory: RendererFactory)

    fun detach()

    /** Starts a new session after a failure, once a surface is attached. */
    fun restart()

    fun setActive(active: Boolean)

    fun configure(configuration: GameConfiguration)

    fun useBenchmark(session: BenchmarkSession?)
}
