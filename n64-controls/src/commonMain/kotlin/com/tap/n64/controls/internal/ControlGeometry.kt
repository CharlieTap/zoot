package com.tap.n64.controls.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import com.tap.n64.controls.ControlId
import com.tap.n64.controls.ControlLayout

internal class ControlGeometry(
    val width: Int,
    val height: Int,
    val density: Float,
    layout: ControlLayout,
) {
    val unit = minOf(density, height / 400f, width / 720f)
    private val bounds =
        Array(ControlId.entries.size) { index ->
            val id = ControlId.entries[index]
            val controlWidth = (id.width * unit).toInt()
            val controlHeight = (id.height * unit).toInt()
            val position = layout[id]
            val centreX = position?.x?.times(width) ?: (id.defaultX(width / unit) * unit)
            val centreY = position?.y?.times(height) ?: (id.defaultY(height / unit) * unit)
            val left = constrainCentre(centreX, controlWidth.toFloat(), width.toFloat()).toInt() - controlWidth / 2
            val top = constrainCentre(centreY, controlHeight.toFloat(), height.toFloat()).toInt() - controlHeight / 2
            IntRect(left, top, left + controlWidth, top + controlHeight)
        }

    operator fun get(id: ControlId): IntRect = bounds[id.ordinal]

    fun hit(point: Offset): ControlId? =
        ControlId.entries.lastOrNull { id ->
            val rect = get(id)
            point.x >= rect.left && point.x < rect.right && point.y >= rect.top && point.y < rect.bottom
        }

    fun withLayout(layout: ControlLayout): ControlGeometry = ControlGeometry(width, height, density, layout)
}

internal val IntRect.centreX: Float
    get() = (left + right) / 2f

internal val IntRect.centreY: Float
    get() = (top + bottom) / 2f
