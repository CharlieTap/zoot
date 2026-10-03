package com.tap.zoot.runtime.diagnostics

import android.os.Build
import com.tap.crashreporting.CrashEnvironment
import com.tap.zoot.runtime.resources.GameAssets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class AndroidCrashEnvironmentProvider(
    private val assets: GameAssets,
) : CrashEnvironmentProvider {
    override fun environment(): CrashEnvironment =
        crashEnvironment(
            assets = assets,
            platform = "Android",
            osVersion = Build.VERSION.RELEASE,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
}
