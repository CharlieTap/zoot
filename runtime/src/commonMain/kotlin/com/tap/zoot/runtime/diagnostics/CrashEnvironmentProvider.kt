package com.tap.zoot.runtime.diagnostics

import com.tap.crashreporting.CrashEnvironment
import com.tap.zoot.runtime.config.ZootBuildConfig
import com.tap.zoot.runtime.resources.GameAsset
import com.tap.zoot.runtime.resources.GameAssets

interface CrashEnvironmentProvider {
    fun environment(): CrashEnvironment
}

internal fun crashEnvironment(
    assets: GameAssets,
    platform: String,
    osVersion: String?,
    deviceModel: String?,
): CrashEnvironment =
    CrashEnvironment(
        applicationVersion = ZootBuildConfig.APPLICATION_VERSION,
        chasmVersion = ZootBuildConfig.CHASM_VERSION,
        wasmSha256 = runCatching { assets.read(GameAsset.WasmSha256).decodeToString().trim() }.getOrNull(),
        platform = platform,
        osVersion = osVersion,
        deviceModel = deviceModel,
    )
