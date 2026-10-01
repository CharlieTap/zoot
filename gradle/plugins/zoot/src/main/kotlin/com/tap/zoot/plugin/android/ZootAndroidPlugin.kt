package com.tap.zoot.plugin.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.tap.zoot.plugin.ZootArtifact
import com.tap.zoot.plugin.assets.StageGameAssets
import com.tap.zoot.plugin.zootArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/** Packages the root project's game assets into every Android app variant. */
class ZootAndroidPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            configureAssets(project)
        }
    }

    private fun configureAssets(project: Project) =
        with(project) {
            val gameAssets = zootArtifacts("gameAssets", ZootArtifact.GAME_ASSETS)
            val stageGameAssets =
                tasks.register<StageGameAssets>("stageGameAssets") {
                    description = "Stage the game assets packaged by the Android app."
                    assets.from(gameAssets)
                    outputDirectory.set(layout.buildDirectory.dir("generated/game-assets"))
                }
            extensions.configure<ApplicationExtension> {
                androidResources.noCompress += listOf("wasm", "o2r")
                // Only staged assets ship; copies an older standalone guest build left in src/main/assets must not.
                sourceSets
                    .getByName("main")
                    .assets.directories
                    .clear()
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.sources.assets?.addGeneratedSourceDirectory(stageGameAssets, StageGameAssets::outputDirectory)
                }
            }
        }
}
