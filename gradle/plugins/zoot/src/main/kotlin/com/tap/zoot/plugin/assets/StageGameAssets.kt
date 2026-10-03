package com.tap.zoot.plugin.assets

import com.tap.zoot.plugin.ROM_DERIVED
import com.tap.zoot.plugin.neverCache
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Collects the files an app bundles, and nothing else that shares their directories. */
@DisableCachingByDefault(because = ROM_DERIVED)
abstract class StageGameAssets
    @Inject
    constructor(
        private val files: FileSystemOperations,
    ) : DefaultTask() {
        init {
            neverCache()
        }

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val assets: ConfigurableFileCollection

        @get:OutputDirectory
        abstract val outputDirectory: DirectoryProperty

        @TaskAction
        fun stage() {
            files.sync {
                from(assets) { include("*.o2r", "*.wasm", "*.wasm.sha256", "language.txt", "THIRD_PARTY_NOTICES.txt") }
                into(outputDirectory)
            }
        }
    }
