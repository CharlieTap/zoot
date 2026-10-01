package com.tap.zoot

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.tap.zoot.benchmark.AndroidBenchmarkSession
import com.tap.zoot.input.AndroidHardwareInput
import com.tap.zoot.launch.LaunchOptions
import com.tap.zoot.runtime.controller.GameState
import com.tap.zoot.ui.GameScreen
import com.tap.zoot.ui.GameScreenDependencies
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey
@Inject
class MainActivity(
    private val viewModelFactory: AppViewModelFactory,
    private val dependencies: GameScreenDependencies,
    private val hardwareInput: AndroidHardwareInput,
) : ComponentActivity() {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory

    private val controller by viewModels<GameViewModel>()
    private var resumed by mutableStateOf(false)
    private var windowFocused by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLaunchOptions(LaunchOptions.from(intent))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GameScreen(
                controller = controller,
                dependencies = dependencies,
                active = resumed && windowFocused,
            )
        }
    }

    private fun applyLaunchOptions(options: LaunchOptions) {
        controller.useBenchmark(options.benchmark?.let(::AndroidBenchmarkSession))
        val upscaler = options.upscaler ?: return
        dependencies.settings.commit { it.copy(display = it.display.copy(upscaler = dependencies.upscalers.resolve(upscaler))) }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = dispatchGameKey(keyCode, pressed = true) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = dispatchGameKey(keyCode, pressed = false) || super.onKeyUp(keyCode, event)

    private fun dispatchGameKey(
        keyCode: Int,
        pressed: Boolean,
    ): Boolean {
        if (!hardwareInput.handles(keyCode) || controller.state.value != GameState.Running) return false
        hardwareInput.dispatch(keyCode, pressed)
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        if (!hasFocus) hardwareInput.reset()
    }

    override fun onPause() {
        resumed = false
        hardwareInput.reset()
        dependencies.settings.persist()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }
}
