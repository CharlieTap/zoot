package com.tap.zoot.runtime.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import platform.Foundation.NSLog

@Inject
@ContributesBinding(AppScope::class)
class IosLogger : Logger {
    override fun info(message: String) = log(message)

    override fun warning(
        message: String,
        throwable: Throwable?,
    ) = log(message, throwable)

    override fun error(
        message: String,
        throwable: Throwable?,
    ) = log(message, throwable)

    private fun log(
        message: String,
        throwable: Throwable? = null,
    ) {
        val suffix = throwable?.let { "\n$it" }.orEmpty()
        NSLog("%s", "Zoot: $message$suffix")
    }
}
