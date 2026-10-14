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
    override var readbacks = 0
    override var readbackNanos = 0L
    override var pipelinesCreated = 0
    override var pipelineNanos = 0L
    override var bindGroupsCreated = 0
    override var passes = 0
    override var textureUploads = 0

    fun reset() {
        acquireNanos = 0
        bufferUploadNanos = 0
        finishNanos = 0
        submitNanos = 0
        presentNanos = 0
        stagingNanos = 0
        draws = 0
        uploadBytes = 0
        readbacks = 0
        readbackNanos = 0
        pipelinesCreated = 0
        pipelineNanos = 0
        bindGroupsCreated = 0
        passes = 0
        textureUploads = 0
    }
}
