@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.audio

import com.tap.zoot.audio.session.ZootAudioSessionEvent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosAudioOutputTest {
    @Test
    fun startsPlaybackAndReturnsBuffersBeforeAndAfterPause() {
        val output = IosAudioOutput()
        output.volume = 0f
        output.use {
            repeat(3) {
                prime(output)
                awaitPlayback(output)
                // More than the three remaining buffers: this also exercises the return callback.
                pumpEvents(0.2)
                prime(output)
                output.pause()
                assertFalse(output.isRunning)
                output.resume()
                pumpEvents(0.02)
            }
        }
    }

    @Test
    fun recoversFromInterruptionWithoutOpeningSettings() =
        withSession { output, session ->
            prime(output)
            awaitPlayback(output)
            session.send(ZootAudioSessionEvent.ZootAudioSessionInactive)
            assertFalse(output.isRunning)
            assertEquals(0, output.submit(samples, 0, 544, RATE))
            session.send(ZootAudioSessionEvent.ZootAudioSessionResume)
            prime(output)
            awaitPlayback(output)
            assertEquals(2, session.activations)
        }

    @Test
    fun systemRecoveryDoesNotOverrideMenuPause() =
        withSession { output, session ->
            prime(output)
            output.pause()
            session.send(ZootAudioSessionEvent.ZootAudioSessionInactive)
            session.send(ZootAudioSessionEvent.ZootAudioSessionResume)
            session.send(ZootAudioSessionEvent.ZootAudioSessionForeground)
            session.send(ZootAudioSessionEvent.ZootAudioSessionLost)
            session.send(ZootAudioSessionEvent.ZootAudioSessionReset)
            assertEquals(1, session.activations)
            assertEquals(0, output.submit(samples, 0, 544, RATE))
            assertFalse(output.isRunning)
            output.resume()
            prime(output)
            awaitPlayback(output)
        }

    @Test
    fun retriesRejectedActivationOnAnEventNotEverySubmission() =
        withSession { output, session ->
            session.activationAllowed = false
            repeat(20) { assertEquals(0, output.submit(samples, 0, 544, RATE)) }
            assertEquals(1, session.activations)
            session.activationAllowed = true
            session.send(ZootAudioSessionEvent.ZootAudioSessionResume)
            prime(output)
            awaitPlayback(output)
            assertEquals(2, session.activations)
        }

    @Test
    fun foregroundRecoversWhenInterruptionEndIsMissing() =
        withSession { output, session ->
            prime(output)
            session.send(ZootAudioSessionEvent.ZootAudioSessionInactive)
            session.send(ZootAudioSessionEvent.ZootAudioSessionForeground)
            prime(output)
            awaitPlayback(output)
        }

    @Test
    fun mediaServicesResetRecreatesPlayback() =
        withSession { output, session ->
            prime(output)
            session.send(ZootAudioSessionEvent.ZootAudioSessionLost)
            session.send(ZootAudioSessionEvent.ZootAudioSessionResume)
            assertEquals(1, session.activations)
            assertEquals(0, output.submit(samples, 0, 544, RATE))
            session.send(ZootAudioSessionEvent.ZootAudioSessionReset)
            prime(output)
            awaitPlayback(output)
            assertEquals(2, session.activations)
        }

    @Test
    fun lateEventsCannotRestartClosedOutput() =
        withSession { output, session ->
            prime(output)
            output.close()
            session.send(ZootAudioSessionEvent.ZootAudioSessionResume)
            session.send(ZootAudioSessionEvent.ZootAudioSessionReset)
            output.resume()
            assertEquals(1, session.activations)
            assertTrue(session.closed)
            assertFalse(output.isRunning)
            assertEquals(0, output.submit(samples, 0, 544, RATE))
        }

    private fun withSession(block: (IosAudioOutput, ControlledSession) -> Unit) {
        val session = ControlledSession()
        IosAudioOutput(session).use { output ->
            output.volume = 0f
            block(output, session)
        }
    }

    private fun prime(output: IosAudioOutput) {
        // Exactly 150 ms; the former eight-chunk test never reached AudioQueueStart.
        for (frames in intArrayOf(544, 528, 528, 544, 528, 528, 544, 528, 528)) {
            assertEquals(frames, output.submit(samples, 0, frames, RATE))
        }
    }

    private fun awaitPlayback(output: IosAudioOutput) {
        repeat(100) {
            if (output.isRunning) return
            pumpEvents(0.01)
        }
        assertTrue(output.isRunning, "Audio Queue did not start")
    }

    private fun pumpEvents(seconds: Double) {
        NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(seconds))
    }

    private class ControlledSession : IosAudioSession {
        private val system = SystemAudioSession()
        private lateinit var listener: (ZootAudioSessionEvent) -> Unit
        var activations = 0
        var activationAllowed = true
        var closed = false

        override fun observe(listener: (ZootAudioSessionEvent) -> Unit) {
            this.listener = listener
        }

        override fun activate(): Boolean {
            activations++
            return activationAllowed && system.activate()
        }

        override fun deactivate() = system.deactivate()

        override fun close() {
            closed = true
            system.close()
        }

        fun send(event: ZootAudioSessionEvent) = listener(event)
    }

    private companion object {
        const val RATE = 32_000
        val samples = ByteArray(544 * 4)
    }
}
