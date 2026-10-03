package com.tap.crashreporting

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle

@Immutable
data class CrashDialogStyle(
    val panel: Color,
    val border: Color,
    val text: Color,
    val mutedText: Color,
    val primary: Color,
    val onPrimary: Color,
    val scrim: Color,
    val shape: Shape,
    val textStyle: TextStyle,
    val monoStyle: TextStyle,
)
