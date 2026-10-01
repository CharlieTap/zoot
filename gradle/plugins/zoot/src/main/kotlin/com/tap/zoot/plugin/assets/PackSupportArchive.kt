package com.tap.zoot.plugin.assets

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Packs Shipwright's own assets and libultraship's shaders into `soh.o2r`. Needs no ROM. */
@DisableCachingByDefault(because = "Packing is quicker than a cache round trip")
abstract class PackSupportArchive
    @Inject
    constructor(
        private val exec: ExecOperations,
        private val files: FileSystemOperations,
    ) : DefaultTask() {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val packer: RegularFileProperty

        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val customAssets: DirectoryProperty

        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val shaders: DirectoryProperty

        @get:Input
        abstract val version: Property<String>

        @get:OutputFile
        abstract val archive: RegularFileProperty

        @TaskAction
        fun pack() {
            // Stage support assets instead of having upstream's CMake target mutate the source tree.
            val support = temporaryDir.resolve("support")
            files.sync {
                from(customAssets) { exclude("shaders/**") }
                from(shaders) { into("shaders") }
                into(support)
            }
            exec.exec {
                commandLine(packer.get().asFile, support, archive.get().asFile, version.get())
            }
        }
    }
