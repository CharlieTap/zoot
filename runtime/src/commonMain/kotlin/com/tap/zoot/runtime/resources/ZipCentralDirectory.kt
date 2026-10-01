package com.tap.zoot.runtime.resources

internal fun readCentralDirectory(
    archive: ByteArray,
    label: String,
): Map<Long, ArchiveEntry> {
    var end = archive.size - END_RECORD_SIZE
    while (end >= maxOf(0, archive.size - END_RECORD_SEARCH_LIMIT) && archive.readI32(end) != END_OF_CENTRAL_DIRECTORY) end--
    check(end >= 0) { "Invalid O2R archive: $label" }
    var cursor = archive.readI32(end + 16)
    val entries = LinkedHashMap<Long, ArchiveEntry>()
    repeat(archive.readU16(end + 10)) {
        check(archive.readI32(cursor) == CENTRAL_DIRECTORY_ENTRY)
        val method = archive.readU16(cursor + 10)
        val compressedSize = archive.readI32(cursor + 20)
        val size = archive.readI32(cursor + 24)
        val nameLength = archive.readU16(cursor + 28)
        val name = archive.decodeToString(cursor + 46, cursor + 46 + nameLength)
        val localHeader = archive.readI32(cursor + 42)
        val dataOffset = localHeader + 30 + archive.readU16(localHeader + 26) + archive.readU16(localHeader + 28)
        if (!name.endsWith('/')) entries[resourceId(name)] = ArchiveEntry(archive, name, dataOffset, compressedSize, size, method)
        cursor += 46 + nameLength + archive.readU16(cursor + 30) + archive.readU16(cursor + 32)
    }
    return entries
}

private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
private const val CENTRAL_DIRECTORY_ENTRY = 0x02014b50
private const val END_RECORD_SIZE = 22
private const val END_RECORD_SEARCH_LIMIT = END_RECORD_SIZE + 65_535
