package com.tap.zoot.runtime.host.resources

import com.tap.zoot.runtime.engine.HostTimings
import com.tap.zoot.runtime.host.GuestMemoryTransfer
import com.tap.zoot.runtime.host.HostImportGroup
import com.tap.zoot.runtime.host.functionImport
import com.tap.zoot.runtime.host.i32
import com.tap.zoot.runtime.platform.monotonicNanos
import com.tap.zoot.runtime.resources.O2rArchive
import dev.zacsweers.metro.Inject
import io.github.charlietap.chasm.embedding.codegen.CodegenImport
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.HostMemory
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.readI64
import io.github.charlietap.chasm.host.readUtf8String
import io.github.charlietap.chasm.host.withMemory
import io.github.charlietap.chasm.host.writeI32

/** Direct O2R metadata and payload callbacks. */
@Inject
internal class ResourceImports(
    private val archive: O2rArchive,
    private val timings: HostTimings,
) : HostImportGroup {
    private val transfer = GuestMemoryTransfer()

    override val imports: List<CodegenImport> =
        listOf(
            functionImport(
                MODULE,
                "resource_info",
                parameterTypes = {
                    i64()
                    i32()
                },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        withMemory(0) {
                            val entry = archive.entries[parameters.readI64(0)]
                            if (entry == null) {
                                results.writeI32(0, NOT_FOUND)
                            } else {
                                val info = parameters.readI32(1)
                                writeI32(info, RESOURCE_INFO_SIZE)
                                writeI32(info + 4, entry.size)
                                results.writeI32(0, 0)
                            }
                        }
                    },
            ),
            functionImport(
                MODULE,
                "resource_read",
                parameterTypes = {
                    i64()
                    i32(3)
                },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        withMemory(0) {
                            val start = monotonicNanos()
                            val id = parameters.readI64(0)
                            val offset = parameters.readI32(1)
                            val destination = parameters.readI32(2)
                            val length = parameters.readI32(3)
                            val written =
                                transfer.write(this, destination, length) { bytes ->
                                    archive.read(id, offset, bytes, 0, length)
                                }
                            results.writeI32(0, written)
                            timings.resourcesNanos += monotonicNanos() - start
                        }
                    },
            ),
            functionImport(
                MODULE,
                "resource_name",
                parameterTypes = {
                    i64()
                    i32(2)
                },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        withMemory(0) {
                            val name = archive.entries[parameters.readI64(0)]?.name
                            val destination = parameters.readI32(1)
                            val capacity = parameters.readI32(2)
                            results.writeI32(0, if (name == null) NOT_FOUND else writeName(name, destination, capacity, checked = false))
                        }
                    },
            ),
            functionImport(
                MODULE,
                "resource_list",
                parameterTypes = { i32(5) },
                resultTypes = { i32() },
                function =
                    HostFunction { parameters, results ->
                        withMemory(0) {
                            val prefix = readUtf8String(parameters.readI32(0), parameters.readI32(1))
                            val index = parameters.readI32(2)
                            val destination = parameters.readI32(3)
                            val capacity = parameters.readI32(4)
                            val name = archive.list(prefix).getOrNull(index)
                            results.writeI32(0, if (name == null) NOT_FOUND else writeName(name, destination, capacity, checked = true))
                        }
                    },
            ),
        )

    private fun HostMemory.writeName(
        name: String,
        destination: Int,
        capacity: Int,
        checked: Boolean,
    ): Int {
        val encoded = name.encodeToByteArray()
        if (capacity > 0) {
            if (checked) check(encoded.size < capacity)
            write(destination, encoded)
            writeI8(destination + encoded.size, 0)
        }
        return encoded.size
    }

    private companion object {
        const val MODULE = "oot_resources"
        const val NOT_FOUND = -1
        const val RESOURCE_INFO_SIZE = 8
    }
}
