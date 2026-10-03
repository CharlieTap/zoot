package com.tap.crashreporting

import io.github.charlietap.chasm.config.RuntimeConfig
import io.github.charlietap.chasm.embedding.error.WasmTrapException
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.NumberValue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChasmCrashReportTest {
    @Test
    fun boundsTrapIncludesChasmTraceAndEnvironment() {
        val failure = trap("load", 70_000)
        val report = CrashReport(failure, Environment, Config)

        assertEquals("Crash: memory out of bounds", report.title)
        assertTrue(report.details.endsWith(failure.trap.toString()))
        assertContains(report.details, "read 4 bytes at 0x")
        assertContains(report.details, "App: game 1.2.3")
        assertContains(report.details, "Chasm: 2.2.0-SNAPSHOT")
        assertContains(report.details, "Wasm SHA-256: abc123")
        assertContains(report.details, "Platform: Android 15")
        assertContains(report.details, "Device: Pixel")
    }

    @Test
    fun deepTraceKeepsChasmOmissionMarker() {
        val failure = trap("recurse", 300)
        val report = CrashReport(failure, Environment, Config)

        assertEquals("Crash: unreachable executed", report.title)
        assertTrue(failure.trap.omittedFrames > 0)
        assertContains(report.details, "frames omitted")
        assertTrue(report.details.endsWith(failure.trap.toString()))
    }

    @Test
    fun trapIsFoundThroughWrappingExceptions() {
        val failure = IllegalStateException("Game failed", trap("load", 70_000))
        val report = CrashReport(failure, Environment, Config)

        assertEquals("Crash: memory out of bounds", report.title)
    }

    @Test
    fun otherFailuresReportOnlyTheirType() {
        val report = CrashReport(IllegalStateException("/private/path/save.dat"), Environment, Config)

        assertEquals("Crash: IllegalStateException", report.title)
        assertContains(report.details, "IllegalStateException (no Wasm stack trace)")
        assertFalse(report.markdown.contains("/private/path"))
    }

    @Test
    fun missingEnvironmentFieldsAreUnknown() {
        val environment = Environment.copy(wasmSha256 = null, osVersion = null, deviceModel = null)
        val report = CrashReport(IllegalStateException(), environment, Config)

        assertContains(report.details, "Wasm SHA-256: unknown")
        assertContains(report.details, "Platform: Android unknown")
        assertContains(report.details, "Device: unknown")
    }

    @Test
    fun markdownAsksWhatHappenedAndCollapsesDetails() {
        val report = CrashReport(trap("load", 70_000), Environment, Config)

        assertTrue(report.markdown.startsWith("**What were you doing when this happened?**"))
        assertContains(report.markdown, "<details>\n<summary>Technical details</summary>\n\n```text\n${report.details}\n```\n\n</details>")
    }

    private fun trap(
        export: String,
        argument: Int,
    ): WasmTrapException {
        val store = store()
        val module = module(TrappingModule).expect("Decode")
        val instance = instance(store, module, emptyList(), RuntimeConfig(debugInfo = true)).expect("Instantiate")
        return assertFailsWith<WasmTrapException> {
            invoke(store, instance, export, listOf(NumberValue.I32(argument))).expect(export)
        }
    }

    private companion object {
        val Config = CrashReportingConfig(applicationName = "game", githubRepositoryUrl = "https://github.com/owner/game")
        val Environment =
            CrashEnvironment(
                applicationVersion = "1.2.3",
                chasmVersion = "2.2.0-SNAPSHOT",
                wasmSha256 = "abc123",
                platform = "Android",
                osVersion = "15",
                deviceModel = "Pixel",
            )

        /**
         * `load` reads an i32 at its argument from a one page memory. `recurse`
         * calls itself until its argument reaches zero, then traps.
         */
        @Suppress("ktlint:standard:argument-list-wrapping")
        val TrappingModule =
            byteArrayOf(
                0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
                0x01, 0x06, 0x01, 0x60, 0x01, 0x7f, 0x01, 0x7f,
                0x03, 0x03, 0x02, 0x00, 0x00,
                0x05, 0x03, 0x01, 0x00, 0x01,
                0x07, 0x12, 0x02,
                0x04, 0x6c, 0x6f, 0x61, 0x64, 0x00, 0x00,
                0x07, 0x72, 0x65, 0x63, 0x75, 0x72, 0x73, 0x65, 0x00, 0x01,
                0x0a, 0x1a, 0x02,
                0x07, 0x00, 0x20, 0x00, 0x28, 0x02, 0x00, 0x0b,
                0x10, 0x00, 0x20, 0x00, 0x45, 0x04, 0x40, 0x00, 0x0b, 0x20, 0x00, 0x41, 0x01, 0x6b, 0x10, 0x01, 0x0b,
            )
    }
}
