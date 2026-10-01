package com.tap.zoot.runtime.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

fun interface MonotonicClock {
    fun nowNanos(): Long
}

@Inject
@ContributesBinding(AppScope::class)
class SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = monotonicNanos()
}
