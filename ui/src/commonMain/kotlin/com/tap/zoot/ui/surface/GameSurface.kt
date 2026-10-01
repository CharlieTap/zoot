package com.tap.zoot.ui.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tap.zoot.graphics.RendererFactory

/** Hosts the platform WebGPU view and reports a factory while its native surface is valid. */
interface GameSurface {
    @Composable
    fun Content(
        onRendererFactoryChanged: (RendererFactory?) -> Unit,
        modifier: Modifier = Modifier,
    )
}
