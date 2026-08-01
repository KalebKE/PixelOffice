package com.pixeloffice.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.pixeloffice.PixelOfficeGame
import java.awt.Taskbar
import java.io.File
import javax.imageio.ImageIO

fun main() {
    configureMacDockIcon()

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pixel Office - Agent Visualization")
        setWindowedMode(320 * 4, 240 * 4) // 4x scale
        useVsync(true)
        setForegroundFPS(30)

        // Pixel-perfect rendering settings
        setResizable(true)

        setWindowIcon(
            "icons/pixel-office-256.png",
            "icons/pixel-office-128.png",
            "icons/pixel-office-64.png",
            "icons/pixel-office-48.png",
            "icons/pixel-office-32.png",
            "icons/pixel-office-16.png"
        )
    }

    Lwjgl3Application(
        PixelOfficeGame(::applyDesktopWindowLayout, ::constrainDesktopWindowResize),
        config
    )
}

private fun configureMacDockIcon() {
    if (!System.getProperty("os.name").lowercase().contains("mac")) return

    try {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return

        val resourcePath = "/icons/pixel-office-macos-1024.png"
        val icon = DesktopLauncherResources::class.java.getResource(resourcePath)?.let(ImageIO::read)
            ?: File(resourcePath.removePrefix("/")).takeIf(File::isFile)?.let(ImageIO::read)
            ?: return
        taskbar.iconImage = icon
    } catch (error: Exception) {
        System.err.println("Could not set macOS Dock icon: ${error.message}")
    }
}

private object DesktopLauncherResources
