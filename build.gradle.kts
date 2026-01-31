plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    // iOS RoboVM plugin - uncomment when iOS module is enabled in settings.gradle.kts
    // id("com.mobidevelop.robovm.robovm-gradle-plugin") version "2.3.21" apply false
}

val gdxVersion by extra { "1.12.1" }
val kotlinVersion by extra { "1.9.22" }
// For iOS builds
val roboVMVersion by extra { "2.3.21" }

allprojects {
    version = "1.0.0"
    group = "com.pixeloffice"
}
