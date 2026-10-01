plugins {
    alias(libs.plugins.conventions.kotlin)
    alias(libs.plugins.conventions.linting)
    alias(libs.plugins.metro)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val sdk = if (target.name == "iosArm64") "iphoneos" else "iphonesimulator"
        val output = layout.buildDirectory.dir("native/${target.name}")
        val script = layout.projectDirectory.file("../../../tools/build-ios-graphics.sh")
        val buildGraphics = tasks.register<Exec>("buildGraphics${target.name.replaceFirstChar(Char::uppercase)}") {
            inputs.files("src/nativeInterop/oot_gpu.cpp", "src/nativeInterop/oot_gpu.h", script)
            outputs.file(output.map { it.file("libootgpu.a") })
            commandLine("bash", script.asFile, sdk, output.get().asFile)
        }
        target.compilations.getByName("main").cinterops.create("ootgpu") {
            definitionFile.set(file("src/nativeInterop/cinterop/ootgpu.def"))
            includeDirs("src/nativeInterop")
            extraOpts("-libraryPath", output.get().asFile.absolutePath, "-libraryPath", output.get().dir("wgpu/lib").asFile.absolutePath)
            tasks.named(interopProcessingTaskName).configure { dependsOn(buildGraphics) }
        }
    }
    sourceSets {
        commonMain.dependencies { api(projects.graphics.core) }
        androidMain.dependencies { implementation(libs.webgpu) }
        getByName("androidHostTest").dependencies { implementation(libs.junit) }
    }
}
