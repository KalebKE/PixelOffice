package com.pixeloffice.rendering

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficeAccentPaletteTest {

    @Test
    fun `project accent is stable`() {
        val projectId = "/projects/pixel-office"

        assertEquals(
            OfficeAccentPalette.forProject(projectId),
            OfficeAccentPalette.forProject(projectId)
        )
    }

    @Test
    fun `project accents exercise every couch and trash can color`() {
        val palettes = (0 until 200)
            .map { OfficeAccentPalette.forProject("project-$it") }
            .toSet()

        assertEquals(OfficeAccentPalette.entries.toSet(), palettes)
        assertEquals(
            setOf("couch_orange", "couch_green", "couch_blue", "couch_gray"),
            palettes.map { it.couchSprite }.toSet()
        )
        assertEquals(
            setOf("red_trash_can", "green_trash_can", "blue_trash_can"),
            palettes.map { it.trashCanSprite }.toSet()
        )
    }
}
