package com.tap.zoot.ui

import androidx.compose.runtime.Composable

// iOS has no system Back button; the overlays provide Close and Cancel.
@Composable
internal actual fun GameBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
