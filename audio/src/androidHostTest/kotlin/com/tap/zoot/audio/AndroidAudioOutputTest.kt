package com.tap.zoot.audio

import android.media.AudioTrack
import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowAudioTrack
import java.io.ByteArrayOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 31], manifest = Config.NONE, shadows = [RecordingAudioTrack::class])
class AndroidAudioOutputTest {
    @Before
    fun resetTrack() {
        RecordingAudioTrack.writeLimit = Int.MAX_VALUE
        RecordingAudioTrack.byteBudget = Int.MAX_VALUE
    }

    @Test
    fun primesThreeGameTicksBeforePlaying() {
        AndroidAudioOutput().use { output ->
            val bytes = ByteArray(544 * 4)
            repeat(2) {
                for (frames in intArrayOf(544, 528, 528)) {
                    assertEquals(frames, output.submit(bytes, 0, frames, 32_000))
                }
            }
            val track = RecordingAudioTrack.latest
            assertEquals(emptyList(), track.playedAtFrames)
            assertEquals(6400, track.realTrack.bufferSizeInFrames)
            for (frames in intArrayOf(544, 528, 528)) output.submit(bytes, 0, frames, 32_000)
            assertEquals(listOf(4800), track.playedAtFrames)
            assertEquals(if (Build.VERSION.SDK_INT >= 31) 4800 else 0, track.startThreshold)
        }
    }

    @Test
    fun completesPartialWritesInOrderWithoutCopyingTheInput() {
        RecordingAudioTrack.writeLimit = 388
        val bytes = ByteArray(36_032) { it.toByte() }
        AndroidAudioOutput().use { output ->
            assertEquals(9000, output.submit(bytes, 16, 9000, 32_000))
            val track = RecordingAudioTrack.latest
            assertContentEquals(bytes.copyOfRange(16, 36_016), track.received.toByteArray())
            assertEquals(listOf(4800), track.playedAtFrames)
            assertTrue(track.inputs.all { it === bytes })
            assertTrue(track.blockingWrites.none { it })
        }
    }

    @Test
    fun pauseDiscardsOldAudioAndResumeRebuildsTheReserve() {
        AndroidAudioOutput().use { output ->
            val bytes = ByteArray(4800 * 4)
            repeat(3) {
                output.resume()
                output.submit(bytes, 0, 4799, 32_000)
                val track = RecordingAudioTrack.latest
                assertEquals(it, track.playedAtFrames.size)
                output.submit(bytes, 0, 1, 32_000)
                assertEquals(List(it + 1) { 4800 }, track.playedAtFrames)
                output.pause()
                assertEquals(0, track.received.size())
            }
        }
    }

    @Test
    fun retainsUnwrittenSamplesBeforeGuestMemoryIsReused() {
        val bytes = ByteArray(240) { it.toByte() }
        val expected = bytes.copyOfRange(16, 216)
        AndroidAudioOutput().use { output ->
            RecordingAudioTrack.byteBudget = 40
            assertEquals(50, output.submit(bytes, 16, 50, 32_000))
            val track = RecordingAudioTrack.latest
            assertContentEquals(expected.copyOf(40), track.received.toByteArray())
            bytes.fill(42)
            // Partly drain the saved tail, then append more while it is still queued.
            RecordingAudioTrack.byteBudget = 20
            assertEquals(10, output.submit(bytes, 0, 10, 32_000))
            bytes.fill(7)
            RecordingAudioTrack.byteBudget = 100
            assertEquals(45, output.submit(bytes, 0, 45, 32_000))
            bytes.fill(9)
            RecordingAudioTrack.byteBudget = Int.MAX_VALUE
            assertEquals(1, output.submit(bytes, 0, 1, 32_000))
            assertContentEquals(expected + ByteArray(40) { 42 } + ByteArray(180) { 7 } + ByteArray(4) { 9 }, track.received.toByteArray())
            assertTrue(track.blockingWrites.none { it })
        }
    }

    @Test
    fun pauseAlsoDiscardsPendingSamples() {
        AndroidAudioOutput().use { output ->
            RecordingAudioTrack.byteBudget = 0
            assertEquals(4, output.submit(ByteArray(16) { 1 }, 0, 4, 32_000))
            output.pause()
            output.resume()
            RecordingAudioTrack.byteBudget = Int.MAX_VALUE
            output.submit(ByteArray(16) { 2 }, 0, 4, 32_000)
            assertContentEquals(ByteArray(16) { 2 }, RecordingAudioTrack.latest.received.toByteArray())
        }
    }

    @Test
    fun reportsFailedWrites() {
        RecordingAudioTrack.writeLimit = AudioTrack.ERROR_DEAD_OBJECT
        AndroidAudioOutput().use { output ->
            assertFailsWith<IllegalStateException> {
                output.submit(ByteArray(16), 0, 4, 32_000)
            }
        }
    }
}

@Implements(AudioTrack::class)
class RecordingAudioTrack : ShadowAudioTrack() {
    @RealObject
    lateinit var realTrack: AudioTrack
    val received = ByteArrayOutputStream()
    val playedAtFrames = mutableListOf<Int>()
    val inputs = mutableListOf<ByteArray>()
    val blockingWrites = mutableListOf<Boolean>()
    var startThreshold = 0

    @Implementation
    override fun native_write_byte(
        audioData: ByteArray,
        offsetInBytes: Int,
        sizeInBytes: Int,
        format: Int,
        isBlocking: Boolean,
    ): Int {
        latest = this
        if (realTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
            assertTrue(received.size() + sizeInBytes <= realTrack.bufferSizeInFrames * 4)
        }
        val count = minOf(sizeInBytes, writeLimit, byteBudget)
        if (count <= 0) return count
        byteBudget -= count
        inputs.add(audioData)
        blockingWrites.add(isBlocking)
        received.write(audioData, offsetInBytes, count)
        return super.native_write_byte(audioData, offsetInBytes, count, format, isBlocking)
    }

    @Implementation
    override fun play() {
        playedAtFrames.add(received.size() / 4)
        super.play()
    }

    @Implementation
    override fun flush() {
        received.reset()
        super.flush()
    }

    @Implementation(minSdk = 31)
    fun setStartThresholdInFrames(frames: Int): Int {
        startThreshold = frames
        return frames
    }

    companion object {
        lateinit var latest: RecordingAudioTrack
        var writeLimit = Int.MAX_VALUE
        var byteBudget = Int.MAX_VALUE
    }
}
