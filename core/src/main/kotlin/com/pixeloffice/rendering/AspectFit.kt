package com.pixeloffice.rendering

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A centered screen-space viewport that preserves a logical world's aspect ratio.
 * Coordinates use OpenGL's bottom-left origin.
 */
data class AspectFitBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

object AspectFit {
    fun calculate(
        screenWidth: Int,
        screenHeight: Int,
        worldWidth: Float,
        worldHeight: Float
    ): AspectFitBounds {
        if (screenWidth <= 0 || screenHeight <= 0 || worldWidth <= 0f || worldHeight <= 0f) {
            return AspectFitBounds(0, 0, 0, 0)
        }

        val scale = min(screenWidth / worldWidth, screenHeight / worldHeight)
        val fittedWidth = (worldWidth * scale).roundToInt().coerceIn(1, screenWidth)
        val fittedHeight = (worldHeight * scale).roundToInt().coerceIn(1, screenHeight)

        return AspectFitBounds(
            x = (screenWidth - fittedWidth) / 2,
            y = (screenHeight - fittedHeight) / 2,
            width = fittedWidth,
            height = fittedHeight
        )
    }
}
