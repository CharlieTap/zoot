package com.tap.n64.controls.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tap.n64.controls.resources.Res
import com.tap.n64.controls.resources.control_bold
import com.tap.n64.controls.resources.control_medium
import com.tap.n64.controls.resources.control_symbols
import com.tap.n64.controls.resources.n64_controls
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.imageResource

internal class ControlArtwork(
    val atlas: ImageBitmap,
    val labels: FontFamily,
    val symbols: FontFamily,
    val editorLabels: FontFamily,
)

@Composable
internal fun rememberControlArtwork(): ControlArtwork {
    val atlas = imageResource(Res.drawable.n64_controls)
    val bold = Font(Res.font.control_bold, FontWeight.Bold)
    val medium = Font(Res.font.control_medium, FontWeight.Medium)
    val symbols = Font(Res.font.control_symbols)
    return remember(atlas, bold, medium, symbols) {
        ControlArtwork(atlas, FontFamily(bold), FontFamily(symbols), FontFamily(medium))
    }
}
