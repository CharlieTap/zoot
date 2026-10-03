@file:OptIn(ExperimentalTestApi::class)

package com.tap.crashreporting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashDialogTest {
    @Test
    fun showsReportActionsWithDetailsCollapsed() =
        runComposeUiTest {
            setContent { Fixture { Dialog() } }

            onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasText("The game stopped unexpectedly"),
            ).assertIsDisplayed()
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "The game stopped unexpectedly")).assertExists()
            onNode(hasText("Report on GitHub") and hasClickAction()).assertIsDisplayed()
            onNode(hasText("Restart game") and hasClickAction()).assertIsDisplayed()
            onNodeWithText("Restarting loses unsaved progress.").assertIsDisplayed()
            onNodeWithText(Report.details).assertDoesNotExist()
            saveCapture("collapsed", onNodeWithTag("fixture").captureToImage())

            onNodeWithText("View details").performClick()
            onNodeWithText(Report.details).assertIsDisplayed()
            saveCapture("expanded", onNodeWithTag("fixture").captureToImage())
        }

    @Test
    fun reportingOpensTheDraftWithoutTouchingTheClipboard() =
        runComposeUiTest {
            val platform = FakePlatform()
            setContent { Fixture { Dialog(actions = platform.actions) } }

            onNodeWithText("Report on GitHub").performClick()

            runOnIdle {
                assertEquals(1, platform.opened.size)
                assertTrue(platform.opened.single().startsWith("https://github.com/owner/game/issues/new?title=Crash"))
                assertEquals(emptyList(), platform.copied)
            }
        }

    @Test
    fun longReportIsCopiedBeforeOpeningAShortDraft() =
        runComposeUiTest {
            val platform = FakePlatform()
            val report = Report.copy(markdown = "x".repeat(10_000))
            setContent { Fixture { Dialog(report, platform.actions) } }

            onNodeWithText("Copy report and open GitHub").performClick()

            runOnIdle {
                assertEquals(listOf(report.markdown), platform.copied)
                assertEquals(1, platform.opened.size)
                assertTrue(platform.opened.single().length < 1_000)
            }
            onNodeWithText("Report copied. Paste it into the issue on GitHub.").assertExists()
        }

    @Test
    fun failedCopyDoesNotOpenGitHubAndShowsTheReport() =
        runComposeUiTest {
            val platform = FakePlatform(copyFails = true)
            val report = Report.copy(markdown = "x".repeat(10_000))
            setContent { Fixture { Dialog(report, platform.actions) } }

            onNodeWithText("Copy report and open GitHub").performClick()

            runOnIdle { assertEquals(emptyList(), platform.opened) }
            onNodeWithText("Couldn't copy the report. You can select it in the details below.").assertExists()
            onNodeWithText(Report.details).assertExists()
        }

    @Test
    fun failedBrowserKeepsTheReportAvailable() =
        runComposeUiTest {
            val platform = FakePlatform(openFails = true)
            var recovered = false
            setContent { Fixture { Dialog(actions = platform.actions, onRecover = { recovered = true }) } }

            onNodeWithText("Report on GitHub").performClick()

            onNodeWithText("Couldn't open GitHub. You can copy the report instead.").assertExists()
            onNodeWithText(Report.details).assertExists()
            onNodeWithText("Copy report").performScrollTo().performClick()
            runOnIdle {
                assertEquals(listOf(Report.markdown), platform.copied)
                assertTrue(!recovered)
            }
            onNodeWithText("Report copied.").assertExists()
        }

    @Test
    fun recoveryInvokesTheCallback() =
        runComposeUiTest {
            var recovered = 0
            setContent { Fixture { Dialog(onRecover = { recovered++ }) } }

            onNodeWithText("Restart game").performClick()

            runOnIdle { assertEquals(1, recovered) }
        }

    @Test
    fun tappingOutsideDoesNotDismiss() =
        runComposeUiTest {
            var recovered = 0
            setContent { Fixture { Dialog(onRecover = { recovered++ }) } }

            onNodeWithTag("fixture").performTouchInput { click(topLeft) }

            onNodeWithTag("crash-dialog").assertExists()
            runOnIdle { assertEquals(0, recovered) }
        }

    @Test
    fun largeTextKeepsActionsReachable() =
        runComposeUiTest {
            setContent { Fixture(fontScale = 2f) { Dialog() } }

            onNodeWithText("View details").performScrollTo().performClick()
            onNodeWithText("Copy report").performScrollTo().assertIsDisplayed()
            onNodeWithText("Restart game").performScrollTo().assertIsDisplayed()
            onNodeWithText("Report on GitHub").performScrollTo().assertIsDisplayed()
            saveCapture("large-text", onNodeWithTag("fixture").captureToImage())
        }

    @Composable
    private fun Dialog(
        report: CrashReport = Report,
        actions: CrashReportActions = FakePlatform().actions,
        onRecover: () -> Unit = {},
    ) = CrashDialog(
        report = report,
        config = Config,
        heading = "The game stopped unexpectedly",
        message = "Please report this to help us find and fix the problem.",
        recoveryLabel = "Restart game",
        onRecover = onRecover,
        style = Style,
        actions = actions,
        recoveryNote = "Restarting loses unsaved progress.",
    )

    @Composable
    private fun Fixture(
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            Box(Modifier.size(900.dp, 420.dp).background(Color(0xff121619)).testTag("fixture")) { content() }
        }
    }

    private class FakePlatform(
        copyFails: Boolean = false,
        openFails: Boolean = false,
    ) {
        val opened = mutableListOf<String>()
        val copied = mutableListOf<String>()
        val actions =
            CrashReportActions(
                openUri = { url ->
                    check(!openFails) { "No browser" }
                    opened += url
                },
                copyText = { text ->
                    check(!copyFails) { "No clipboard" }
                    copied += text
                },
            )
    }

    private companion object {
        val Config = CrashReportingConfig("game", "https://github.com/owner/game")
        val Report =
            CrashReport(
                title = "Crash: memory out of bounds",
                details = "App: game 1.0\n\nmemory out of bounds\n  at func[12] @ 0x1a2b",
                markdown = "**What were you doing when this happened?**",
            )
        val Style =
            CrashDialogStyle(
                panel = Color(0xf5141719),
                border = Color(0xff61696e),
                text = Color(0xfff4f5f5),
                mutedText = Color(0xffb2bec7),
                primary = Color(0xff9cf0cf),
                onPrimary = Color(0xff141719),
                scrim = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp),
                textStyle = TextStyle(fontSize = 13.sp),
                monoStyle = TextStyle(color = Color(0xffb2bec7), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            )
    }
}

internal expect fun saveCapture(
    name: String,
    image: ImageBitmap,
)
