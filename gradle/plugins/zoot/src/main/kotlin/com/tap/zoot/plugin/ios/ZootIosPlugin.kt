package com.tap.zoot.plugin.ios

import com.tap.zoot.plugin.ZootArtifact
import com.tap.zoot.plugin.assets.StageGameAssets
import com.tap.zoot.plugin.zootArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/** Builds and launches the release iOS app for an Apple Silicon simulator. */
class ZootIosPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configure(project)
        }
    }

    private fun configure(project: Project) =
        with(project) {
            val zootIos =
                extensions.create<ZootIosExtension>("zootIos").apply {
                    simulatorTarget.convention("iosSimulatorArm64")
                    xcodeProject.convention(layout.projectDirectory.dir("../iosApp"))
                    launchScript.convention(layout.projectDirectory.file("../tools/launch-ios.sh"))
                    simulator.convention(providers.gradleProperty("iosSimulator"))
                }
            val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
            // Looked up lazily: the build script declares its targets after this plugin applies.
            val framework =
                zootIos.simulatorTarget.map { target ->
                    (kotlin.targets.getByName(target) as KotlinNativeTarget).binaries.getFramework(NativeBuildType.RELEASE)
                }
            val app = zootIos.xcodeProject

            val gameAssets = zootArtifacts("gameAssets", ZootArtifact.GAME_ASSETS)
            val stageGameAssets =
                tasks.register<StageGameAssets>("stageGameAssets") {
                    description = "Stage the game assets bundled by the Xcode project."
                    assets.from(gameAssets)
                    outputDirectory.set(layout.buildDirectory.dir("game-assets"))
                }

            val generateXcodeProject =
                tasks.register<GenerateXcodeProject>("generateXcodeProject") {
                    description = "Generate the iOS Xcode project with its game assets ready."
                    spec.set(app.map { it.file("project.yml") })
                    sources.from(app.map { it.asFileTree.matching { exclude("Oot.xcodeproj/**", "Info.plist", "DerivedData/**") } })
                    resources.from(stageGameAssets)
                    xcodeProject.set(app.map { it.dir("Oot.xcodeproj") })
                    infoPlist.set(app.map { it.file("Info.plist") })
                }

            val derivedData = layout.buildDirectory.dir("xcode")
            val releaseApp = derivedData.map { it.dir("Build/Products/Release-iphonesimulator/Oot.app") }

            val buildReleaseApp =
                tasks.register<XcodeBuild>("buildReleaseApp") {
                    description = "Build the release iOS app with Xcode."
                    xcodeProject.set(generateXcodeProject.flatMap { it.xcodeProject })
                    val link = framework.flatMap { it.linkTaskProvider }
                    // Kotlin's link task does not attach itself as the producer of its output directory.
                    dependsOn(link)
                    kotlinFrameworks.set(link.flatMap { it.destinationDirectory })
                    kotlinFrameworkName.set(framework.map { it.baseName })
                    scheme.set("Oot")
                    configuration.set("Release")
                    destination.set("generic/platform=iOS Simulator")
                    buildSettings.putAll(
                        mapOf(
                            "ARCHS" to "arm64",
                            "CODE_SIGNING_ALLOWED" to "NO",
                            "OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" to "YES",
                        ),
                    )
                    this.derivedData.set(derivedData)
                }

            val assembleRelease =
                tasks.register<Sync>("assembleRelease") {
                    group = "build"
                    description = "Build the release iOS app for an Apple Silicon simulator."
                    // Xcode owns the bundle, so ordering cannot come from a file input.
                    dependsOn(buildReleaseApp)
                    from(framework.map { binary -> binary.compilation.allKotlinSourceSets.map { it.resources.sourceDirectories } })
                    into(releaseApp.map { it.dir("compose-resources") })
                }

            tasks.register<LaunchSimulator>("runRelease") {
                group = "application"
                description = "Build and launch the release iOS app in an iPhone simulator."
                dependsOn(assembleRelease)
                script.set(zootIos.launchScript)
                this.app.set(releaseApp)
                device.set(zootIos.simulator)
            }
        }
}

abstract class ZootIosExtension {
    /** The Kotlin target whose release framework the simulator app links. */
    abstract val simulatorTarget: Property<String>

    /** Directory containing XcodeGen's `project.yml`. */
    abstract val xcodeProject: DirectoryProperty

    abstract val launchScript: RegularFileProperty

    /** Simulator UDID, from `-PiosSimulator` by convention. */
    abstract val simulator: Property<String>
}
