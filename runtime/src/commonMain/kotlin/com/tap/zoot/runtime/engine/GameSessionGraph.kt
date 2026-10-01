package com.tap.zoot.runtime.engine

import com.tap.zoot.graphics.Renderer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@GraphExtension(GameSessionScope::class)
interface GameSessionGraph {
    val engine: ChasmGameEngine

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun create(
            @Provides renderer: Renderer,
        ): GameSessionGraph
    }
}
