package com.tap.zoot.runtime.host.graphics

import com.tap.zoot.graphics.Renderer
import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.HostImportGroup
import com.tap.zoot.runtime.host.functionImport
import com.tap.zoot.runtime.platform.monotonicNanos
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.readF32
import io.github.charlietap.chasm.host.writeI32

@Inject
internal class DepthQueryImports(
    private val renderer: Renderer,
    private val timings: HostTimings,
) : HostImportGroup {
    override val imports: List<CodegenImport> =
        listOf(
            functionImport(
                MODULE,
                "prepare_depth_query",
                parameterTypes = {
                    f32()
                    f32()
                },
                function = HostFunction { _, _ -> },
            ),
            functionImport(
                MODULE,
                "read_depth_query",
                parameterTypes = {
                    f32()
                    f32()
                },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        val start = monotonicNanos()
                        results.writeI32(0, renderer.readDepth(SCREEN_TARGET, parameters.readF32(0), parameters.readF32(1)))
                        timings.graphicsNanos += monotonicNanos() - start
                    },
            ),
        )

    private companion object {
        const val MODULE = "oot_graphics"
        const val SCREEN_TARGET = 0
    }
}
