plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets.commonMain.dependencies { implementation(projects.graphics.core) }
}
