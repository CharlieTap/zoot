package com.tap.zoot.ui.surface

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tap.zoot.graphics.RendererFactory
import com.tap.zoot.graphics.webgpu.AndroidWebGpuRenderer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class AndroidGameSurface(
    private val renderers: AndroidWebGpuRenderer.Factory,
) : GameSurface {
    @Composable
    override fun Content(
        onRendererFactoryChanged: (RendererFactory?) -> Unit,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val surfaceView = remember(context) { SurfaceView(context) }
        val currentFactoryChanged = rememberUpdatedState(onRendererFactoryChanged)
        DisposableEffect(surfaceView) {
            val callback =
                object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        currentFactoryChanged.value(
                            RendererFactory { upscaler ->
                                renderers.create(holder.surface, width, height, upscaler)
                            },
                        )
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        currentFactoryChanged.value(null)
                    }
                }
            surfaceView.holder.addCallback(callback)
            onDispose {
                surfaceView.holder.removeCallback(callback)
                currentFactoryChanged.value(null)
            }
        }
        AndroidView(factory = { surfaceView }, modifier = modifier)
    }
}
