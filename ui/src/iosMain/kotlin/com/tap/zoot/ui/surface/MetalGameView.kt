@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.ui.surface

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.MetalKit.MTKView
import platform.QuartzCore.CAMetalLayer
import kotlin.math.roundToInt

internal class MetalSurface(
    val layer: CAMetalLayer,
    val width: Int,
    val height: Int,
)

internal class MetalGameView(
    private val onResized: (MetalSurface) -> Unit,
) : MTKView(CGRectMake(0.0, 0.0, 0.0, 0.0), MTLCreateSystemDefaultDevice()) {
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    init {
        paused = true
        enableSetNeedsDisplay = false
        framebufferOnly = false
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            val width = (size.width * contentScaleFactor).roundToInt()
            val height = (size.height * contentScaleFactor).roundToInt()
            if (width > 0 && height > 0 && (surfaceWidth != width || surfaceHeight != height)) {
                surfaceWidth = width
                surfaceHeight = height
                drawableSize = CGSizeMake(width.toDouble(), height.toDouble())
                onResized(MetalSurface(layer as CAMetalLayer, width, height))
            }
        }
    }
}
