package com.pixeloffice.world

import com.pixeloffice.core.Config
import com.pixeloffice.core.DisplayConfig
import com.pixeloffice.integration.AgentSessionSnapshot
import com.pixeloffice.integration.AgentState
import com.pixeloffice.states.DeveloperStateNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficeGridTest {

    private val config = Config(
        display = DisplayConfig(width = 320, height = 240)
    )

    @Test
    fun `world height contains only the 2D office`() {
        val grid = OfficeGrid(config)

        assertEquals(320f, grid.worldWidth)
        assertEquals(240f, grid.worldHeight)
        assertEquals(202f, grid.officeContentHeight)
        assertEquals(202f, grid.officeWorldHeight)
        assertEquals(0f, grid.projectionBottom)
        assertTrue(grid.getOffice("default")!!.getAllDevelopers().isEmpty())
    }

    @Test
    fun `multiple projects render side by side without a factory strip`() {
        val grid = OfficeGrid(config)
        grid.syncWithSnapshots(
            listOf(
                snapshot(id = "agent-a", projectId = "project-a"),
                snapshot(id = "agent-b", projectId = "project-b")
            )
        )

        val entries = grid.getOfficeRenderData()
        assertEquals(listOf("project-a", "project-b"), entries.map { it.projectId })
        assertEquals(listOf("project-a", "project-b"), entries.map { it.projectLabel })
        assertEquals(listOf(0f, 320f), entries.map { it.offsetX })
        assertEquals(listOf(0f, 0f), entries.map { it.offsetY })
        assertEquals(640f, grid.worldWidth)
        assertEquals(240f, grid.worldHeight)
    }

    @Test
    fun `default grid fills three columns before adding a row`() {
        val grid = OfficeGrid(config)
        grid.syncWithSnapshots(
            (1..4).map { snapshot("agent-$it", "project-$it") }
        )

        assertEquals(listOf(0, 1, 2, 0), grid.getOfficeRenderData().map { it.column })
        assertEquals(listOf(0, 0, 0, 1), grid.getOfficeRenderData().map { it.row })
        assertEquals(960f, grid.worldWidth)
        assertEquals(442f, grid.worldHeight)
    }

    @Test
    fun `repository grid balances both desk column orientations`() {
        val grid = OfficeGrid(config)
        val projectIds = listOf(
            "/projects/atlas",
            "/projects/beacon",
            "/projects/cedar",
            "/projects/dynamo",
            "/projects/ember",
            "/projects/flint"
        )

        grid.syncWithSnapshots(projectIds.mapIndexed { index, projectId ->
            snapshot("agent-$index", projectId)
        })

        val loungePositions = grid.getOfficeRenderData().map { entry ->
            entry.renderData.deskColumns.minBy { it.rows.size }.baseX
        }
        assertEquals(3, loungePositions.count { it == DeskColumn.LEFT_COLUMN_X })
        assertEquals(3, loungePositions.count { it == DeskColumn.RIGHT_COLUMN_X })
    }

    @Test
    fun `projects wrap into 202 pixel rows after configured columns`() {
        val expectedDimensions = mapOf(
            1 to (320f to 240f),
            2 to (640f to 240f),
            3 to (640f to 442f),
            4 to (640f to 442f),
            5 to (640f to 644f)
        )

        for ((count, dimensions) in expectedDimensions) {
            val grid = OfficeGrid(config, columns = 2)
            grid.syncWithSnapshots((1..count).map { snapshot("agent-$it", "project-$it") })

            assertEquals(dimensions.first, grid.worldWidth, "width for $count projects")
            assertEquals(dimensions.second, grid.worldHeight, "height for $count projects")
            assertEquals(dimensions.second - 38f, grid.officeWorldHeight, "office height for $count projects")
        }
    }

    @Test
    fun `partial final row is left aligned beneath the shared sky`() {
        val grid = OfficeGrid(config, columns = 2)
        grid.syncWithSnapshots((1..5).map { snapshot("agent-$it", "project-$it") })

        val entries = grid.getOfficeRenderData()
        assertEquals(listOf(0, 1, 0, 1, 0), entries.map { it.column })
        assertEquals(listOf(0, 0, 1, 1, 2), entries.map { it.row })
        assertEquals(listOf(0f, 320f, 0f, 320f, 0f), entries.map { it.offsetX })
        assertEquals(listOf(0f, 0f, 202f, 202f, 404f), entries.map { it.offsetY })
        assertEquals(-404f, grid.projectionBottom)
        assertEquals(
            listOf(GridCellOffset(320f, 404f, column = 1, row = 2)),
            grid.getEmptyCellOffsets()
        )
    }

    @Test
    fun `complete rows do not create background-only cells`() {
        val grid = OfficeGrid(config, columns = 2)
        grid.syncWithSnapshots((1..4).map { snapshot("agent-$it", "project-$it") })

        assertEquals(emptyList(), grid.getEmptyCellOffsets())
    }

    @Test
    fun `invalid column count is clamped to one`() {
        val grid = OfficeGrid(config, columns = 0)
        grid.syncWithSnapshots((1..3).map { snapshot("agent-$it", "project-$it") })

        assertEquals(320f, grid.worldWidth)
        assertEquals(644f, grid.worldHeight)
        assertEquals(listOf(0f, 202f, 404f), grid.getOfficeRenderData().map { it.offsetY })
    }

    @Test
    fun `removing projects collapses unused rows`() {
        val grid = OfficeGrid(config, columns = 2)
        grid.syncWithSnapshots((1..5).map { snapshot("agent-$it", "project-$it") })

        grid.syncWithSnapshots((1..2).map { snapshot("agent-$it", "project-$it") })

        assertEquals(640f, grid.worldWidth)
        assertEquals(240f, grid.worldHeight)
        assertEquals(listOf("project-1", "project-2"), grid.getOfficeRenderData().map { it.projectId })
    }

    @Test
    fun `all sessions use developer desks and overflow only after configured capacity`() {
        val grid = OfficeGrid(config)
        val snapshots = (1..10).map { snapshot("agent-$it", "project") }

        grid.syncWithSnapshots(snapshots)

        assertEquals(9, grid.getOffice("project")!!.getAllDevelopers().size)
        assertEquals(1, grid.getOverflowCount("project"))
    }

    @Test
    fun `recreated session receives its current state after moving projects`() {
        val grid = OfficeGrid(config)
        grid.syncWithSnapshots(listOf(snapshot("agent", "project-a")))

        grid.syncWithSnapshots(listOf(snapshot("agent", "project-b")))

        assertEquals(
            DeveloperStateNames.WRITING_CODE,
            grid.getOffice("project-b")!!.getDeveloper("agent")!!.getState()
        )
    }

    @Test
    fun `recreated project keeps its desk appearance and pet placement`() {
        val grid = OfficeGrid(config)
        grid.syncWithSnapshots(listOf(snapshot("agent", "project-a")))
        val before = grid.getOffice("project-a")!!.getRenderData()

        grid.syncWithSnapshots(emptyList())
        grid.syncWithSnapshots(listOf(snapshot("agent", "project-a")))
        val after = grid.getOffice("project-a")!!.getRenderData()

        assertEquals(before.deskColumns, after.deskColumns)
        assertEquals(before.accentPalette, after.accentPalette)
        assertEquals(
            before.pets.map { Triple(it.type, it.x, it.y) },
            after.pets.map { Triple(it.type, it.x, it.y) }
        )
    }

    private fun snapshot(id: String, projectId: String) = AgentSessionSnapshot(
        id = id,
        provider = "test",
        sessionId = id,
        agentId = "main",
        projectId = projectId,
        projectLabel = projectId,
        state = AgentState.CODING,
        activity = "active",
        lastActivityAt = 1L
    )
}
