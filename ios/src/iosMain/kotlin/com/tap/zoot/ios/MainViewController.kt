package com.tap.zoot.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tap.zoot.ui.GameScreen
import dev.zacsweers.metro.createGraph
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** iOS lifecycle shell around the shared application. */
fun mainViewController(): UIViewController {
    val app = createGraph<AppGraph>().app
    return ComposeUIViewController {
        var foreground by remember { mutableStateOf(false) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { foreground = true }
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
            foreground = false
            app.dependencies.settings.persist()
        }
        DisposableEffect(app) {
            UIApplication.sharedApplication.idleTimerDisabled = true
            onDispose {
                UIApplication.sharedApplication.idleTimerDisabled = false
                app.close()
            }
        }
        GameScreen(
            controller = app.controller,
            dependencies = app.dependencies,
            active = foreground,
        )
    }
}
