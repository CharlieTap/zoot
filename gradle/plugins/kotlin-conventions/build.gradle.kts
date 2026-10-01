plugins { `kotlin-dsl` }
repositories { gradlePluginPortal(); google(); mavenCentral() }
dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${libs.versions.kotlin.get()}")
    implementation(libs.kotlin.gradle.plugin)
}
kotlin { jvmToolchain(libs.versions.java.get().toInt()) }
