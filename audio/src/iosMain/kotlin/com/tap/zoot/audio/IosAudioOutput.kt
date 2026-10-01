@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.audio

import com.tap.zoot.audio.session.ZootAudioSessionEvent
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AudioToolbox.AudioQueueDispose
import platform.AudioToolbox.AudioQueueEnqueueBuffer
import platform.AudioToolbox.AudioQueueGetProperty
import platform.AudioToolbox.AudioQueueNewOutput
import platform.AudioToolbox.AudioQueueRef
import platform.AudioToolbox.AudioQueueRefVar
import platform.AudioToolbox.AudioQueueSetParameter
import platform.AudioToolbox.AudioQueueStart
import platform.AudioToolbox.AudioQueueStop
import platform.AudioToolbox.kAudioQueueParam_Volume
import platform.AudioToolbox.kAudioQueueProperty_IsRunning
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSLog
import platform.Foundation.NSRecursiveLock
import platform.posix.memcpy

class IosAudioOutput internal constructor(
    private val session: IosAudioSession,
) : AudioOutput {
    constructor() : this(SystemAudioSession())

    private var queue: AudioQueueRef? = null
    private val bufferPool = AudioQueueBufferPool(BUFFER_COUNT)
    private var bufferPoolReference: StableRef<AudioQueueBufferPool>? = null
    private var observing = false
    private var playbackWanted = true
    private var sessionActive = false
    private var mediaServicesAvailable = true
    private var closed = false
    private var sampleRate = 0
    private var started = false
    private var primedFrames = 0
    private var outputVolume = 1f

    // Control operations can arrive from the game worker or the system notification thread.
    private val controlLock = NSRecursiveLock()

    override var volume: Float
        get() = locked { outputVolume }
        set(value) {
            locked {
                outputVolume = value
                queue?.let { AudioQueueSetParameter(it, kAudioQueueParam_Volume, value) }
            }
        }

    internal val isRunning: Boolean
        get() =
            locked {
                val output = queue ?: return@locked false
                memScoped {
                    val running = alloc<UIntVar>()
                    val size = alloc<UIntVar> { value = UInt.SIZE_BYTES.toUInt() }
                    AudioQueueGetProperty(output, kAudioQueueProperty_IsRunning, running.ptr, size.ptr) == 0 && running.value != 0u
                }
            }

    override fun submit(
        pcm: ByteArray,
        offset: Int,
        frameCount: Int,
        sampleRate: Int,
    ): Int =
        locked {
            if (closed) return@locked 0
            if (!observing) {
                observing = true
                session.observe(::onSessionEvent)
            }
            if (this.sampleRate == 0) {
                this.sampleRate = sampleRate
                activate()
            }
            if (!playbackWanted || !sessionActive) return@locked 0
            val output = queue ?: createQueue(sampleRate)
            val buffer = bufferPool.take() ?: return@locked 0
            val count = minOf(frameCount * BYTES_PER_FRAME, buffer.pointed.mAudioDataBytesCapacity.toInt())
            pcm.usePinned { memcpy(buffer.pointed.mAudioData, it.addressOf(offset), count.toULong()) }
            buffer.pointed.mAudioDataByteSize = count.toUInt()
            val enqueued = AudioQueueEnqueueBuffer(output, buffer, 0u, null)
            if (enqueued != 0) {
                playbackFailed("enqueue", enqueued)
                return@locked 0
            }
            if (!started) {
                primedFrames += count / BYTES_PER_FRAME
                // The guest supplies 50 ms bursts. Keep a short reserve for uneven frame timing.
                if (primedFrames >= sampleRate * PREBUFFER_MILLIS / 1000) {
                    val status = AudioQueueStart(output, null)
                    if (status == 0) {
                        started = true
                    } else {
                        playbackFailed("start", status)
                        return@locked 0
                    }
                }
            }
            count / BYTES_PER_FRAME
        }

    private fun createQueue(sampleRate: Int): AudioQueueRef =
        memScoped {
            val format =
                alloc<AudioStreamBasicDescription> {
                    mSampleRate = sampleRate.toDouble()
                    mFormatID = kAudioFormatLinearPCM
                    mFormatFlags = kAudioFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked
                    mBytesPerPacket = BYTES_PER_FRAME.toUInt()
                    mFramesPerPacket = 1u
                    mBytesPerFrame = BYTES_PER_FRAME.toUInt()
                    mChannelsPerFrame = 2u
                    mBitsPerChannel = 16u
                }
            val output = alloc<AudioQueueRefVar>()
            val reference = StableRef.create(bufferPool)
            bufferPoolReference = reference
            check(
                AudioQueueNewOutput(
                    format.ptr,
                    staticCFunction { context, _, buffer ->
                        checkNotNull(context).asStableRef<AudioQueueBufferPool>().get().recycle(checkNotNull(buffer))
                    },
                    reference.asCPointer(),
                    null,
                    null,
                    0u,
                    output.ptr,
                ) == 0,
            )
            val created = checkNotNull(output.value)
            queue = created
            bufferPool.allocate(created, BUFFER_BYTES)
            AudioQueueSetParameter(created, kAudioQueueParam_Volume, outputVolume)
            created
        }

    override fun pause() =
        locked {
            if (closed) return@locked
            playbackWanted = false
            stopQueue()
            if (sessionActive) {
                sessionActive = false
                session.deactivate()
            }
        }

    override fun resume() =
        locked {
            if (closed) return@locked
            playbackWanted = true
            activate()
        }

    private fun activate() {
        if (playbackWanted && mediaServicesAvailable && !sessionActive && sampleRate != 0) {
            sessionActive = session.activate()
        }
    }

    private fun onSessionEvent(event: ZootAudioSessionEvent) =
        locked {
            if (closed) return@locked
            when (event) {
                ZootAudioSessionEvent.ZootAudioSessionInactive -> {
                    sessionActive = false
                    stopQueue()
                }

                ZootAudioSessionEvent.ZootAudioSessionResume,
                ZootAudioSessionEvent.ZootAudioSessionForeground,
                -> {
                    activate()
                }

                ZootAudioSessionEvent.ZootAudioSessionLost -> {
                    mediaServicesAvailable = false
                    sessionActive = false
                    stopQueue()
                }

                ZootAudioSessionEvent.ZootAudioSessionReset -> {
                    sessionActive = false
                    disposeQueue()
                    mediaServicesAvailable = true
                    activate()
                }
            }
        }

    private fun playbackFailed(
        operation: String,
        status: Int,
    ) {
        NSLog("%s", "Zoot audio: queue $operation failed: $status")
        sessionActive = false
        stopQueue()
        session.deactivate()
    }

    private fun stopQueue() {
        queue?.let { AudioQueueStop(it, true) }
        started = false
        primedFrames = 0
        bufferPool.reclaimAll()
    }

    override fun close() =
        locked {
            if (closed) return@locked
            closed = true
            session.close()
            disposeQueue()
            if (sessionActive) session.deactivate()
            sessionActive = false
        }

    private fun disposeQueue() {
        queue?.let { AudioQueueDispose(it, true) }
        queue = null
        started = false
        primedFrames = 0
        bufferPoolReference?.dispose()
        bufferPoolReference = null
        bufferPool.forget()
    }

    private inline fun <T> locked(block: () -> T): T {
        controlLock.lock()
        try {
            return block()
        } finally {
            controlLock.unlock()
        }
    }

    private companion object {
        const val BYTES_PER_FRAME = 4
        const val PREBUFFER_MILLIS = 150
        const val BUFFER_BYTES = 8192u

        // Twelve guest chunks hold 200 ms, leaving room while Audio Queue returns used buffers.
        const val BUFFER_COUNT = 12
    }
}
