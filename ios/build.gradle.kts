plugins {
    alias(libs.plugins.zoot.ios)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.conventions.compose)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "OotApp"
            isStatic = true
            binaryOption("bundleId", "com.tap.zoot.shared")
        }
    }
    sourceSets.iosMain.dependencies {
        implementation(projects.ui)
        implementation(projects.graphics.upscaler.sgsr1)
        implementation(libs.compose.foundation)
        implementation(libs.coroutines.core)
    }
}
