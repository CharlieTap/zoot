package com.tap.zoot.graphics

/** Detailed per-frame counters exposed only to the opt-in benchmark session. */
interface RendererFrameMetrics {
    val acquireNanos: Long
    val bufferUploadNanos: Long
    val finishNanos: Long
    val submitNanos: Long
    val presentNanos: Long
    val stagingNanos: Long
    val draws: Int
    val uploadBytes: Long
}
