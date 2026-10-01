@file:OptIn(ExperimentalForeignApi::class)

package com.tap.zoot.runtime.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

actual fun monotonicNanos(): Long = clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()).toLong()
