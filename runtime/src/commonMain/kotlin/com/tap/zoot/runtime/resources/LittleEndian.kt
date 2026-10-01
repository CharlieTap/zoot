package com.tap.zoot.runtime.resources

internal fun ByteArray.readU16(offset: Int): Int = (this[offset].toInt() and 255) or ((this[offset + 1].toInt() and 255) shl 8)

internal fun ByteArray.readI32(offset: Int): Int = readU16(offset) or (readU16(offset + 2) shl 16)

internal fun ByteArray.writeI32(
    offset: Int,
    value: Int,
) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
    this[offset + 2] = (value ushr 16).toByte()
    this[offset + 3] = (value ushr 24).toByte()
}
