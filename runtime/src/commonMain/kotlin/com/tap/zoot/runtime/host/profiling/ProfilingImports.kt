package com.tap.zoot.runtime.host.profiling

import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.HostImportGroup
import com.tap.zoot.runtime.host.functionImport
import com.tap.zoot.runtime.platform.monotonicNanos
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.readI32

/** Fast3D timing markers, imported only by profiling guests. */
@Inject
internal class ProfilingImports(
    private val timings: HostTimings,
) : HostImportGroup {
    private var fast3dStart = 0L

    override val imports: List<CodegenImport> =
        listOf(
            functionImport(
                "oot_profile",
                "fast3d",
                parameterTypes = { i32() },
                function =
                    HostFunction { parameters, _ ->
                        if (parameters.readI32(0) == PHASE_START) {
                            fast3dStart = monotonicNanos()
                        } else {
                            timings.fast3dNanos += monotonicNanos() - fast3dStart
                        }
                    },
            ),
        )

    private companion object {
        const val PHASE_START = 0
    }
}
