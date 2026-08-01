package com.pixeloffice.rendering

import kotlin.test.Test
import kotlin.test.assertEquals

class AspectFitTest {

    @Test
    fun `wide window is pillarboxed around a single office`() {
        assertEquals(
            AspectFitBounds(x = 320, y = 0, width = 960, height = 720),
            AspectFit.calculate(1600, 720, 320f, 240f)
        )
    }

    @Test
    fun `tall window is letterboxed around a two-office world`() {
        assertEquals(
            AspectFitBounds(x = 0, y = 240, width = 1280, height = 480),
            AspectFit.calculate(1280, 960, 640f, 240f)
        )
    }

    @Test
    fun `matching aspect ratio fills the window`() {
        assertEquals(
            AspectFitBounds(x = 0, y = 0, width = 1280, height = 960),
            AspectFit.calculate(1280, 960, 320f, 240f)
        )
    }

    @Test
    fun `minimized window returns an empty viewport`() {
        assertEquals(
            AspectFitBounds(x = 0, y = 0, width = 0, height = 0),
            AspectFit.calculate(0, 0, 320f, 240f)
        )
    }
}
