package com.tap.zoot.plugin.tools

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Lets one large CMake build at a time use every core. */
abstract class NativeBuildService : BuildService<BuildServiceParameters.None> {
    companion object {
        const val NAME = "zootNativeBuild"
    }
}

internal fun Project.registerNativeBuildService(): Provider<NativeBuildService> =
    gradle.sharedServices.registerIfAbsent(NativeBuildService.NAME, NativeBuildService::class.java) {
        maxParallelUsages.set(1)
    }

internal fun onOff(value: Boolean) = if (value) "ON" else "OFF"
