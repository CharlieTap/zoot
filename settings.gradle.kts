pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent { snapshotsOnly() }
            content { includeGroupByRegex("io\\.github\\.charlietap\\.chasm.*") }
        }
    }
    includeBuild("gradle/plugins/kotlin-conventions")
    includeBuild("gradle/plugins/linting-conventions")
    includeBuild("gradle/plugins/zoot")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent { snapshotsOnly() }
            content { includeGroupByRegex("io\\.github\\.charlietap\\.chasm.*") }
        }
    }
}

rootProject.name = "zoot"

include(":android")
include(":audio")
include(":crash-reporting")
include(":graphics:core")
include(":graphics:backend:webgpu")
include(":graphics:upscaler:sgsr1")
include(":n64-controls")
include(":n64-input")
include(":runtime")
include(":ui")
include(":ios")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("ENHANCED_GRAPH_ORDERING")
