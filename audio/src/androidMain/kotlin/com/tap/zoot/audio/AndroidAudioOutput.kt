package com.tap.zoot.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

class AndroidAudioOutput : AudioOutput {
    private var track: AudioTrack? = null
    private var started = false
    private var primedFrames = 0
    private val pending = PendingPcm()

    override var volume = 1f
        set(value) {
            field = value
            track?.setVolume(value)
        }

    override fun submit(
        pcm: ByteArray,
        offset: Int,
        frameCount: Int,
        sampleRate: Int,
    ): Int {
        val output = track ?: createTrack(sampleRate).also { track = it }
        val prebufferFrames = sampleRate * PREBUFFER_MILLIS / 1000
        while (!pending.isEmpty) {
            val count = write(output, pending.bytes, pending.offset, pending.size, prebufferFrames)
            if (count == 0) {
                pending.append(pcm, offset, frameCount * BYTES_PER_FRAME)
                return frameCount
            }
            pending.consume(count)
        }
        var written = 0
        val size = frameCount * BYTES_PER_FRAME
        while (written < size) {
            val count = write(output, pcm, offset + written, size - written, prebufferFrames)
            if (count == 0) {
                pending.append(pcm, offset + written, size - written)
                break
            }
            written += count
        }
        return frameCount
    }

    private fun createTrack(sampleRate: Int): AudioTrack =
        AudioTrack
            .Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
            .setAudioFormat(
                AudioFormat
                    .Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            ).setBufferSizeInBytes(
                maxOf(
                    sampleRate * BUFFER_MILLIS / 1000 * BYTES_PER_FRAME,
                    AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                ),
            ).setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                it.setVolume(volume)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    it.setStartThresholdInFrames(sampleRate * PREBUFFER_MILLIS / 1000)
                }
            }

    private fun write(
        output: AudioTrack,
        bytes: ByteArray,
        offset: Int,
        size: Int,
        prebufferFrames: Int,
    ): Int {
        val remaining = if (started) size else minOf(size, (prebufferFrames - primedFrames) * BYTES_PER_FRAME)
        val count = output.write(bytes, offset, remaining, AudioTrack.WRITE_NON_BLOCKING)
        check(count >= 0) { "AudioTrack write failed: $count" }
        if (!started) {
            primedFrames += count / BYTES_PER_FRAME
            if (primedFrames >= prebufferFrames) {
                output.play()
                started = true
            }
        }
        return count
    }

    override fun pause() {
        track?.pause()
        track?.flush()
        started = false
        primedFrames = 0
        pending.clear()
    }

    // The next submissions rebuild the reserve before playback resumes.
    override fun resume() = Unit

    override fun close() {
        track?.release()
        track = null
        started = false
        primedFrames = 0
        pending.release()
    }

    private companion object {
        const val BYTES_PER_FRAME = 4
        const val PREBUFFER_MILLIS = 150
        const val BUFFER_MILLIS = 200
    }
}
