package com.tap.zoot.plugin.guest

import com.tap.zoot.plugin.tools.NativeBuildService
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/** Compiles the guest and the prepared Shipwright sources to `oot.wasm`. Needs no ROM. */
@DisableCachingByDefault(because = "The WASI compiler version is not tracked as an input")
abstract class BuildGuest
    @Inject
    constructor(
        private val exec: ExecOperations,
    ) : DefaultTask() {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sources: ConfigurableFileCollection

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val shipwrightSources: ConfigurableFileCollection

        /** Tracked through [sources]. */
        @get:Internal
        abstract val sourceDirectory: DirectoryProperty

        /** Tracked through [shipwrightSources]. */
        @get:Internal
        abstract val shipwrightDirectory: DirectoryProperty

        @get:Input
        @get:Optional
        abstract val compiler: Property<String>

        /** CMake cache definitions for the guest's build options. */
        @get:Input
        abstract val definitions: MapProperty<String, String>

        @get:Internal
        abstract val parallelism: Property<Int>

        @get:LocalState
        abstract val cmakeDirectory: DirectoryProperty

        @get:OutputFile
        abstract val wasm: RegularFileProperty

        @get:ServiceReference(NativeBuildService.NAME)
        abstract val nativeBuild: Property<NativeBuildService>

        @TaskAction
        fun build() {
            val source = sourceDirectory.get().asFile
            val cmake = cmakeDirectory.get().asFile
            exec.exec {
                compiler.orNull?.let { environment("WASI_CLANG", it) }
                commandLine(
                    listOf(
                        "cmake",
                        "-S",
                        source,
                        "-B",
                        cmake,
                        "-G",
                        "Ninja",
                        "-DSHIPWRIGHT_SOURCE_DIR=${shipwrightDirectory.get().asFile}",
                        "-DCMAKE_TOOLCHAIN_FILE=${source.resolve("cmake/wasm32-wasip1.cmake")}",
                        "-DCMAKE_BUILD_TYPE=Release",
                    ) + definitions.get().map { (name, value) -> "-D$name=$value" },
                )
            }
            exec.exec {
                commandLine("cmake", "--build", cmake, "--parallel", parallelism.get())
            }
            Files.copy(cmake.resolve("oot.wasm").toPath(), wasm.get().asFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
