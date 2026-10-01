package com.tap.zoot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun GameBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = BackHandler(enabled, onBack)
