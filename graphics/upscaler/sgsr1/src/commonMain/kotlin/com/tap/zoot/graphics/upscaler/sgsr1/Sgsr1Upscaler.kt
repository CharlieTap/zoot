package com.tap.zoot.graphics.upscaler.sgsr1

import com.tap.zoot.graphics.upscaler.Upscaler
import com.tap.zoot.graphics.upscaler.UpscalerId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ContributesIntoSet(AppScope::class, binding<Upscaler>())
@Inject
class Sgsr1Upscaler : Upscaler {
    override val id = UpscalerId.Sgsr1
    override val order = 2
    override val name = "SGSR 1"
    override val shaderSource = SGSR1_SHADER
}
