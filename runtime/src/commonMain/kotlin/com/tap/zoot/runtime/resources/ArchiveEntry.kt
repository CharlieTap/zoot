package com.tap.zoot.runtime.resources

internal class ArchiveEntry(
    val archive: ByteArray,
    val name: String,
    val offset: Int,
    val compressedSize: Int,
    val size: Int,
    val method: Int,
)
