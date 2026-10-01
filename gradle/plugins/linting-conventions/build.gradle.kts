plugins { `kotlin-dsl` }
repositories { gradlePluginPortal() }
dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation("org.jmailen.gradle:kotlinter-gradle:${libs.versions.kotlinter.get()}")
}
kotlin { jvmToolchain(libs.versions.java.get().toInt()) }
