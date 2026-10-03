package com.tap.zoot.plugin.guest

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.security.MessageDigest

/** Writes the SHA-256 of the shipped guest, so crash reports name the binary their offsets refer to. */
@DisableCachingByDefault(because = "Hashing the guest is cheaper than a cache lookup")
abstract class GenerateGuestIdentity : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val wasm: RegularFileProperty

    @get:OutputFile
    abstract val identity: RegularFileProperty

    @TaskAction
    fun generate() {
        val digest = MessageDigest.getInstance("SHA-256")
        wasm.get().asFile.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        identity.get().asFile.writeText(digest.digest().joinToString("") { "%02x".format(it) } + "\n")
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 16
    }
}
