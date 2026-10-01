package com.tap.zoot.runtime.engine

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Per-frame host timing accumulated directly by the host callbacks. */
@Inject
@SingleIn(GameSessionScope::class)
class HostTimings {
    var graphicsNanos: Long = 0
        internal set
    var audioNanos: Long = 0
        internal set
    var fast3dNanos: Long = 0
        internal set
    var resourcesNanos: Long = 0
        internal set

    internal fun reset() {
        graphicsNanos = 0
        audioNanos = 0
        fast3dNanos = 0
        resourcesNanos = 0
    }
}
