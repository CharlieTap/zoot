@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.runtime.platform

import com.tap.zoot.runtime.resources.GameAsset
import com.tap.zoot.runtime.resources.GameAssets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@Inject
@ContributesBinding(AppScope::class)
class IosGameAssets : GameAssets {
    override fun read(asset: GameAsset): ByteArray {
        val path = checkNotNull(NSBundle.mainBundle.pathForResource(asset.fileName, null)) { "Missing asset: ${asset.fileName}" }
        val data = checkNotNull(NSData.dataWithContentsOfFile(path)) { "Cannot read asset: ${asset.fileName}" }
        return ByteArray(data.length.toInt()).also { bytes ->
            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        }
    }
}
