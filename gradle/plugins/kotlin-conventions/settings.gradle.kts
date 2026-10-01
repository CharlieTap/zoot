pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../../libs.versions.toml")) } }
}
rootProject.name = "zoot-kotlin-conventions"
