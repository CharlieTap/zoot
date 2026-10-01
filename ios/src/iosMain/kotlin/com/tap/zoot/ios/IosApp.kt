package com.tap.zoot.ios

import com.tap.zoot.runtime.controller.DefaultGameController
import com.tap.zoot.ui.GameScreenDependencies
import dev.zacsweers.metro.Inject

@Inject
class IosApp(
    val controller: DefaultGameController,
    val dependencies: GameScreenDependencies,
) : AutoCloseable {
    override fun close() = controller.close()
}
