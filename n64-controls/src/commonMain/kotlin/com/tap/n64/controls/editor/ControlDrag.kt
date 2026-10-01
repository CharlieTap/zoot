package com.tap.n64.controls.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import com.tap.n64.controls.ControlId

internal class ControlDrag(
    val id: ControlId,
    val origin: IntRect,
    val pointerDown: Offset,
    val snapX: FloatArray,
    val snapY: FloatArray,
) {
    var bounds by mutableStateOf(origin)
    var moved by mutableStateOf(false)
    var guideX by mutableFloatStateOf(Float.NaN)
    var guideY by mutableFloatStateOf(Float.NaN)
}
