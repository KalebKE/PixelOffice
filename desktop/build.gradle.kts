plugins {
    kotlin("jvm")
    application
}

val gdxVersion: String by rootProject.extra

dependencies {
    implementation(project(":core"))

    // libGDX desktop backend (LWJGL3)
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.pixeloffice.desktop.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    isIgnoreExitValue = true

    // Enable macOS specific settings
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Create a fat JAR for distribution
tasks.register<Jar>("dist") {
    from(files(sourceSets.main.get().output.classesDirs))
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    // Include assets in the JAR
    from(rootProject.file("assets"))

    manifest {
        attributes["Main-Class"] = "com.pixeloffice.desktop.DesktopLauncherKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("pixel-office")
}
