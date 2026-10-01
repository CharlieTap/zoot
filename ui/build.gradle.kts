plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.compose)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
}

compose.resources { packageOfResClass = "com.tap.zoot.ui.resources" }

kotlin {
    android {
        namespace = libs.versions.application.namespace.get() + ".oot.ui"
        androidResources.enable = true
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            api(projects.n64Controls)
            api(projects.runtime)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coroutines.core)
            implementation(libs.atomicfu)
        }
        androidMain.dependencies {
            implementation(projects.graphics.backend.webgpu)
            implementation(libs.activity.compose)
        }
        iosMain.dependencies { implementation(projects.graphics.backend.webgpu) }
    }
}
