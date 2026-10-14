package com.tap.zoot.graphics.webgpu

import android.util.Log
import java.io.File

/**
 * The shaders and (shader, flags) pairs this install has drawn with. Guest shaders are defined just
 * before their first draw, so later sessions compile them ahead of time from this record instead.
 */
internal class PipelineHistory(
    private val directory: File,
) {
    class Entry(
        val wgsl: String,
        val vertexStride: Int,
        val attributeSizes: IntArray,
        val flags: MutableSet<Int>,
    )

    private val dirty = LinkedHashMap<Long, Entry>()

    fun load(): Map<Long, Entry> =
        runCatching {
            directory
                .listFiles()
                .orEmpty()
                .mapNotNull { runCatching { parse(it) }.getOrNull() }
                .toMap()
        }.onFailure { Log.w(LOG_TAG, "Ignoring unreadable pipeline history", it) }
            .getOrDefault(emptyMap())

    // Each file is named by its key: stride, attribute sizes, flags, then the WGSL.
    private fun parse(file: File): Pair<Long, Entry>? {
        val key = file.name.toLongOrNull(RADIX) ?: return null
        val (stride, attributes, flags, wgsl) = file.readText().split('\n', limit = 4).takeIf { it.size == 4 } ?: return null
        return key to Entry(wgsl, stride.toInt(), attributes.toInts().toIntArray(), flags.toInts().toHashSet())
    }

    private fun format(entry: Entry): String =
        "${entry.vertexStride}\n${entry.attributeSizes.joinToString(",")}\n${entry.flags.joinToString(",")}\n${entry.wgsl}"

    private fun String.toInts(): List<Int> = split(',').filter(String::isNotEmpty).map(String::toInt)

    fun changed(
        key: Long,
        entry: Entry,
    ) {
        dirty[key] = entry
    }

    fun save() {
        if (dirty.isEmpty()) return
        runCatching {
            directory.mkdirs()
            for ((key, entry) in dirty) {
                File(directory, key.toString(RADIX)).writeText(format(entry))
            }
        }.onFailure { Log.w(LOG_TAG, "Could not save pipeline history", it) }
        dirty.clear()
    }

    companion object {
        private const val LOG_TAG = "Zoot-GPU"
        private const val RADIX = 16

        fun key(
            wgsl: String,
            vertexStride: Int,
        ): Long = (wgsl.hashCode().toLong() shl 32) or ((wgsl.length.toLong() * 31 + vertexStride) and 0xffffffffL)
    }
}
