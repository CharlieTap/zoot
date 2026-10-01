package com.tap.zoot

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

@DependencyGraph(AppScope::class)
interface AppGraph :
    ViewModelGraph,
    MetroAppComponentProviders {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
