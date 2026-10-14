package com.tap.zoot.graphics.webgpu

import android.app.Application
import android.content.pm.ApplicationInfo
import dev.zacsweers.metro.Inject
import java.io.File

/** Build-dependent Dawn behaviour. Debuggable builds keep validation so guest mistakes surface as errors. */
@Inject
class WebGpuSettings(
    application: Application,
) {
    val validation = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pipelineHistory = File(application.noBackupFilesDir, "webgpu-pipeline-history")

    val toggles: Array<String> =
        if (validation) arrayOf(MONOLITHIC_PIPELINE_CACHE) else arrayOf(MONOLITHIC_PIPELINE_CACHE, SKIP_VALIDATION, DISABLE_ROBUSTNESS)

    private companion object {
        const val MONOLITHIC_PIPELINE_CACHE = "vulkan_monolithic_pipeline_cache"
        const val SKIP_VALIDATION = "skip_validation"
        const val DISABLE_ROBUSTNESS = "disable_robustness"
    }
}
