package com.tap.zoot.plugin.guest

import com.tap.zoot.plugin.ZootArtifact
import com.tap.zoot.plugin.zootArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType

/** Gives a project the release Wasm guest, without extracting a ROM. */
class ZootGuestPlugin : Plugin<Project> {
    override fun apply(project: Project) =
        with(project) {
            val guestBinary = zootArtifacts("guestBinary", ZootArtifact.GUEST_BINARY)
            val zootGuest =
                extensions.create<ZootGuestExtension>("zootGuest").apply {
                    binary.set(layout.file(guestBinary.flatMap { it.elements }.map { it.single().asFile }))
                    binary.disallowChanges()
                }
            val arguments =
                objects.newInstance<GuestBinaryArguments>().apply {
                    binary.set(zootGuest.binary)
                }
            tasks.withType<Test>().configureEach {
                jvmArgumentProviders.add(arguments)
            }
        }
}

abstract class ZootGuestExtension {
    /** `oot.wasm`, carrying the dependency on the task that builds it. */
    abstract val binary: RegularFileProperty
}
