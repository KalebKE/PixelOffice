package com.pixeloffice.rendering

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class WindowContentSize(val width: Int, val height: Int)

enum class WindowResizeAxis { WIDTH, HEIGHT }

/** Pure sizing policy shared by desktop window management and unit tests. */
object WindowSizing {
    fun calculate(
        worldWidth: Int,
        worldHeight: Int,
        maxContentWidth: Int,
        maxContentHeight: Int,
        preferredScale: Float = 4f
    ): WindowContentSize {
        if (worldWidth <= 0 || worldHeight <= 0 ||
            maxContentWidth <= 0 || maxContentHeight <= 0 || preferredScale <= 0f
        ) {
            return WindowContentSize(0, 0)
        }

        val skyWorldHeight = OfficeLayout.SKY_HEIGHT
        val officeWorldHeight = worldHeight - skyWorldHeight
        if (officeWorldHeight <= 0f || maxContentHeight <= 1) return WindowContentSize(0, 0)

        val skyHeight = (skyWorldHeight * preferredScale).roundToInt()
            .coerceIn(1, maxContentHeight - 1)
        val officeScale = min(
            preferredScale,
            min(
                maxContentWidth / worldWidth.toFloat(),
                (maxContentHeight - skyHeight) / officeWorldHeight
            )
        )
        val targetWidth = (worldWidth * officeScale).roundToInt()
            .coerceIn(1, maxContentWidth)
        val targetHeight = (skyHeight + officeWorldHeight * officeScale).roundToInt()
            .coerceIn(1, maxContentHeight)

        return WindowContentSize(targetWidth, targetHeight)
    }

    fun calculateUniform(
        worldWidth: Int,
        worldHeight: Int,
        maxContentWidth: Int,
        maxContentHeight: Int,
        preferredScale: Float = 4f
    ): WindowContentSize {
        if (worldWidth <= 0 || worldHeight <= 0 ||
            maxContentWidth <= 0 || maxContentHeight <= 0 || preferredScale <= 0f
        ) {
            return WindowContentSize(0, 0)
        }

        val scale = min(
            preferredScale,
            min(
                maxContentWidth / worldWidth.toFloat(),
                maxContentHeight / worldHeight.toFloat()
            )
        )
        return WindowContentSize(
            width = (worldWidth * scale).roundToInt().coerceIn(1, maxContentWidth),
            height = (worldHeight * scale).roundToInt().coerceIn(1, maxContentHeight)
        )
    }

    fun inferResizeAxis(
        previousWidth: Int,
        previousHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): WindowResizeAxis {
        if (previousWidth <= 0 || previousHeight <= 0) return WindowResizeAxis.WIDTH
        val widthChange = abs(requestedWidth - previousWidth) / previousWidth.toFloat()
        val heightChange = abs(requestedHeight - previousHeight) / previousHeight.toFloat()
        return if (widthChange >= heightChange) WindowResizeAxis.WIDTH else WindowResizeAxis.HEIGHT
    }

    /**
     * Constrain a manual resize while preserving either the fixed-sky layout
     * or the ordinary uniform world ratio. The dimension changed most by the
     * user remains authoritative unless the monitor work area requires a cap.
     */
    fun constrainResize(
        worldWidth: Int,
        worldHeight: Int,
        previousWidth: Int,
        previousHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int,
        maxContentWidth: Int,
        maxContentHeight: Int,
        fixedSky: Boolean,
        preferredScale: Float = 4f
    ): WindowContentSize {
        if (worldWidth <= 0 || worldHeight <= 0 ||
            requestedWidth <= 0 || requestedHeight <= 0 ||
            maxContentWidth <= 0 || maxContentHeight <= 0 || preferredScale <= 0f
        ) {
            return WindowContentSize(0, 0)
        }

        val axis = inferResizeAxis(
            previousWidth,
            previousHeight,
            requestedWidth,
            requestedHeight
        )
        return if (fixedSky) {
            constrainFixedSkyResize(
                worldWidth,
                worldHeight,
                requestedWidth,
                requestedHeight,
                maxContentWidth,
                maxContentHeight,
                preferredScale,
                axis
            )
        } else {
            constrainUniformResize(
                worldWidth,
                worldHeight,
                requestedWidth,
                requestedHeight,
                maxContentWidth,
                maxContentHeight,
                axis
            )
        }
    }

    private fun constrainFixedSkyResize(
        worldWidth: Int,
        worldHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int,
        maxContentWidth: Int,
        maxContentHeight: Int,
        preferredScale: Float,
        axis: WindowResizeAxis
    ): WindowContentSize {
        val officeWorldHeight = worldHeight - OfficeLayout.SKY_HEIGHT
        if (officeWorldHeight <= 0f || maxContentHeight <= 1) return WindowContentSize(0, 0)

        val skyHeight = (OfficeLayout.SKY_HEIGHT * preferredScale).roundToInt()
            .coerceIn(1, maxContentHeight - 1)

        fun fromHeight(height: Int): WindowContentSize {
            val targetHeight = height.coerceIn(skyHeight + 1, maxContentHeight)
            val officeScale = (targetHeight - skyHeight) / officeWorldHeight
            val targetWidth = (worldWidth * officeScale).roundToInt().coerceAtLeast(1)
            if (targetWidth <= maxContentWidth) {
                return WindowContentSize(targetWidth, targetHeight)
            }
            val cappedScale = maxContentWidth / worldWidth.toFloat()
            return WindowContentSize(
                maxContentWidth,
                skyHeight + (officeWorldHeight * cappedScale).roundToInt().coerceAtLeast(1)
            )
        }

        fun fromWidth(width: Int): WindowContentSize {
            val targetWidth = width.coerceIn(1, maxContentWidth)
            val officeScale = targetWidth / worldWidth.toFloat()
            val officeHeight = (officeWorldHeight * officeScale).roundToInt().coerceAtLeast(1)
            val targetHeight = skyHeight + officeHeight
            if (targetHeight <= maxContentHeight) {
                return WindowContentSize(targetWidth, targetHeight)
            }
            return fromHeight(maxContentHeight)
        }

        return when (axis) {
            WindowResizeAxis.WIDTH -> fromWidth(requestedWidth)
            WindowResizeAxis.HEIGHT -> fromHeight(requestedHeight)
        }
    }

    private fun constrainUniformResize(
        worldWidth: Int,
        worldHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int,
        maxContentWidth: Int,
        maxContentHeight: Int,
        axis: WindowResizeAxis
    ): WindowContentSize {
        fun fromHeight(height: Int): WindowContentSize {
            val targetHeight = height.coerceIn(1, maxContentHeight)
            val targetWidth = (targetHeight * worldWidth.toFloat() / worldHeight).roundToInt()
                .coerceAtLeast(1)
            if (targetWidth <= maxContentWidth) {
                return WindowContentSize(targetWidth, targetHeight)
            }
            return WindowContentSize(
                maxContentWidth,
                (maxContentWidth * worldHeight.toFloat() / worldWidth).roundToInt().coerceAtLeast(1)
            )
        }

        fun fromWidth(width: Int): WindowContentSize {
            val targetWidth = width.coerceIn(1, maxContentWidth)
            val targetHeight = (targetWidth * worldHeight.toFloat() / worldWidth).roundToInt()
                .coerceAtLeast(1)
            if (targetHeight <= maxContentHeight) {
                return WindowContentSize(targetWidth, targetHeight)
            }
            return fromHeight(maxContentHeight)
        }

        return when (axis) {
            WindowResizeAxis.WIDTH -> fromWidth(requestedWidth)
            WindowResizeAxis.HEIGHT -> fromHeight(requestedHeight)
        }
    }
}
