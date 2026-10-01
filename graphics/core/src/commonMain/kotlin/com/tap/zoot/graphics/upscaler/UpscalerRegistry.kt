package com.tap.zoot.graphics.upscaler

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Ordered, validated view of the upscalers contributed through Metro. */
@Inject
@SingleIn(AppScope::class)
class UpscalerRegistry(
    upscalers: Set<Upscaler>,
) {
    val options: List<Upscaler> = upscalers.sortedWith(compareBy<Upscaler> { it.order }.thenBy { it.id.value })

    private val byId = options.associateBy(Upscaler::id)

    init {
        require(byId.size == options.size) { "Upscaler ids must be unique: ${options.map { it.id.value }}" }
        require(UpscalerId.Default in byId) { "The default ${UpscalerId.Default.value} upscaler is not registered" }
    }

    operator fun get(id: UpscalerId): Upscaler = byId[id] ?: byId.getValue(UpscalerId.Default)

    fun resolve(id: UpscalerId): UpscalerId = if (id in byId) id else UpscalerId.Default
}
