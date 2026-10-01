import io.github.charlietap.chasm.gradle.CodegenConfig
import io.github.charlietap.chasm.gradle.CodegenRuntime
import io.github.charlietap.chasm.gradle.CodegenTask
import io.github.charlietap.chasm.gradle.FactoryVisibility
import io.github.charlietap.chasm.gradle.InterfaceVisibility
import io.github.charlietap.chasm.gradle.WasiLinking
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask

plugins {
    alias(libs.plugins.zoot.guest)
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
    alias(libs.plugins.chasm)
}

chasm {
    modules {
        create("OotWasmModule") {
            binary.set(zootGuest.binary)
            packageName.set("com.tap.zoot.runtime.generated")
            interfaceVisibility.set(InterfaceVisibility.INTERNAL)
            factoryVisibility.set(FactoryVisibility.INTERNAL)
            codegenConfig.set(
                CodegenConfig(
                    runtime = CodegenRuntime.CHASM,
                    wasi = WasiLinking.AUTOMATIC,
                    generateTypesafeMemoryProperties = true,
                ),
            )
            initializers.set(setOf("_initialize"))
            ignoredExports.set(
                setOf(
                    "oot_init",
                    "oot_shutdown",
                    "oot_stub_count",
                    "oot_stub_calls",
                    "oot_stub_name",
                    "oot_stub_reset",
                    "oot_trace_entrance",
                    "oot_trace_frame",
                    "oot_scene_probe_state",
                ),
            )
            function("oot_step") {
                longParam("timestampMicros")
                intReturnType()
            }
            function("oot_audio_step") {
                intParam("frames")
                intReturnType()
            }
            function("oot_set_language") {
                intParam("language")
                intReturnType()
            }
            function("oot_set_audio_volumes") {
                floatParam("music")
                floatParam("effects")
                floatParam("fanfares")
            }
        }
    }
}

tasks.withType<ConfigurableKtLintTask>().configureEach {
    dependsOn(tasks.withType<CodegenTask>())
}

kotlin {
    android { namespace = libs.versions.application.namespace.get() + ".oot.runtime" }
    sourceSets.commonMain.dependencies {
        api(projects.audio)
        api(projects.graphics.core)
        api(projects.n64Input)
        api(libs.chasm)
        implementation(libs.coroutines.core)
    }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.junit)
        implementation(libs.chasm.memory)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.coroutines.test)
    }
}
