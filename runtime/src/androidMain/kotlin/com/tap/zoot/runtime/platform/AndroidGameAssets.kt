package com.tap.zoot.runtime.platform

import android.app.Application
import com.tap.zoot.runtime.resources.GameAsset
import com.tap.zoot.runtime.resources.GameAssets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class AndroidGameAssets(
    private val application: Application,
) : GameAssets {
    override fun read(asset: GameAsset): ByteArray = application.assets.open(asset.fileName).use { it.readBytes() }
}
