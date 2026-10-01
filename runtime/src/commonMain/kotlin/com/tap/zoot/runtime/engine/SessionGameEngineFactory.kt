package com.tap.zoot.runtime.engine

import com.tap.zoot.graphics.Renderer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Constructs a fully started engine and closes it if startup fails. */
@Inject
@ContributesBinding(AppScope::class)
class SessionGameEngineFactory(
    private val sessions: GameSessionGraph.Factory,
) : GameEngine.Factory {
    override fun create(renderer: Renderer): GameEngine {
        val engine = sessions.create(renderer).engine
        try {
            engine.start()
        } catch (failure: Throwable) {
            engine.close()
            throw failure
        }
        return engine
    }
}
