package com.tap.zoot.plugin

import com.tap.zoot.plugin.assets.PackSupportArchive
import com.tap.zoot.plugin.assets.StageGameAssets
import com.tap.zoot.plugin.guest.BuildGuest
import com.tap.zoot.plugin.rom.ExtractRomAssets
import com.tap.zoot.plugin.rom.ROM_PATTERNS
import com.tap.zoot.plugin.rom.SelectRom
import com.tap.zoot.plugin.rom.WriteGameLanguage
import com.tap.zoot.plugin.shipwright.PrepareShipwright
import com.tap.zoot.plugin.tools.BuildAssetTools
import com.tap.zoot.plugin.tools.registerNativeBuildService
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/** Prepares sources, builds the guest and turns a user-supplied ROM into game assets. */
class ZootPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit =
        with(project) {
            val zoot =
                extensions.create<ZootExtension>("zoot").apply {
                    shipwright.recipe.convention(layout.projectDirectory.dir("third-party/shipwright"))
                    rom.directory.convention(layout.projectDirectory.dir("rom"))
                    rom.file.convention(providers.gradleProperty("rom").map(layout.projectDirectory::file))
                    rom.manifest.convention(layout.projectDirectory.file("tools/supported-roms.tsv"))
                    rom.language.convention(providers.gradleProperty("gameLanguage"))
                    guest.sources.convention(layout.projectDirectory.dir("guest"))
                    guest.compiler.convention(providers.environmentVariable("WASI_CLANG"))
                    notices.convention(layout.projectDirectory.file("third-party/THIRD_PARTY_NOTICES.txt"))
                }
            registerNativeBuildService()
            val offline = gradle.startParameter.isOffline
            val parallelism = gradle.startParameter.maxWorkerCount

            val prepareShipwright =
                tasks.register<PrepareShipwright>("prepareShipwright") {
                    group = GROUP
                    description = "Prepare the pinned Shipwright sources and Zoot patches."
                    recipeDirectory.set(zoot.shipwright.recipe)
                    bootstrapDirectory.set(layout.buildDirectory.dir("dependencies/bootstrap"))
                    outputDirectory.set(layout.buildDirectory.dir("dependencies/shipwright"))
                    this.offline.set(offline)
                }
            val shipwright = prepareShipwright.flatMap { it.outputDirectory }

            val buildAssetTools =
                tasks.register<BuildAssetTools>("buildAssetTools") {
                    group = GROUP
                    description = "Build the native ROM extraction and archive tools."
                    recipeDirectory.set(zoot.shipwright.recipe)
                    shipwrightDirectory.set(shipwright)
                    sources.from(
                        shipwright.subset(
                            "CMakeLists.txt",
                            "CMake/**",
                            "torch/**",
                            "soh/assets/**",
                            "soh/soh/Extractor/**",
                            "libultraship/src/fast/shaders/**",
                        ) { exclude("**/build*/**", "**/.git/**", "**/*.o2r", "**/*.otr") },
                    )
                    this.offline.set(offline)
                    this.parallelism.set(parallelism)
                    cmakeDirectory.set(layout.buildDirectory.dir("native-assets"))
                    outputDirectory.set(layout.buildDirectory.dir("generated/asset-tools"))
                }
            val assetTools = buildAssetTools.flatMap { it.outputDirectory }

            val packSupportArchive =
                tasks.register<PackSupportArchive>("packSupportArchive") {
                    group = GROUP
                    description = "Pack Shipwright's own assets into soh.o2r."
                    packer.set(assetTools.map { it.file(BuildAssetTools.PACKER) })
                    customAssets.set(shipwright.map { it.dir("soh/assets/custom") })
                    shaders.set(shipwright.map { it.dir("libultraship/src/fast/shaders") })
                    version.set(zoot.shipwright.version)
                    archive.set(layout.buildDirectory.file("generated/support-archive/soh.o2r"))
                }

            val selectRom =
                tasks.register<SelectRom>("selectRom") {
                    group = GROUP
                    description = "Validate the local ROM and normalise its byte order."
                    candidates.from(
                        zoot.rom.file
                            .map<Any> { it }
                            .orElse(zoot.rom.directory.map { it.asFileTree.matching { include(*ROM_PATTERNS) } }),
                    )
                    manifest.set(zoot.rom.manifest)
                    normalisedRom.set(layout.buildDirectory.file("generated/rom/selected.z64"))
                    metadata.set(layout.buildDirectory.file("generated/rom/rom.properties"))
                }

            val extractRomAssets =
                tasks.register<ExtractRomAssets>("extractRomAssets") {
                    group = GROUP
                    description = "Extract oot.o2r from the local ROM."
                    rom.set(selectRom.flatMap { it.normalisedRom })
                    torch.set(assetTools.map { it.file(BuildAssetTools.TORCH) })
                    definitions.set(shipwright.map { it.dir("soh/assets/yml") })
                    version.set(zoot.shipwright.version)
                    outputDirectory.set(layout.buildDirectory.dir("generated/rom-assets"))
                }

            val writeGameLanguage =
                tasks.register<WriteGameLanguage>("writeGameLanguage") {
                    group = GROUP
                    description = "Write the game language for the local ROM."
                    metadata.set(selectRom.flatMap { it.metadata })
                    language.set(zoot.rom.language)
                    languageFile.set(layout.buildDirectory.file("generated/language/language.txt"))
                }

            tasks.register("prepareRomAssets") {
                group = GROUP
                description = "Validate a local ROM and extract its game resources."
                dependsOn(packSupportArchive, extractRomAssets, writeGameLanguage)
            }

            val buildGuest =
                tasks.register<BuildGuest>("buildGuest") {
                    group = GROUP
                    description = "Build the shared release Wasm guest."
                    sourceDirectory.set(zoot.guest.sources)
                    sources.from(zoot.guest.sources.map { it.asFileTree.matching { exclude("build*/**") } })
                    shipwrightDirectory.set(shipwright)
                    shipwrightSources.from(
                        shipwright.subset(
                            "soh/src/**",
                            "soh/include/**",
                            "soh/assets/**/*.h",
                            "soh/soh/**",
                            "libultraship/include/**",
                            "libultraship/src/**",
                            "torch/lib/n64graphics/**",
                            "CMake/**",
                        ) { exclude("**/build*/**") },
                    )
                    compiler.set(zoot.guest.compiler)
                    definitions.putAll(
                        mapOf(
                            "OOT_PROFILE" to "OFF",
                            "OOT_RENDER_TRACE" to "OFF",
                            "OOT_AUDIO_STATS" to "OFF",
                            "OOT_SCENE_PROBE" to "OFF",
                        ),
                    )
                    this.parallelism.set(parallelism)
                    cmakeDirectory.set(layout.buildDirectory.dir("guest"))
                    wasm.set(layout.buildDirectory.file("generated/guest/oot.wasm"))
                }

            val prepareGameAssets =
                tasks.register<StageGameAssets>("prepareGameAssets") {
                    group = GROUP
                    description = "Prepare the Wasm, ROM resources and language for Android and iOS."
                    assets.from(
                        packSupportArchive.flatMap { it.archive },
                        extractRomAssets.flatMap { it.archive },
                        writeGameLanguage.flatMap { it.languageFile },
                        buildGuest.flatMap { it.wasm },
                        zoot.notices,
                    )
                    outputDirectory.set(layout.buildDirectory.dir("game-assets"))
                }

            zootVariant("gameAssets", ZootArtifact.GAME_ASSETS, prepareGameAssets.flatMap { it.outputDirectory })
            zootVariant("guestBinary", ZootArtifact.GUEST_BINARY, buildGuest.flatMap { it.wasm })
        }

    private fun Provider<Directory>.subset(
        vararg includes: String,
        excludes: PatternFilterable.() -> Unit,
    ): Provider<FileTree> =
        map { directory ->
            directory.asFileTree.matching {
                include(*includes)
                excludes()
            }
        }

    private companion object {
        const val GROUP = "game"
    }
}
