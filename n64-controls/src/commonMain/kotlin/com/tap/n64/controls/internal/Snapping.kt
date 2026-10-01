package com.tap.n64.controls.internal

import kotlin.math.abs

internal fun constrainCentre(
    value: Float,
    extent: Float,
    surface: Float,
): Float = value.coerceIn(extent / 2f, surface - extent / 2f)

internal fun snapCentre(
    value: Float,
    candidates: FloatArray,
    threshold: Float,
): Float {
    var nearest = value
    var distance = threshold
    for (candidate in candidates) {
        val delta = abs(value - candidate)
        if (delta < distance) {
            nearest = candidate
            distance = delta
        }
    }
    return nearest
}
