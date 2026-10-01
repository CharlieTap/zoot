package com.tap.zoot.graphics.webgpu

import com.tap.zoot.graphics.RendererFrameMetrics

internal class FrameCounters : RendererFrameMetrics {
    override var acquireNanos = 0L
    override var bufferUploadNanos = 0L
    override var finishNanos = 0L
    override var submitNanos = 0L
    override var presentNanos = 0L
    override var stagingNanos = 0L
    override var draws = 0
    override var uploadBytes = 0L

    fun reset() {
        acquireNanos = 0
        bufferUploadNanos = 0
        finishNanos = 0
        submitNanos = 0
        presentNanos = 0
        stagingNanos = 0
        draws = 0
        uploadBytes = 0
    }
}
