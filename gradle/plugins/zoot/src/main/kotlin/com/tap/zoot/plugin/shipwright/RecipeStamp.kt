package com.tap.zoot.plugin.shipwright

import org.gradle.api.GradleException
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Records which recipe produced CMake's downloaded state. Gradle tracks outputs, but
 * only this marker proves an offline build can reuse FetchContent's populated trees.
 */
internal class RecipeStamp(
    private val marker: File,
    recipe: File,
) {
    private val hash = sourceRecipeHash(recipe)

    fun requirePreparedWhenOffline(
        offline: Boolean,
        message: String,
    ) {
        if (offline && !(marker.isFile && marker.readText() == hash)) throw GradleException(message)
    }

    /** A failed rerun must not leave an older completion marker behind. */
    fun clear() {
        marker.delete()
    }

    fun complete() {
        marker.parentFile.mkdirs()
        marker.writeText(hash)
    }
}

internal fun sourceRecipeHash(directory: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    directory.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(directory).invariantSeparatorsPath }.forEach { file ->
        digest.update(file.relativeTo(directory).invariantSeparatorsPath.toByteArray())
        digest.update(0.toByte())
        digest.update(file.readBytes())
        digest.update(0.toByte())
    }
    return HexFormat.of().formatHex(digest.digest())
}
