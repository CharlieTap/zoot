package com.tap.zoot.plugin

import org.gradle.api.Task

internal const val ROM_DERIVED = "ROM-derived assets must never enter a build cache"

/** Unlike the annotation, this cannot be overridden by a build script's `cacheIf`. */
internal fun Task.neverCache() {
    outputs.doNotCacheIf(ROM_DERIVED) { true }
}
