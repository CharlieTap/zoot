package com.tap.zoot.runtime.platform

import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class AndroidLogger : Logger {
    override fun info(message: String) {
        Log.println(Log.INFO, TAG, message)
    }

    override fun warning(
        message: String,
        throwable: Throwable?,
    ) {
        if (throwable == null) {
            Log.println(Log.WARN, TAG, message)
        } else {
            Log.w(TAG, message, throwable)
        }
    }

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {
        if (throwable == null) {
            Log.println(Log.ERROR, TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }

    private companion object {
        const val TAG = "Zoot"
    }
}
