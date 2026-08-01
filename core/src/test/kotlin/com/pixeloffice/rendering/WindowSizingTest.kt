package com.pixeloffice.rendering

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizingTest {

    @Test
    fun `preferred four times scale is retained when it fits`() {
        assertEquals(
            WindowContentSize(1280, 960),
            WindowSizing.calculate(320, 240, 3000, 2000)
        )
    }

    @Test
    fun `wide grid is capped by monitor width without distortion`() {
        assertEquals(
            WindowContentSize(1920, 758),
            WindowSizing.calculate(640, 240, 1920, 1000)
        )
    }

    @Test
    fun `multi-row grid is capped by monitor height without distortion`() {
        assertEquals(
            WindowContentSize(1343, 1000),
            WindowSizing.calculate(640, 442, 1920, 1000)
        )
    }

    @Test
    fun `sky retains preferred height as more office rows are added`() {
        val oneRow = WindowSizing.calculate(640, 240, 1920, 1000)
        val threeRows = WindowSizing.calculate(640, 644, 1920, 1000)

        assertEquals(758, oneRow.height)
        assertEquals(1000, threeRows.height)
        assertEquals(896, threeRows.width)
    }

    @Test
    fun `invalid dimensions return an empty size`() {
        assertEquals(
            WindowContentSize(0, 0),
            WindowSizing.calculate(0, 240, 1920, 1000)
        )
    }

    @Test
    fun `width led resize keeps fixed sky and removes side margins`() {
        assertEquals(
            WindowContentSize(1060, 487),
            WindowSizing.constrainResize(
                worldWidth = 640,
                worldHeight = 240,
                previousWidth = 1280,
                previousHeight = 556,
                requestedWidth = 1060,
                requestedHeight = 500,
                maxContentWidth = 1920,
                maxContentHeight = 1000,
                fixedSky = true
            )
        )
    }

    @Test
    fun `height led resize keeps fixed sky and removes side margins`() {
        assertEquals(
            WindowContentSize(852, 421),
            WindowSizing.constrainResize(
                worldWidth = 640,
                worldHeight = 240,
                previousWidth = 1060,
                previousHeight = 487,
                requestedWidth = 1050,
                requestedHeight = 421,
                maxContentWidth = 1920,
                maxContentHeight = 1000,
                fixedSky = true
            )
        )
    }

    @Test
    fun `resize axis follows the largest proportional edge change`() {
        assertEquals(
            WindowResizeAxis.HEIGHT,
            WindowSizing.inferResizeAxis(1060, 487, 1000, 421)
        )
        assertEquals(
            WindowResizeAxis.WIDTH,
            WindowSizing.inferResizeAxis(1060, 487, 900, 450)
        )
    }

    @Test
    fun `width led resize falls back to monitor height cap`() {
        assertEquals(
            WindowContentSize(896, 1000),
            WindowSizing.constrainResize(
                worldWidth = 640,
                worldHeight = 644,
                previousWidth = 896,
                previousHeight = 1000,
                requestedWidth = 1920,
                requestedHeight = 1000,
                maxContentWidth = 1920,
                maxContentHeight = 1000,
                fixedSky = true
            )
        )
    }

    @Test
    fun `height led resize falls back to monitor width cap`() {
        assertEquals(
            WindowContentSize(1000, 468),
            WindowSizing.constrainResize(
                worldWidth = 640,
                worldHeight = 240,
                previousWidth = 1000,
                previousHeight = 468,
                requestedWidth = 1000,
                requestedHeight = 1000,
                maxContentWidth = 1000,
                maxContentHeight = 1000,
                fixedSky = true
            )
        )
    }

    @Test
    fun `ordinary rendering retains a uniform ratio during resize`() {
        assertEquals(
            WindowContentSize(1000, 750),
            WindowSizing.constrainResize(
                worldWidth = 320,
                worldHeight = 240,
                previousWidth = 1280,
                previousHeight = 960,
                requestedWidth = 1000,
                requestedHeight = 900,
                maxContentWidth = 1920,
                maxContentHeight = 1000,
                fixedSky = false
            )
        )
    }
}
