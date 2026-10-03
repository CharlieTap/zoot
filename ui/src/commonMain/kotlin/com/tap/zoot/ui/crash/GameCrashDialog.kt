package com.tap.zoot.ui.crash

import androidx.compose.runtime.Composable
import com.tap.crashreporting.CrashDialog
import com.tap.crashreporting.CrashDialogStyle
import com.tap.crashreporting.CrashReportingConfig
import com.tap.zoot.runtime.controller.GameState
import com.tap.zoot.ui.GameBackHandler
import com.tap.zoot.ui.theme.PanelShape
import com.tap.zoot.ui.theme.ZootColors
import com.tap.zoot.ui.theme.ZootTextStyles

internal val GameCrashDialogStyle =
    CrashDialogStyle(
        panel = ZootColors.Panel,
        border = ZootColors.Border,
        text = ZootColors.Text,
        mutedText = ZootColors.Muted,
        primary = ZootColors.Mint,
        onPrimary = ZootColors.OnMint,
        scrim = ZootColors.Scrim,
        shape = PanelShape,
        textStyle = ZootTextStyles.Label,
        monoStyle = ZootTextStyles.Mono,
    )

@Composable
internal fun GameCrashDialog(
    failure: GameState.Failed,
    config: CrashReportingConfig,
    onRestart: () -> Unit,
) {
    GameBackHandler(enabled = true) {}
    CrashDialog(
        report = failure.report,
        config = config,
        heading = if (failure.duringStartup) "The game couldn't start" else "The game stopped unexpectedly",
        message = "Please report this to help us find and fix the problem.",
        recoveryLabel = "Restart game",
        onRecover = onRestart,
        style = GameCrashDialogStyle,
        recoveryNote = if (failure.duringStartup) null else "Restarting loses unsaved progress.",
    )
}
