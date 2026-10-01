package com.tap.zoot.runtime.resources

fun resourceId(path: String): Long {
    var crc = -1L
    for (byte in path.removePrefix("__OTR__").encodeToByteArray()) {
        crc = crc xor ((byte.toLong() and 255) shl 56)
        repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x42F0E1EBA9EA3693L else crc shl 1 }
    }
    return crc
}
