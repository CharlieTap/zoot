import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.zoot.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.conventions.compose)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
}
android {
    namespace = libs.versions.application.namespace.get()
    compileSdk = libs.versions.compile.sdk.get().toInt()
    defaultConfig {
        applicationId = libs.versions.application.id.get()
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = 1
        versionName = libs.versions.version.name.get()
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
dependencies {
    implementation(projects.ui)
    implementation(projects.graphics.backend.webgpu)
    implementation(projects.graphics.upscaler.sgsr1)
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.activity)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(libs.webgpu)
    implementation(libs.coroutines.android)
    implementation(libs.metrox.android)
    implementation(libs.metrox.viewmodel)
}
