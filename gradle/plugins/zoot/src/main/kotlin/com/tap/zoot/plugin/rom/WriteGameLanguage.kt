package com.tap.zoot.plugin.rom

import com.tap.zoot.plugin.ROM_DERIVED
import com.tap.zoot.plugin.neverCache
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Writes Shipwright's language id: the override if set, otherwise the ROM's default. */
@DisableCachingByDefault(because = ROM_DERIVED)
abstract class WriteGameLanguage : DefaultTask() {
    init {
        neverCache()
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val metadata: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val language: Property<String>

    @get:OutputFile
    abstract val languageFile: RegularFileProperty

    @TaskAction
    fun write() {
        val rom = Rom.fromMetadata(metadata.get().asFile.readText())
        val selected = language.orNull ?: rom.language
        val id = languageId(selected)
        logger.lifecycle("Game language: $selected")
        languageFile.get().asFile.writeText("$id\n")
    }
}
