package com.pixeloffice.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.pixeloffice.PixelOfficeGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pixel Office - Claude Code Visualization")
        setWindowedMode(320 * 2, 240 * 2) // 2x scale for better visibility
        useVsync(true)
        setForegroundFPS(30)

        // Pixel-perfect rendering settings
        setResizable(true)

        // Window icon (optional, can be added later)
        // setWindowIcon("icon.png")
    }

    Lwjgl3Application(PixelOfficeGame(), config)
}
