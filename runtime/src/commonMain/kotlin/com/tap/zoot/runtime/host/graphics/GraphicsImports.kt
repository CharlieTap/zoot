package com.tap.zoot.runtime.host.graphics

import com.tap.zoot.graphics.Renderer
import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.GuestMemoryTransfer
import com.tap.zoot.runtime.host.HostImportGroup
import com.tap.zoot.runtime.host.functionImport
import com.tap.zoot.runtime.host.i32
import com.tap.zoot.runtime.platform.monotonicNanos
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.readF32
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.readUtf8String
import io.github.charlietap.chasm.host.withMemory
import io.github.charlietap.chasm.host.writeI32

/** Direct guest graphics callbacks. */
@Inject
internal class GraphicsImports(
    private val renderer: Renderer,
    private val timings: HostTimings,
) : HostImportGroup {
    private val transfer = GuestMemoryTransfer()

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
                                val textureId = parameters.readI32(0)
                                val pixelsPointer = parameters.readI32(1)
                                val width = parameters.readI32(2)
                                val height = parameters.readI32(3)
                                transfer.read(this, pixelsPointer, width * height * 4) { pixels ->
                                    renderer.uploadTexture(textureId, pixels, 0, width, height)
                                }
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
                                val drawState = parameters.readI32(0)
                                val verticesPointer = parameters.readI32(1)
                                val verticesSize = parameters.readI32(2)
                                val vertexCount = parameters.readI32(3)
                                transfer.read(this, verticesPointer, verticesSize) { vertices ->
                                    renderer.draw(this, drawState, vertices, 0, verticesSize, vertexCount)
                                }
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
                "depth",
                parameterTypes = {
                    i32()
                    f32()
                    f32()
                },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        measured {
                            val depth =
                                renderer.readDepth(
                                    targetId = parameters.readI32(0),
                                    x = parameters.readF32(1),
                                    y = parameters.readF32(2),
                                )
                            results.writeI32(0, depth)
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
