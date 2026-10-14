package com.tap.zoot.runtime.host.graphics

import com.tap.zoot.graphics.Renderer
import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.HostImportGroup
import com.tap.zoot.runtime.host.functionImport
import com.tap.zoot.runtime.host.i32
import com.tap.zoot.runtime.platform.monotonicNanos
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.readUtf8String
import io.github.charlietap.chasm.host.withMemory

/** Direct guest graphics callbacks. */
@Inject
internal class GraphicsImports(
    private val renderer: Renderer,
    private val timings: HostTimings,
) : HostImportGroup {
    override val imports: List<CodegenImport> =
        listOf(
            functionImport(
                GPU,
                "shader",
                parameterTypes = { i32(6) },
                function =
                    HostFunction { parameters, _ ->
                        withMemory(0) {
                            measured {
                                val sourcePointer = parameters.readI32(1)
                                val sourceLength = parameters.readI32(2)
                                val attributesPointer = parameters.readI32(4)
                                val attributeCount = parameters.readI32(5)
                                renderer.createShader(
                                    shaderId = parameters.readI32(0),
                                    wgsl = readUtf8String(sourcePointer, sourceLength),
                                    vertexStride = parameters.readI32(3),
                                    attributeSizes = IntArray(attributeCount) { readI32(attributesPointer + it * 4) },
                                )
                            }
                        }
                    },
            ),
            functionImport(
                GPU,
                "texture",
                parameterTypes = { i32(4) },
                function =
                    HostFunction { parameters, _ ->
                        withMemory(0) {
                            measured {
                                renderer.uploadTexture(
                                    textureId = parameters.readI32(0),
                                    memory = this,
                                    pixels = parameters.readI32(1),
                                    width = parameters.readI32(2),
                                    height = parameters.readI32(3),
                                )
                            }
                        }
                    },
            ),
            functionImport(
                GPU,
                "delete_texture",
                parameterTypes = { i32() },
                function = HostFunction { parameters, _ -> renderer.deleteTexture(textureId = parameters.readI32(0)) },
            ),
            functionImport(
                GPU,
                "draw",
                parameterTypes = { i32(4) },
                function =
                    HostFunction { parameters, _ ->
                        withMemory(0) {
                            measured {
                                renderer.draw(
                                    memory = this,
                                    drawState = parameters.readI32(0),
                                    vertices = parameters.readI32(1),
                                    size = parameters.readI32(2),
                                    vertexCount = parameters.readI32(3),
                                )
                            }
                        }
                    },
            ),
            functionImport(
                GPU,
                "frame",
                parameterTypes = { i32() },
                function =
                    HostFunction { parameters, _ ->
                        measured {
                            if (parameters.readI32(0) == FRAME_BEGIN) renderer.beginFrame() else renderer.endFrame()
                        }
                    },
            ),
            functionImport(
                GPU,
                "target",
                parameterTypes = { i32(3) },
                function =
                    HostFunction { parameters, _ ->
                        measured {
                            renderer.resizeTarget(
                                targetId = parameters.readI32(0),
                                width = parameters.readI32(1),
                                height = parameters.readI32(2),
                            )
                        }
                    },
            ),
            functionImport(
                GPU,
                "clear",
                parameterTypes = { i32(3) },
                function =
                    HostFunction { parameters, _ ->
                        measured {
                            renderer.clearTarget(
                                targetId = parameters.readI32(0),
                                clearColour = parameters.readI32(1) != 0,
                                clearDepth = parameters.readI32(2) != 0,
                            )
                        }
                    },
            ),
            functionImport(
                GPU,
                "copy",
                parameterTypes = { i32(2) },
                function =
                    HostFunction { parameters, _ ->
                        measured { renderer.copyTarget(destinationId = parameters.readI32(0), sourceId = parameters.readI32(1)) }
                    },
            ),
            functionImport(
                GPU,
                "read",
                parameterTypes = { i32(4) },
                function =
                    HostFunction { parameters, _ ->
                        withMemory(0) {
                            measured {
                                renderer.readTargetRgba16(
                                    targetId = parameters.readI32(0),
                                    width = parameters.readI32(1),
                                    height = parameters.readI32(2),
                                    memory = this,
                                    destination = parameters.readI32(3),
                                )
                            }
                        }
                    },
            ),
            functionImport(
                GPU,
                "depth_batch",
                parameterTypes = { i32(4) },
                function =
                    HostFunction { parameters, _ ->
                        withMemory(0) {
                            measured {
                                renderer.readDepths(
                                    targetId = parameters.readI32(0),
                                    memory = this,
                                    coordinates = parameters.readI32(1),
                                    count = parameters.readI32(2),
                                    destination = parameters.readI32(3),
                                )
                            }
                        }
                    },
            ),
        )

    private inline fun measured(block: () -> Unit) {
        val start = monotonicNanos()
        block()
        timings.graphicsNanos += monotonicNanos() - start
    }

    private companion object {
        const val GPU = "oot_gpu"
        const val FRAME_BEGIN = 0
    }
}
