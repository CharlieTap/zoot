package com.tap.zoot.runtime.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@Inject
@ContributesBinding(AppScope::class)
class AndroidGameThread : GameThread {
    override val dispatcher: ExecutorCoroutineDispatcher =
        Executors
            .newSingleThreadExecutor { runnable -> Thread(runnable, "Zoot-OOT") }
            .asCoroutineDispatcher()

    override fun close() = dispatcher.close()
}
