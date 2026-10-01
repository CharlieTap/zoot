package com.tap.zoot.ios

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph

@DependencyGraph(AppScope::class)
interface AppGraph {
    val app: IosApp
}
