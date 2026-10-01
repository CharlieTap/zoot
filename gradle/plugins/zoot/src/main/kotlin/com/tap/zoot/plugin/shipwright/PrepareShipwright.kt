package com.tap.zoot.plugin.shipwright

import com.tap.zoot.plugin.tools.NativeBuildService
import com.tap.zoot.plugin.tools.onOff
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Downloads the pinned Shipwright, libultraship and Torch archives and applies Zoot's patches. */
@DisableCachingByDefault(because = "Source downloads and CMake state are local to the checkout")
abstract class PrepareShipwright
    @Inject
    constructor(
        private val exec: ExecOperations,
        private val files: FileSystemOperations,
    ) : DefaultTask() {
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val recipeDirectory: DirectoryProperty

        @get:LocalState
        abstract val bootstrapDirectory: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDirectory: DirectoryProperty

        /** Changes how CMake fetches, not what it produces. */
        @get:Internal
        abstract val offline: Property<Boolean>

        @get:ServiceReference(NativeBuildService.NAME)
        abstract val nativeBuild: Property<NativeBuildService>

        @TaskAction
        fun prepare() {
            val recipe = recipeDirectory.get().asFile
            val bootstrap = bootstrapDirectory.get().asFile
            val output = outputDirectory.get().asFile
            val stamp = RecipeStamp(bootstrap.resolve("recipe.sha256"), recipe)
            stamp.requirePreparedWhenOffline(
                offline.get(),
                "Shipwright sources are not prepared for this recipe. Run ./gradlew prepareShipwright online first.",
            )
            stamp.clear()
            exec.exec {
                commandLine(
                    "cmake",
                    "-S",
                    recipe,
                    "-B",
                    bootstrap,
                    "-G",
                    "Ninja",
                    "-DFETCHCONTENT_FULLY_DISCONNECTED=${onOff(offline.get())}",
                )
            }
            files.sync {
                from(bootstrap.resolve("upstream/shipwright"))
                from(bootstrap.resolve("upstream/libultraship")) { into("libultraship") }
                from(bootstrap.resolve("upstream/torch")) { into("torch") }
                into(output)
            }
            val patches = recipe.resolve("patches")
            patches.resolve("series").readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { name ->
                logger.lifecycle("Applying $name")
                exec.exec {
                    workingDir(output)
                    // Do not discover the enclosing Zoot repository or touch its index.
                    environment("GIT_CEILING_DIRECTORIES", output.parentFile.canonicalPath)
                    commandLine("git", "apply", patches.resolve(name))
                }
            }
            stamp.complete()
        }
    }
