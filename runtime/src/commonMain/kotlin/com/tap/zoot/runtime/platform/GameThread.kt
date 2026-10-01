package com.tap.zoot.runtime.platform

import kotlinx.coroutines.CoroutineDispatcher

/** Dedicated serial execution context for one guest instance. */
interface GameThread : AutoCloseable {
    val dispatcher: CoroutineDispatcher
}
