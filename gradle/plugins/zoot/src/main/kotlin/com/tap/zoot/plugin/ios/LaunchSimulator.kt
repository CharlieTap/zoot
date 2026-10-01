package com.tap.zoot.plugin.ios

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** Installs and launches the app in a booted or available iPhone simulator. */
@UntrackedTask(because = "Launching an app has no outputs")
abstract class LaunchSimulator
    @Inject
    constructor(
        private val exec: ExecOperations,
    ) : DefaultTask() {
        @get:Internal
        abstract val script: RegularFileProperty

        @get:Internal
        abstract val app: DirectoryProperty

        /** A simulator UDID; the script picks an iPhone when absent. */
        @get:Input
        @get:Optional
        abstract val device: Property<String>

        @TaskAction
        fun launch() {
            exec.exec {
                commandLine(listOfNotNull("bash", script.get().asFile, app.get().asFile, device.orNull))
            }
        }
    }
