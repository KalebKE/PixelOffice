package com.pixeloffice.rendering

import kotlin.math.min
import kotlin.math.roundToInt

data class FixedSkyViewportBounds(
    val composite: AspectFitBounds,
    val sky: AspectFitBounds,
    val offices: AspectFitBounds
)

/**
 * Fits a uniformly scaled office grid beneath a fixed-height sky band.
 * Coordinates use OpenGL's bottom-left origin.
 */
object FixedSkyViewport {
    fun calculate(
        screenWidth: Int,
        screenHeight: Int,
        worldWidth: Float,
        officeWorldHeight: Float,
        preferredSkyHeight: Int = (OfficeLayout.SKY_HEIGHT * 4f).roundToInt()
    ): FixedSkyViewportBounds? {
        if (screenWidth <= 0 || screenHeight <= 1 ||
            worldWidth <= 0f || officeWorldHeight <= 0f || preferredSkyHeight <= 0
        ) {
            return null
        }

        val skyHeight = min(preferredSkyHeight, screenHeight - 1)
        val availableOfficeHeight = screenHeight - skyHeight
        val officeScale = min(
            screenWidth / worldWidth,
            availableOfficeHeight / officeWorldHeight
        )
        val officeWidth = (worldWidth * officeScale).roundToInt().coerceIn(1, screenWidth)
        val officeHeight = (officeWorldHeight * officeScale).roundToInt()
            .coerceIn(1, availableOfficeHeight)
        val compositeHeight = skyHeight + officeHeight
        val x = (screenWidth - officeWidth) / 2
        val y = (screenHeight - compositeHeight) / 2

        val offices = AspectFitBounds(x, y, officeWidth, officeHeight)
        val sky = AspectFitBounds(x, y + officeHeight, officeWidth, skyHeight)
        return FixedSkyViewportBounds(
            composite = AspectFitBounds(x, y, officeWidth, compositeHeight),
            sky = sky,
            offices = offices
        )
    }
}
