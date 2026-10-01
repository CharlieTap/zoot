plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.linting)
}

kotlin {
    android { namespace = "com.tap.n64.input" }
    sourceSets.commonMain.dependencies { implementation(libs.atomicfu) }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
