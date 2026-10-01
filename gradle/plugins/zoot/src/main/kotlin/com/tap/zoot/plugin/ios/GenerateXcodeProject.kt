package com.tap.zoot.plugin.ios

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Runs XcodeGen. The generated project lists source files, so it reruns when they change. */
@DisableCachingByDefault(because = "XcodeGen is quicker than a cache round trip")
abstract class GenerateXcodeProject
    @Inject
    constructor(
        private val exec: ExecOperations,
    ) : DefaultTask() {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val spec: RegularFileProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sources: ConfigurableFileCollection

        /** XcodeGen requires referenced resources to exist. */
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NAME_ONLY)
        abstract val resources: ConfigurableFileCollection

        @get:OutputDirectory
        abstract val xcodeProject: DirectoryProperty

        @get:OutputFile
        abstract val infoPlist: RegularFileProperty

        @TaskAction
        fun generate() {
            exec.exec {
                commandLine("xcodegen", "generate", "--spec", spec.get().asFile)
            }
        }
    }
