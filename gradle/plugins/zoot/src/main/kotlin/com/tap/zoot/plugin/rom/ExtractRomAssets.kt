package com.tap.zoot.plugin.rom

import com.tap.zoot.plugin.ROM_DERIVED
import com.tap.zoot.plugin.neverCache
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Runs Torch over the normalised ROM to produce `oot.o2r`. */
@DisableCachingByDefault(because = ROM_DERIVED)
abstract class ExtractRomAssets
    @Inject
    constructor(
        private val exec: ExecOperations,
    ) : DefaultTask() {
        init {
            neverCache()
        }

        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val rom: RegularFileProperty

        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val torch: RegularFileProperty

        /** Shipwright's YAML asset descriptions. */
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val definitions: DirectoryProperty

        @get:Input
        abstract val version: Property<String>

        /** Torch also keeps its `torch.hash.yml` state here. */
        @get:OutputDirectory
        abstract val outputDirectory: DirectoryProperty

        @get:Internal
        val archive: Provider<RegularFile>
            get() = outputDirectory.file("oot.o2r")

        @TaskAction
        fun extract() {
            val output = outputDirectory.get().asFile
            exec.exec {
                commandLine(
                    torch.get().asFile,
                    "--src",
                    definitions.get().asFile,
                    "--dest",
                    output,
                    "--version",
                    version.get(),
                    rom.get().asFile,
                )
            }
            check(output.resolve("oot.o2r").isFile) { "Torch did not produce oot.o2r" }
        }
    }
