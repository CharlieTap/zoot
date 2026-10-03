@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.runtime.diagnostics

import com.tap.crashreporting.CrashEnvironment
import com.tap.zoot.runtime.resources.GameAssets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

@Inject
@ContributesBinding(AppScope::class)
class IosCrashEnvironmentProvider(
    private val assets: GameAssets,
) : CrashEnvironmentProvider {
    override fun environment(): CrashEnvironment =
        crashEnvironment(
            assets = assets,
            platform = "iOS",
            osVersion = UIDevice.currentDevice.systemVersion,
            deviceModel = simulatorModel() ?: hardwareModel(),
        )

    private fun simulatorModel(): String? = NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String

    /** Such as `iPhone15,2`. */
    private fun hardwareModel(): String? =
        memScoped {
            val system = alloc<utsname>()
            if (uname(system.ptr) == 0) system.machine.toKString() else null
        }
}
