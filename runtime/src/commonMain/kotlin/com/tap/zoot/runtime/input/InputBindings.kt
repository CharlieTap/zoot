package com.tap.zoot.runtime.input

import com.tap.n64.input.N64Input
import com.tap.n64.input.N64InputSink
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object InputBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun input(): N64Input = N64Input()

    @Provides
    fun inputSink(input: N64Input): N64InputSink = input
}
