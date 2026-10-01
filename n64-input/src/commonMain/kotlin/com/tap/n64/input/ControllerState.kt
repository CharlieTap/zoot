package com.tap.n64.input

import kotlin.jvm.JvmInline

/** One packed N64 controller sample read by the game at the start of a tick. */
@JvmInline
value class ControllerState(
    val packed: Int,
) {
    val buttons: Int
        get() = packed and 0xffff

    val stickX: Int
        get() = (packed shr 16).toByte().toInt()

    val stickY: Int
        get() = (packed shr 24).toByte().toInt()

    companion object {
        val Neutral = ControllerState(0)

        fun of(
            buttons: Int,
            stickX: Int,
            stickY: Int,
        ): ControllerState = ControllerState(buttons or ((stickX and 0xff) shl 16) or ((stickY and 0xff) shl 24))
    }
}
