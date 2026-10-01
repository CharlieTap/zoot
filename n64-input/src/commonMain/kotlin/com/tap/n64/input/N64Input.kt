package com.tap.n64.input

import kotlinx.atomicfu.atomic

/** Receives controller changes from touch controls or platform input devices. */
interface N64InputSink {
    fun pressButtons(buttonMask: Int)

    fun releaseButtons(buttonMask: Int)

    fun setTouchStick(
        x: Int,
        y: Int,
    )

    fun releaseTouchStick()

    fun setHardwareStick(
        x: Int,
        y: Int,
    )

    fun releaseAll()
}

/** Supplies one complete controller sample to the game for each tick. */
interface N64InputSource {
    fun poll(): ControllerState
}

/** Updated on the UI thread; the game reads one complete controller snapshot per tick. */
class N64Input :
    N64InputSink,
    N64InputSource {
    private val published = atomic(0L)
    private val holds = IntArray(16)
    private var buttons = 0
    private var touchActive = false
    private var touchX = 0
    private var touchY = 0
    private var hardwareX = 0
    private var hardwareY = 0

    val state: ControllerState
        get() = ControllerState(published.value.toInt())

    // Low 32 bits: current buttons and stick. High 16 bits: presses since the last tick.
    // Preserve a tap even if down and up both arrived between game ticks.
    override fun poll(): ControllerState {
        while (true) {
            val snapshot = published.value
            if (published.compareAndSet(snapshot, snapshot and 0xffffffffL)) {
                return ControllerState(snapshot.toInt() or (snapshot ushr 32).toInt())
            }
        }
    }

    // A touch and a keyboard key can hold the same button independently.
    override fun pressButtons(buttonMask: Int) {
        val pressed = buttonMask and buttons.inv()
        for (bit in 0..15) {
            if (buttonMask and (1 shl bit) != 0 && holds[bit]++ == 0) buttons = buttons or (1 shl bit)
        }
        publish(pressed)
    }

    override fun releaseButtons(buttonMask: Int) {
        for (bit in 0..15) {
            if (buttonMask and (1 shl bit) != 0 && holds[bit] > 0 && --holds[bit] == 0) {
                buttons = buttons and (1 shl bit).inv()
            }
        }
        publish()
    }

    override fun setTouchStick(
        x: Int,
        y: Int,
    ) {
        touchX = x
        touchY = y
        touchActive = true
        publish()
    }

    override fun releaseTouchStick() {
        touchX = 0
        touchY = 0
        touchActive = false
        publish()
    }

    override fun setHardwareStick(
        x: Int,
        y: Int,
    ) {
        hardwareX = x
        hardwareY = y
        publish()
    }

    override fun releaseAll() {
        holds.fill(0)
        buttons = 0
        touchActive = false
        touchX = 0
        touchY = 0
        hardwareX = 0
        hardwareY = 0
        published.value = 0
    }

    private fun publish(pressed: Int = 0) {
        val current =
            if (touchActive) {
                ControllerState.of(buttons, touchX, touchY)
            } else {
                ControllerState.of(buttons, hardwareX, hardwareY)
            }.packed.toLong() and 0xffffffffL
        while (true) {
            val previous = published.value
            val pending = (previous ushr 32).toInt() or pressed
            if (published.compareAndSet(previous, current or (pending.toLong() shl 32))) return
        }
    }
}
