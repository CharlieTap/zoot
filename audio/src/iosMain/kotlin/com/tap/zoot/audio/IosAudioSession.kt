@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tap.zoot.audio

import com.tap.zoot.audio.session.ZootAudioSessionEvent
import com.tap.zoot.audio.session.ZootObserveAudioSession
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter

internal interface IosAudioSession : AutoCloseable {
    fun observe(listener: (ZootAudioSessionEvent) -> Unit)

    fun activate(): Boolean

    fun deactivate()
}

internal class SystemAudioSession : IosAudioSession {
    private val session = AVAudioSession.sharedInstance()
    private var observers: List<*> = emptyList<Any>()

    override fun observe(listener: (ZootAudioSessionEvent) -> Unit) {
        observers = ZootObserveAudioSession(listener) ?: emptyList<Any>()
    }

    override fun activate(): Boolean =
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!session.setCategory(AVAudioSessionCategoryPlayback, error.ptr) || !session.setActive(true, error.ptr)) {
                NSLog("%s", "Zoot audio: activation failed: ${error.value?.localizedDescription}")
                false
            } else {
                true
            }
        }

    override fun deactivate() {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error.ptr)) {
                NSLog("%s", "Zoot audio: deactivation failed: ${error.value?.localizedDescription}")
            }
        }
    }

    override fun close() {
        observers.forEach { observer ->
            if (observer != null) NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
        observers = emptyList<Any>()
    }
}
