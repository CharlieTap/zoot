pluginManagement { repositories { gradlePluginPortal() } }
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../../libs.versions.toml")) } }
}
rootProject.name = "zoot-linting-conventions"
