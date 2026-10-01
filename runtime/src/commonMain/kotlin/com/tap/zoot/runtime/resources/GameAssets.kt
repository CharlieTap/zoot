package com.tap.zoot.runtime.resources

/** Packaged files consumed by the game host. */
interface GameAssets {
    fun read(asset: GameAsset): ByteArray
}

enum class GameAsset(
    val fileName: String,
) {
    Wasm("oot.wasm"),
    OotArchive("oot.o2r"),
    SohArchive("soh.o2r"),
    Language("language.txt"),
}
