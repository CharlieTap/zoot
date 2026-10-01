package com.tap.zoot.runtime.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Inject
@ContributesBinding(AppScope::class)
class IosGameThread : GameThread {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, "Zoot-OOT")

    override fun close() = Unit
}
