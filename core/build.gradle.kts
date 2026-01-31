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

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
}

kotlin {
    jvmToolchain(21)
}
