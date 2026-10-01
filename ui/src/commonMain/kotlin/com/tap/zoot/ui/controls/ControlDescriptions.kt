package com.tap.zoot.ui.controls

import com.tap.n64.controls.ControlId

internal fun ootControlDescription(id: ControlId): String =
    when (id) {
        ControlId.A -> "A · Action"
        ControlId.B -> "B · Sword"
        ControlId.Start -> "Start · Pause"
        ControlId.L -> "L · Map"
        ControlId.R -> "R · Shield"
        ControlId.Z -> "Z · Target"
        ControlId.CUp -> "C up · Look / Navi"
        ControlId.CDown -> "C down · Item"
        ControlId.CLeft -> "C left · Item"
        ControlId.CRight -> "C right · Item"
        ControlId.Stick -> "Analog control stick · Move and aim"
        ControlId.Dpad -> "Directional pad"
    }
