package com.tap.zoot.plugin.guest

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class GuestBinaryArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val binary: RegularFileProperty

    override fun asArguments(): Iterable<String> = listOf("-DguestBinary=${binary.get().asFile.absolutePath}")
}
