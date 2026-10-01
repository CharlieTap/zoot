package com.tap.n64.controls.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tap.n64.controls.ControlId

internal enum class Sprite(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    A(32, 324, 264, 261),
    B(337, 323, 264, 262),
    C(650, 320, 265, 265),
    Start(961, 381, 263, 150),
    Shoulder(28, 754, 273, 156),
    Dpad(326, 680, 290, 290),
    Socket(649, 680, 290, 294),
    Stick(987, 712, 236, 236),
    ;

    val offset = IntOffset(x, y)
    val size = IntSize(width, height)
}

internal val ControlId.sprite: Sprite
    get() =
        when (this) {
            ControlId.A -> Sprite.A
            ControlId.B -> Sprite.B
            ControlId.Start -> Sprite.Start
            ControlId.L, ControlId.R, ControlId.Z -> Sprite.Shoulder
            ControlId.CUp, ControlId.CDown, ControlId.CLeft, ControlId.CRight -> Sprite.C
            ControlId.Stick -> Sprite.Socket
            ControlId.Dpad -> Sprite.Dpad
        }

internal val ControlId.legend: String
    get() =
        when (this) {
            ControlId.CUp -> "▲"
            ControlId.CDown -> "▼"
            ControlId.CLeft -> "◀"
            ControlId.CRight -> "▶"
            ControlId.Start -> "START"
            ControlId.Stick, ControlId.Dpad -> ""
            else -> label
        }

internal fun DrawScope.drawSprite(
    artwork: ControlArtwork,
    sprite: Sprite,
    centreX: Float,
    centreY: Float,
    width: Float,
    height: Float,
    pressed: Boolean = false,
) {
    val scale = if (pressed) 0.94f else 1f
    // Keep fractional destination bounds, as in the Android Canvas implementation.
    withTransform({
        translate(centreX - width * scale / 2, centreY - height * scale / 2)
        scale(width * scale / sprite.size.width, height * scale / sprite.size.height, Offset.Zero)
    }) {
        drawImage(
            artwork.atlas,
            srcOffset = sprite.offset,
            srcSize = sprite.size,
            dstSize = sprite.size,
            alpha = if (pressed) 210f / 255f else 1f,
            filterQuality = FilterQuality.Low,
        )
    }
}
