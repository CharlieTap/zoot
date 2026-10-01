plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinter)
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("zoot") {
            id = "zoot"
            implementationClass = "com.tap.zoot.plugin.ZootPlugin"
        }
        register("zootAndroid") {
            id = "zoot.android"
            implementationClass = "com.tap.zoot.plugin.android.ZootAndroidPlugin"
        }
        register("zootIos") {
            id = "zoot.ios"
            implementationClass = "com.tap.zoot.plugin.ios.ZootIosPlugin"
        }
        register("zootGuest") {
            id = "zoot.guest"
            implementationClass = "com.tap.zoot.plugin.guest.ZootGuestPlugin"
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

tasks.validatePlugins {
    enableStricterValidation.set(true)
    failOnWarning.set(true)
}
