@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AudioToolbox.AudioQueueAllocateBuffer
import platform.AudioToolbox.AudioQueueBufferRef
import platform.AudioToolbox.AudioQueueBufferRefVar
import platform.AudioToolbox.AudioQueueRef
import platform.Foundation.NSLock

internal class AudioQueueBufferPool(
    private val capacity: Int,
) {
    // The queue callback must never wait for controlLock: stop/dispose may wait for callbacks.
    private val lock = NSLock()
    private val buffers = ArrayList<AudioQueueBufferRef>(capacity)
    private val available = ArrayList<AudioQueueBufferRef>(capacity)

    fun allocate(
        queue: AudioQueueRef,
        bufferBytes: UInt,
    ) = memScoped {
        repeat(capacity) {
            val buffer = alloc<AudioQueueBufferRefVar>()
            check(AudioQueueAllocateBuffer(queue, bufferBytes, buffer.ptr) == 0)
            buffers.add(checkNotNull(buffer.value))
        }
        available.addAll(buffers)
    }

    fun take(): AudioQueueBufferRef? = locked { available.removeLastOrNull() }

    fun recycle(buffer: AudioQueueBufferRef) = locked { available.add(buffer) }

    fun reclaimAll() =
        locked {
            available.clear()
            available.addAll(buffers)
        }

    fun forget() {
        available.clear()
        buffers.clear()
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
