package com.tap.zoot.runtime.resources

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.test.assertTrue

class O2rArchiveTest {
    @Test
    fun parsesListsAndReadsStoredEntries() {
        val archive =
            zip(
                entry("textures/zeta.bin", byteArrayOf(4, 5, 6)),
                entry("textures/alpha.bin", byteArrayOf(1, 2, 3)),
                entry("textures/"),
                entry("scenes/room.bin", byteArrayOf(7)),
            )
        val resources = archive(oot = archive)

        resources.open()

        assertEquals(listOf("textures/alpha.bin", "textures/zeta.bin"), resources.list("textures/"))
        assertEquals(listOf("scenes/room.bin"), resources.list("scenes/"))
        assertEquals(3, resources.entries.size)

        val destination = ByteArray(7) { 9 }
        assertEquals(
            3,
            resources.read(resourceId("textures/alpha.bin"), 0, destination, 2, 3),
        )
        assertContentEquals(byteArrayOf(9, 9, 1, 2, 3, 9, 9), destination)
        assertEquals(-1, resources.read(resourceId("missing"), 0, destination, 0, 1))
    }

    @Test
    fun ootArchiveWinsWhenBothArchivesContainTheSameResource() {
        val resources =
            archive(
                oot = zip(entry("shared.bin", byteArrayOf(1)), entry("oot.bin", byteArrayOf(2))),
                soh = zip(entry("shared.bin", byteArrayOf(3)), entry("soh.bin", byteArrayOf(4))),
            )

        resources.open()

        assertEquals(
            setOf("shared.bin", "oot.bin", "soh.bin"),
            resources.entries.values
                .map { it.name }
                .toSet(),
        )
        val destination = ByteArray(1)
        assertEquals(1, resources.read(resourceId("shared.bin"), 0, destination, 0, 1))
        assertContentEquals(byteArrayOf(1), destination)
    }

    @Test
    fun deflatedReadsUseTheEntrySliceAndExpectedOutputLength() {
        val compressed = byteArrayOf(11, 12, 13)
        val decoded = byteArrayOf(21, 22, 23, 24, 25)
        val decoder = RecordingDecoder(decoded)
        val bytes = zip(entry("compressed.bin", compressed, decoded.size, DEFLATED))
        val resources = archive(oot = bytes, decoder = decoder)
        resources.open()

        val destination = ByteArray(9) { 7 }
        assertEquals(
            decoded.size,
            resources.read(resourceId("compressed.bin"), 0, destination, 2, decoded.size),
        )

        val call = decoder.calls.single()
        assertSame(bytes, call.source)
        assertEquals(compressed.size, call.size)
        assertEquals(2, call.destinationOffset)
        assertEquals(decoded.size, call.length)
        assertContentEquals(compressed, bytes.copyOfRange(call.offset, call.offset + call.size))
        assertContentEquals(byteArrayOf(7, 7, 21, 22, 23, 24, 25, 7, 7), destination)
    }

    @Test
    fun malformedArchiveFailsDuringIndexing() {
        val resources = archive(oot = ByteArray(21))

        assertFails { resources.open() }
    }

    @Test
    fun decoderFailurePropagatesForTruncatedDeflatedEntry() {
        val decoder = RecordingDecoder(ByteArray(0), failure = IllegalStateException("Truncated resource"))
        val resources = archive(oot = zip(entry("compressed.bin", byteArrayOf(1), 5, DEFLATED)), decoder = decoder)
        resources.open()

        val failure =
            assertFails {
                resources.read(resourceId("compressed.bin"), 0, ByteArray(5), 0, 5)
            }

        assertEquals("Truncated resource", failure.message)
    }

    @Test
    fun closeReleasesTheDecoderAndClearsIndexesAndCachedQueries() {
        val decoder = RecordingDecoder(ByteArray(0))
        val resources = archive(oot = zip(entry("textures/a.bin", byteArrayOf(1))), decoder = decoder)
        resources.open()
        assertEquals(listOf("textures/a.bin"), resources.list("textures/"))

        resources.close()

        assertTrue(decoder.closed)
        assertTrue(resources.entries.isEmpty())
        assertTrue(resources.list("textures/").isEmpty())
    }

    @Test
    fun hashesOtrAndArchivePathsIdentically() {
        assertEquals(resourceId("textures/example"), resourceId("__OTR__textures/example"))
        assertEquals(0x9D13A61C0E5B0FF5uL.toLong(), resourceId("123456789"))
    }

    private fun archive(
        oot: ByteArray,
        soh: ByteArray = zip(),
        decoder: RecordingDecoder = RecordingDecoder(ByteArray(0)),
    ): O2rArchive =
        O2rArchive(
            assets =
                object : GameAssets {
                    override fun read(asset: GameAsset): ByteArray =
                        when (asset) {
                            GameAsset.OotArchive -> oot
                            GameAsset.SohArchive -> soh
                            GameAsset.Wasm, GameAsset.Language -> error("Not an archive: $asset")
                        }
                },
            decoder = decoder,
        )

    private data class DecodeCall(
        val source: ByteArray,
        val offset: Int,
        val size: Int,
        val destinationOffset: Int,
        val length: Int,
    )

    private class RecordingDecoder(
        private val decoded: ByteArray,
        private val failure: Throwable? = null,
    ) : RawDeflateDecoder {
        val calls = mutableListOf<DecodeCall>()
        var closed = false

        override fun decode(
            source: ByteArray,
            offset: Int,
            size: Int,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ) {
            calls += DecodeCall(source, offset, size, destinationOffset, length)
            failure?.let { throw it }
            assertEquals(length, decoded.size)
            decoded.copyInto(destination, destinationOffset)
        }

        override fun close() {
            closed = true
        }
    }

    private data class ZipEntry(
        val name: String,
        val contents: ByteArray,
        val size: Int,
        val method: Int,
    )

    private fun entry(
        name: String,
        contents: ByteArray = ByteArray(0),
        size: Int = contents.size,
        method: Int = STORED,
    ) = ZipEntry(name, contents, size, method)

    private fun zip(vararg entries: ZipEntry): ByteArray {
        val bytes = mutableListOf<Byte>()
        val localOffsets = mutableListOf<Int>()
        entries.forEach { entry ->
            localOffsets += bytes.size
            bytes.putI32(LOCAL_FILE_ENTRY)
            bytes.putU16(20)
            bytes.putU16(0)
            bytes.putU16(entry.method)
            bytes.putI32(0)
            bytes.putI32(0)
            bytes.putI32(entry.contents.size)
            bytes.putI32(entry.size)
            val name = entry.name.encodeToByteArray()
            bytes.putU16(name.size)
            bytes.putU16(0)
            bytes.addAll(name.toList())
            bytes.addAll(entry.contents.toList())
        }

        val centralOffset = bytes.size
        entries.forEachIndexed { index, entry ->
            val name = entry.name.encodeToByteArray()
            bytes.putI32(CENTRAL_DIRECTORY_ENTRY)
            bytes.putU16(20)
            bytes.putU16(20)
            bytes.putU16(0)
            bytes.putU16(entry.method)
            bytes.putI32(0)
            bytes.putI32(0)
            bytes.putI32(entry.contents.size)
            bytes.putI32(entry.size)
            bytes.putU16(name.size)
            bytes.putU16(0)
            bytes.putU16(0)
            bytes.putU16(0)
            bytes.putU16(0)
            bytes.putI32(0)
            bytes.putI32(localOffsets[index])
            bytes.addAll(name.toList())
        }
        val centralSize = bytes.size - centralOffset

        bytes.putI32(END_OF_CENTRAL_DIRECTORY)
        bytes.putU16(0)
        bytes.putU16(0)
        bytes.putU16(entries.size)
        bytes.putU16(entries.size)
        bytes.putI32(centralSize)
        bytes.putI32(centralOffset)
        bytes.putU16(0)
        return bytes.toByteArray()
    }

    private fun MutableList<Byte>.putU16(value: Int) {
        add(value.toByte())
        add((value ushr 8).toByte())
    }

    private fun MutableList<Byte>.putI32(value: Int) {
        putU16(value)
        putU16(value ushr 16)
    }

    private companion object {
        const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
        const val CENTRAL_DIRECTORY_ENTRY = 0x02014b50
        const val LOCAL_FILE_ENTRY = 0x04034b50
        const val STORED = 0
        const val DEFLATED = 8
    }
}
