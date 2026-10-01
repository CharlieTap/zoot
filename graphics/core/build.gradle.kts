plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.chasm.host)
    }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
