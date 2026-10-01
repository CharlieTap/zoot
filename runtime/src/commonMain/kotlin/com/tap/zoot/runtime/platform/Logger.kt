package com.tap.zoot.runtime.platform

/** Records messages produced by the game host and guest streams. */
interface Logger {
    fun info(message: String)

    fun warning(
        message: String,
        throwable: Throwable? = null,
    )

    fun error(
        message: String,
        throwable: Throwable? = null,
    )
}
