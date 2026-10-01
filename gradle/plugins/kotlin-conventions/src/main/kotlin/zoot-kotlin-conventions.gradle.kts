import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val libs = the<LibrariesForLibs>()
kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    android {
        namespace = libs.versions.application.namespace.get() + project.path.replace(":", ".").replace("-", ".")
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get())) }
    }
    iosArm64()
    iosSimulatorArm64()
    compilerOptions {
        extraWarnings.set(true)
        freeCompilerArgs.add("-Xwarning-level=REDUNDANT_VISIBILITY_MODIFIER:disabled")
    }
}
