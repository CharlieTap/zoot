package com.tap.zoot.plugin.rom

import java.io.File
import java.security.MessageDigest

data class Rom(
    val sha1: String,
    val language: String,
    val name: String,
) {
    /** One `key=value` per line, without the timestamp `Properties.store` would add. */
    internal fun toMetadata() = "sha1=$sha1\nlanguage=$language\nname=$name\n"

    companion object {
        fun readManifest(file: File): List<Rom> =
            file
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .map { line ->
                    val (sha1, language, name) = line.split('\t', limit = 3)
                    Rom(sha1, language, name)
                }

        internal fun fromMetadata(text: String): Rom {
            val values = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return Rom(values.getValue("sha1"), values.getValue("language"), values.getValue("name"))
        }
    }
}

internal val ROM_PATTERNS = arrayOf("*.z64", "*.n64", "*.v64", "*.Z64", "*.N64", "*.V64")

fun normaliseRom(bytes: ByteArray): ByteArray {
    require(bytes.size >= 4 && bytes.size % 4 == 0) { "Invalid N64 ROM size" }
    val order = bytes.take(4).map { it.toInt() and 255 }
    when (order) {
        listOf(0x80, 0x37, 0x12, 0x40) -> {}

        listOf(0x37, 0x80, 0x40, 0x12) -> {
            for (offset in bytes.indices step 2) {
                val first = bytes[offset]
                bytes[offset] = bytes[offset + 1]
                bytes[offset + 1] = first
            }
        }

        listOf(0x40, 0x12, 0x37, 0x80) -> {
            for (offset in bytes.indices step 4) bytes.reverse(offset, offset + 4)
        }

        else -> {
            error("Not an N64 ROM. Supply an uncompressed .z64, .v64 or .n64 dump.")
        }
    }
    return bytes
}

fun romSha1(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-1")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

fun languageId(language: String): Int =
    when (language) {
        "english" -> 0
        "japanese" -> 3
        else -> error("Unknown game language '$language'; use english or japanese.")
    }
