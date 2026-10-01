package com.tap.zoot.runtime.resources

import com.tap.zoot.runtime.engine.GameSessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Indexed O2R archive access. Resource decoding remains in the guest. */
@Inject
@SingleIn(GameSessionScope::class)
internal class O2rArchive(
    private val assets: GameAssets,
    private val decoder: RawDeflateDecoder,
) : AutoCloseable {
    private val index = LinkedHashMap<Long, ArchiveEntry>()
    private val listings = HashMap<String, List<String>>()

    val entries: Map<Long, ArchiveEntry>
        get() = index

    fun open() {
        for (asset in listOf(GameAsset.OotArchive, GameAsset.SohArchive)) {
            readCentralDirectory(assets.read(asset), asset.fileName).forEach { (id, entry) ->
                if (id !in index) index[id] = entry
            }
        }
    }

    fun list(prefix: String): List<String> =
        listings.getOrPut(prefix) {
            index.values
                .map(ArchiveEntry::name)
                .filter { it.startsWith(prefix) }
                .sorted()
        }

    fun read(
        id: Long,
        offset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ): Int {
        val entry = index[id] ?: return -1
        require(offset == 0 && length == entry.size)
        when (entry.method) {
            STORED -> entry.archive.copyInto(destination, destinationOffset, entry.offset, entry.offset + length)
            DEFLATED -> decoder.decode(entry.archive, entry.offset, entry.compressedSize, destination, destinationOffset, length)
            else -> error("Unsupported ZIP compression: ${entry.method}")
        }
        return length
    }

    override fun close() {
        decoder.close()
        index.clear()
        listings.clear()
    }

    private companion object {
        const val STORED = 0
        const val DEFLATED = 8
    }
}
