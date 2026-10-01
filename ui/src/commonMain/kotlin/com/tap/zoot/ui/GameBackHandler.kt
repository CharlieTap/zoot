package com.tap.zoot.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun GameBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
