package com.tap.zoot.plugin.rom

import com.tap.zoot.plugin.ROM_DERIVED
import com.tap.zoot.plugin.neverCache
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Verifies the user's ROM against the supported list and writes it in big-endian order.
 * Equivalent dumps produce identical output, so extraction does not rerun for a rename
 * or a byte-swapped copy.
 */
@DisableCachingByDefault(because = ROM_DERIVED)
abstract class SelectRom : DefaultTask() {
    init {
        neverCache()
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val candidates: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val normalisedRom: RegularFileProperty

    @get:OutputFile
    abstract val metadata: RegularFileProperty

    @TaskAction
    fun select() {
        val roms = candidates.files.filter { it.isFile }.sortedBy { it.name }
        if (roms.size != 1) {
            throw GradleException(
                "Put one supported ROM in rom/, or select one with -Prom=rom/your-game.z64. " +
                    "Found ${roms.size} ROMs. See the README for supported SHA-1 hashes.",
            )
        }
        val bytes = normaliseRom(roms.single().readBytes())
        val hash = romSha1(bytes)
        val rom =
            Rom.readManifest(manifest.get().asFile).singleOrNull { it.sha1 == hash }
                ?: throw GradleException("Unsupported ROM SHA-1: $hash. See the README for accepted US/Japanese N64 releases.")
        logger.lifecycle("Selected ${rom.name} ($hash)")
        normalisedRom.get().asFile.writeBytes(bytes)
        metadata.get().asFile.writeText(rom.toMetadata())
    }
}
