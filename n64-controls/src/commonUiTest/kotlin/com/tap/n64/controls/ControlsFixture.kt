package com.tap.n64.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

@Composable
internal fun ControlsFixture(
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
        Box(Modifier.size(960.dp, 400.dp).background(Color(0xff121619)).testTag("fixture")) { content() }
    }
}

internal expect fun saveCapture(
    name: String,
    image: ImageBitmap,
)
