import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.linting)
}

// Audio Queue needs a booted simulator with an audio device, not a standalone process.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set(providers.gradleProperty("iosSimulator").orElse("booted"))
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.compilations.getByName("main").cinterops.create("audioSession") {
            definitionFile.set(file("src/nativeInterop/audioSession.def"))
            includeDirs("src/nativeInterop")
        }
    }
    sourceSets.iosTest.dependencies { implementation(kotlin("test")) }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.kotlin.test.junit)
        implementation(libs.robolectric)
    }
}
