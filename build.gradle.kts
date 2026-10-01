plugins {
    alias(libs.plugins.zoot.assets)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.kotlinter) apply false
}

zoot {
    shipwright {
        version = libs.versions.shipwright
    }
}

tasks.register("fmt") {
    dependsOn(
        ":android:fmt",
        ":audio:fmt",
        ":graphics:core:fmt",
        ":graphics:backend:webgpu:fmt",
        ":graphics:upscaler:sgsr1:fmt",
        ":ios:fmt",
        ":n64-controls:fmt",
        ":n64-input:fmt",
        ":runtime:fmt",
        ":ui:fmt",
    )
}
