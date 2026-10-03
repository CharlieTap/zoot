plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.compose)
    alias(libs.plugins.conventions.linting)
}

kotlin {
    android {
        namespace = "com.tap.crashreporting"
        androidResources.enable = true
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets.commonMain.dependencies {
        api(libs.chasm)
        api(libs.compose.runtime)
        api(libs.compose.ui)
        implementation(libs.compose.foundation)
        implementation(libs.coroutines.core)
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        val commonUiTest = create("commonUiTest") {
            dependsOn(commonTest.get())
            dependencies { implementation(libs.compose.ui.test) }
        }
        iosTest.get().dependsOn(commonUiTest)
        getByName("androidDeviceTest") {
            dependsOn(commonUiTest)
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.compose.ui.test.junit4)
            }
        }
    }
}
