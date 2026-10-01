package com.tap.zoot.plugin.ios

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** Builds the app with the Kotlin framework Gradle has already linked. */
@UntrackedTask(because = "Xcode tracks the app's incremental build, including its SDK and signing settings")
abstract class XcodeBuild
    @Inject
    constructor(
        private val exec: ExecOperations,
    ) : DefaultTask() {
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val xcodeProject: DirectoryProperty

        /** Directory holding the linked Kotlin framework. */
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val kotlinFrameworks: DirectoryProperty

        @get:Input
        abstract val kotlinFrameworkName: Property<String>

        @get:Input
        abstract val scheme: Property<String>

        @get:Input
        abstract val configuration: Property<String>

        @get:Input
        abstract val destination: Property<String>

        @get:Input
        abstract val buildSettings: MapProperty<String, String>

        @get:Internal
        abstract val derivedData: DirectoryProperty

        @TaskAction
        fun build() {
            val frameworks = kotlinFrameworks.get().asFile
            val settings =
                buildSettings.get() +
                    mapOf(
                        "ZOOT_KOTLIN_FRAMEWORK" to "$frameworks/${kotlinFrameworkName.get()}.framework",
                        "FRAMEWORK_SEARCH_PATHS" to "\"$frameworks\"",
                    )
            exec.exec {
                commandLine(
                    listOf(
                        "xcodebuild",
                        "-quiet",
                        "-project",
                        xcodeProject.get().asFile,
                        "-scheme",
                        scheme.get(),
                        "-configuration",
                        configuration.get(),
                        "-destination",
                        destination.get(),
                        "-derivedDataPath",
                        derivedData.get().asFile,
                    ) + settings.map { (name, value) -> "$name=$value" } + "build",
                )
            }
        }
    }
