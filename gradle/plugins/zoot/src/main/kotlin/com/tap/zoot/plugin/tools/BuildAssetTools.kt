package com.tap.zoot.plugin.tools

import com.tap.zoot.plugin.shipwright.RecipeStamp
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Builds Torch and the archive packer from the prepared sources. Needs no ROM. */
@DisableCachingByDefault(because = "Native executables depend on the untracked host compiler")
abstract class BuildAssetTools
    @Inject
    constructor(
        private val exec: ExecOperations,
        private val files: FileSystemOperations,
    ) : DefaultTask() {
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val recipeDirectory: DirectoryProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sources: ConfigurableFileCollection

        /** Tracked through [sources]. */
        @get:Internal
        abstract val shipwrightDirectory: DirectoryProperty

        @get:Internal
        abstract val offline: Property<Boolean>

        @get:Internal
        abstract val parallelism: Property<Int>

        @get:LocalState
        abstract val cmakeDirectory: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDirectory: DirectoryProperty

        @get:ServiceReference(NativeBuildService.NAME)
        abstract val nativeBuild: Property<NativeBuildService>

        @TaskAction
        fun build() {
            val recipe = recipeDirectory.get().asFile
            val cmake = cmakeDirectory.get().asFile
            val stamp = RecipeStamp(cmake.resolve("recipe.sha256"), recipe)
            stamp.requirePreparedWhenOffline(
                offline.get(),
                "Native asset tools are not prepared for this recipe. Run ./gradlew prepareRomAssets online first.",
            )
            stamp.clear()
            val tools = cmake.resolve("tools")
            exec.exec {
                commandLine(
                    "cmake",
                    "-S",
                    shipwrightDirectory.get().asFile,
                    "-B",
                    tools,
                    "-G",
                    "Ninja",
                    "-DSOH_TOOLS_ONLY=ON",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DZOOT_DEPENDENCY_RECIPE=$recipe",
                    "-DFETCHCONTENT_FULLY_DISCONNECTED=${onOff(offline.get())}",
                )
            }
            exec.exec {
                commandLine("cmake", "--build", tools, "--target", TORCH, PACKER, "--parallel", parallelism.get())
            }
            files.sync {
                from(tools) { include(TORCH, PACKER) }
                into(outputDirectory)
            }
            stamp.complete()
        }

        companion object {
            const val TORCH = "soh-torch"
            const val PACKER = "soh-o2r-packer"
        }
    }
