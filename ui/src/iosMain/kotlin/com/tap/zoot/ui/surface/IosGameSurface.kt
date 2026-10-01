package com.tap.zoot.ui.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.graphics.webgpu.IosWebGpuRenderer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Inject
@ContributesBinding(AppScope::class)
class IosGameSurface(
    private val renderers: IosWebGpuRenderer.Factory,
) : GameSurface {
    @Composable
    override fun Content(
        onRendererFactoryChanged: (RendererFactory?) -> Unit,
        modifier: Modifier,
    ) {
        val currentFactoryChanged = rememberUpdatedState(onRendererFactoryChanged)
        val view =
            remember {
                MetalGameView { surface ->
                    currentFactoryChanged.value(
                        RendererFactory { upscaler ->
                            runBlocking(Dispatchers.Main.immediate) {
                                renderers.create(surface.layer, surface.width, surface.height, upscaler)
                            }
                        },
                    )
                }
            }
        DisposableEffect(view) {
            onDispose { currentFactoryChanged.value(null) }
        }
        UIKitView(
            factory = { view },
            modifier = modifier,
            properties = UIKitInteropProperties(interactionMode = null),
        )
    }
}
