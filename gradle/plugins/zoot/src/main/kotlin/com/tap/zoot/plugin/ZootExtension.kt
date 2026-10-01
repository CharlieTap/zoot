package com.tap.zoot.plugin

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

/** Inputs to the game build. Conventions match the repository layout. */
abstract class ZootExtension {
    @get:Nested
    abstract val shipwright: ShipwrightSettings

    @get:Nested
    abstract val rom: RomSettings

    @get:Nested
    abstract val guest: GuestSettings

    /** Licence notices packaged with both apps. */
    abstract val notices: RegularFileProperty

    fun shipwright(action: Action<in ShipwrightSettings>) = action.execute(shipwright)

    fun rom(action: Action<in RomSettings>) = action.execute(rom)

    fun guest(action: Action<in GuestSettings>) = action.execute(guest)
}

interface ShipwrightSettings {
    /** Source pins, patches and the CMake bootstrap kept in Git. */
    val recipe: DirectoryProperty

    /** Shipwright release recorded in the generated archives; no convention. */
    val version: Property<String>
}

interface RomSettings {
    /** Searched for a single ROM when [file] is absent. */
    val directory: DirectoryProperty

    /** An explicit ROM, from `-Prom` by convention. */
    val file: RegularFileProperty

    /** SHA-1 hashes of supported big-endian ROMs. */
    val manifest: RegularFileProperty

    /** Overrides the ROM's default language, from `-PgameLanguage` by convention. */
    val language: Property<String>
}

interface GuestSettings {
    val sources: DirectoryProperty

    /** WASI-capable Clang, from `WASI_CLANG` by convention; the toolchain file searches otherwise. */
    val compiler: Property<String>
}
