package com.tap.n64.controls

import com.tap.n64.controls.internal.Anchor
import com.tap.n64.controls.internal.Anchor.Centre
import com.tap.n64.controls.internal.Anchor.FromEnd
import com.tap.n64.controls.internal.Anchor.FromStart
import com.tap.n64.input.N64Button

enum class ControlId(
    val label: String,
    internal val storageKey: String,
    internal val width: Float,
    internal val height: Float,
    internal val anchorX: Anchor,
    internal val anchorY: Anchor,
    internal val button: N64Button?,
) {
    A("A", "A", 78f, 78f, anchorX = FromEnd(72f), anchorY = FromEnd(85f), button = N64Button.A),
    B("B", "B", 68f, 68f, anchorX = FromEnd(143f), anchorY = FromEnd(135f), button = N64Button.B),
    Start("Start", "START", 78f, 48f, anchorX = Centre, anchorY = FromEnd(29f), button = N64Button.Start),
    L("L", "L", 78f, 48f, anchorX = FromStart(52f), anchorY = FromStart(55f), button = N64Button.L),
    R("R", "R", 88f, 48f, anchorX = FromEnd(90f), anchorY = FromStart(55f), button = N64Button.R),
    Z("Z", "Z", 78f, 48f, anchorX = FromStart(142f), anchorY = FromStart(55f), button = N64Button.Z),
    CUp("C up", "C_UP", 50f, 50f, anchorX = FromEnd(92f), anchorY = FromEnd(297f), button = N64Button.CUp),
    CDown("C down", "C_DOWN", 50f, 50f, anchorX = FromEnd(92f), anchorY = FromEnd(207f), button = N64Button.CDown),
    CLeft("C left", "C_LEFT", 50f, 50f, anchorX = FromEnd(137f), anchorY = FromEnd(252f), button = N64Button.CLeft),
    CRight("C right", "C_RIGHT", 50f, 50f, anchorX = FromEnd(47f), anchorY = FromEnd(252f), button = N64Button.CRight),
    Stick("Control stick", "STICK", 156f, 156f, anchorX = FromStart(97f), anchorY = FromEnd(112f), button = null),
    Dpad("Directional pad", "DPAD", 100f, 100f, anchorX = FromStart(97f), anchorY = FromEnd(247f), button = null),
    ;

    internal fun defaultX(surfaceWidth: Float): Float = anchorX.resolve(surfaceWidth)

    internal fun defaultY(surfaceHeight: Float): Float = anchorY.resolve(surfaceHeight)
}
