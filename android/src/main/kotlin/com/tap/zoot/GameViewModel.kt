package com.tap.zoot

import androidx.lifecycle.ViewModel
import com.tap.zoot.runtime.controller.DefaultGameController
import com.tap.zoot.runtime.controller.GameController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class GameViewModel(
    controller: DefaultGameController,
) : ViewModel(),
    GameController by controller {
    init {
        addCloseable(controller)
    }
}
