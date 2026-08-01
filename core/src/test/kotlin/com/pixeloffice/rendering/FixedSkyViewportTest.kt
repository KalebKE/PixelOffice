package com.pixeloffice.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FixedSkyViewportTest {

    @Test
    fun `one and two office rows reserve the same sky height`() {
        val oneRow = FixedSkyViewport.calculate(1920, 758, 640f, 202f)!!
        val twoRows = FixedSkyViewport.calculate(1343, 1000, 640f, 404f)!!

        assertEquals(152, oneRow.sky.height)
        assertEquals(152, twoRows.sky.height)
        assertEquals(606, oneRow.offices.height)
        assertEquals(848, twoRows.offices.height)
    }

    @Test
    fun `sky sits directly above centered office viewport`() {
        val layout = FixedSkyViewport.calculate(1920, 1080, 640f, 404f)!!

        assertEquals(layout.offices.x, layout.sky.x)
        assertEquals(layout.offices.width, layout.sky.width)
        assertEquals(layout.offices.y + layout.offices.height, layout.sky.y)
        assertEquals(layout.composite.height, layout.sky.height + layout.offices.height)
    }

    @Test
    fun `invalid dimensions do not produce a viewport`() {
        assertNull(FixedSkyViewport.calculate(0, 1000, 640f, 404f))
    }
}
