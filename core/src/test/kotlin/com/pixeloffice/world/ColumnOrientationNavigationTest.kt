package com.pixeloffice.world

import com.pixeloffice.core.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnOrientationNavigationTest {

    @Test
    fun `both column orientations keep every desk and lounge corridor navigable`() {
        val officesByOrientation = (0 until 200)
            .map { projectIndex ->
                Office(Config(), "project-$projectIndex").also { it.setupDefaultDeskColumns() }
            }
            .associateBy { office ->
                office.deskColumn1!!.baseX to office.deskColumn2!!.baseX
            }

        assertEquals(2, officesByOrientation.size)
        for (office in officesByOrientation.values) {
            val network = office.getLineNetwork()
            for (desk in office.getRenderData().desks) {
                assertNotNull(network.getDeskMidpoint(desk.id), desk.id)
                assertTrue(
                    network.getPath("corridor_center", "mid_${desk.id}").isNotEmpty(),
                    "${desk.id} should remain connected"
                )
            }
            assertNotNull(network.getPoint("bottom_corridor_center"))
        }
    }

    @Test
    fun `outer aisles and lounge corridor mirror when columns swap`() {
        val offices = (0 until 200)
            .map { index ->
                Office(Config(), "mirrored-$index").also { it.setupDefaultDeskColumns() }
            }
        val normal = offices.first { it.deskColumn1!!.baseX == DeskColumn.LEFT_COLUMN_X }
        val swapped = offices.first { it.deskColumn1!!.baseX == DeskColumn.RIGHT_COLUMN_X }

        val normalNetwork = normal.getLineNetwork()
        val swappedNetwork = swapped.getLineNetwork()

        assertEquals(listOf(136, 166, 196), aisleRows(normalNetwork, "left"))
        assertEquals(listOf(136, 166), aisleRows(normalNetwork, "right"))
        assertEquals(listOf(136, 166), aisleRows(swappedNetwork, "left"))
        assertEquals(listOf(136, 166, 196), aisleRows(swappedNetwork, "right"))

        assertNull(normalNetwork.getPoint("bottom_corridor_left"))
        assertNotNull(normalNetwork.getPoint("bottom_corridor_right"))
        assertNotNull(swappedNetwork.getPoint("bottom_corridor_left"))
        assertNull(swappedNetwork.getPoint("bottom_corridor_right"))

        assertLine(normalNetwork, "bottom_corridor_center", "bottom_corridor_right")
        assertLine(normalNetwork, "left_aisle_y196", "left_aisle_bottom")
        assertLine(swappedNetwork, "bottom_corridor_left", "bottom_corridor_center")
        assertLine(swappedNetwork, "right_aisle_y196", "right_aisle_bottom")
    }

    private fun aisleRows(network: LineNetwork, side: String): List<Int> =
        network.getAllPoints().keys
            .mapNotNull {
                Regex("${side}_aisle_y(\\d+)").matchEntire(it)?.groupValues?.get(1)?.toInt()
            }
            .sorted()

    private fun assertLine(network: LineNetwork, first: String, second: String) {
        assertTrue(
            network.getAllLines().any { line ->
                (line.from.id == first && line.to.id == second) ||
                    (line.from.id == second && line.to.id == first)
            },
            "expected line between $first and $second"
        )
    }
}
