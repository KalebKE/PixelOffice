package com.pixeloffice.rendering

import kotlin.test.Test
import kotlin.test.assertEquals

class SkyCoverageTest {

    @Test
    fun `three office widths receive complete sky coverage`() {
        assertEquals(
            listOf(
                SkyCoverageSegment(0, 320),
                SkyCoverageSegment(320, 320),
                SkyCoverageSegment(640, 320)
            ),
            skyCoverageSegments(renderWidth = 960, baseWidth = 320)
        )
    }

    @Test
    fun `partial final sky segment covers the remaining width`() {
        assertEquals(
            listOf(
                SkyCoverageSegment(0, 320),
                SkyCoverageSegment(320, 47)
            ),
            skyCoverageSegments(renderWidth = 367, baseWidth = 320)
        )
    }
}
