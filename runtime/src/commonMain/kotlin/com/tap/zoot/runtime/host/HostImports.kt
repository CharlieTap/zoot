package com.tap.zoot.runtime.host

import com.tap.zoot.runtime.host.audio.AudioImports
import com.tap.zoot.runtime.host.graphics.DepthQueryImports
import com.tap.zoot.runtime.host.graphics.GraphicsImports
import com.tap.zoot.runtime.host.profiling.ProfilingImports
import com.tap.zoot.runtime.host.resources.ResourceImports
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport

@Inject
internal class HostImports(
    graphics: GraphicsImports,
    depthQueries: DepthQueryImports,
    profiling: ProfilingImports,
    audio: AudioImports,
    resources: ResourceImports,
) {
    val all: List<CodegenImport> = graphics.imports + depthQueries.imports + profiling.imports + audio.imports + resources.imports
}
