package com.tap.zoot.plugin

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.project

/** Selects what a project consumes from the root project's game build. */
interface ZootArtifact : Named {
    companion object {
        val ATTRIBUTE: Attribute<ZootArtifact> = Attribute.of("com.tap.zoot.artifact", ZootArtifact::class.java)

        /** Staged Wasm, ROM archives, language and notices. */
        const val GAME_ASSETS = "game-assets"

        /** The release Wasm guest alone; building it never needs a ROM. */
        const val GUEST_BINARY = "guest-binary"
    }
}

internal fun Project.zootVariant(
    name: String,
    kind: String,
    artifact: Provider<out FileSystemLocation>,
): NamedDomainObjectProvider<ConsumableConfiguration> =
    configurations.consumable(name) {
        attributes.attribute(ZootArtifact.ATTRIBUTE, objects.named(kind))
        outgoing.artifact(artifact)
    }

internal fun Project.zootArtifacts(
    name: String,
    kind: String,
): NamedDomainObjectProvider<ResolvableConfiguration> {
    val dependencyScope = configurations.dependencyScope("${name}Dependencies")
    dependencies.add(dependencyScope.name, dependencies.project(":"))
    return configurations.resolvable(name) {
        extendsFrom(dependencyScope.get())
        attributes.attribute(ZootArtifact.ATTRIBUTE, objects.named(kind))
    }
}
