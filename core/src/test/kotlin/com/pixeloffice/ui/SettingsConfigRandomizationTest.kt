package com.pixeloffice.ui

import com.pixeloffice.world.ChairColor
import com.pixeloffice.world.Equipment
import com.pixeloffice.world.DeskColumn
import com.pixeloffice.world.WallDecor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SettingsConfigRandomizationTest {

    @Test
    fun `project appearance is stable and project specific`() {
        val first = SettingsConfig.randomizedForProject("/projects/pixel-office")
        val recreated = SettingsConfig.randomizedForProject("/projects/pixel-office")
        val other = SettingsConfig.randomizedForProject("/projects/billfold")

        assertEquals(first, recreated)
        assertNotEquals(first.allDeskSettings(), other.allDeskSettings())
    }

    @Test
    fun `generated office keeps every desk usable and balances its furnishings`() {
        val desks = SettingsConfig.randomizedForProject("/projects/pixel-office").allDeskSettings()

        assertEquals(9, desks.size)
        assertEquals(5, desks.count { it.equipment == Equipment.COMPUTER })
        assertEquals(4, desks.count { it.equipment == Equipment.MONITOR })
        assertTrue(desks.none { it.equipment == Equipment.NONE })
        assertEquals(ChairColor.entries.toSet(), desks.map { it.chairColor }.toSet())
        assertEquals(6, desks.count { it.wallDecor != WallDecor.NONE })
        assertTrue(desks.all { it.deskItems.isEmpty() })
    }

    @Test
    fun `projects use both stable desk column orientations`() {
        val layouts = (0 until 200)
            .map { SettingsConfig.randomizedForProject("project-$it") }

        assertEquals(
            setOf(
                DeskColumn.LEFT_COLUMN_X to DeskColumn.RIGHT_COLUMN_X,
                DeskColumn.RIGHT_COLUMN_X to DeskColumn.LEFT_COLUMN_X
            ),
            layouts.map { it.column1.baseX to it.column2.baseX }.toSet()
        )
        assertTrue(layouts.all { it.column1.rows.size == 3 })
        assertTrue(layouts.all { it.column2.rows.size == 2 })
    }
}
