package com.tap.zoot.runtime.platform

actual fun monotonicNanos(): Long = System.nanoTime()
