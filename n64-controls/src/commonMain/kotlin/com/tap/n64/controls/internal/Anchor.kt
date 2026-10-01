package com.tap.n64.controls.internal

internal sealed interface Anchor {
    fun resolve(extent: Float): Float

    data class FromStart(
        val offset: Float,
    ) : Anchor {
        override fun resolve(extent: Float): Float = offset
    }

    data class FromEnd(
        val offset: Float,
    ) : Anchor {
        override fun resolve(extent: Float): Float = extent - offset
    }

    data object Centre : Anchor {
        override fun resolve(extent: Float): Float = extent / 2f
    }
}
