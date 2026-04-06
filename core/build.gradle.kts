plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
}

val gdxVersion: String by rootProject.extra

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // libGDX core
    api("com.badlogicgames.gdx:gdx:$gdxVersion")

    // gdx-gltf for GLB model loading
    api("com.github.mgsx-dev.gdx-gltf:gltf:2.2.1")

    // gdx-vfx for post-processing (SSAO, bloom, etc.)
    api("com.crashinvaders.vfx:gdx-vfx-core:0.5.4")
    api("com.crashinvaders.vfx:gdx-vfx-effects:0.5.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
}

kotlin {
    jvmToolchain(17)
}
