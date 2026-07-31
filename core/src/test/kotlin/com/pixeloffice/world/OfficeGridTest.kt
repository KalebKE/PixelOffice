package com.pixeloffice.world

import com.pixeloffice.core.Config
import com.pixeloffice.core.DisplayConfig
import com.pixeloffice.integration.AoSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficeGridTest {

    private val config = Config(
        display = DisplayConfig(width = 320, height = 240)
    )

    @Test
    fun `world height contains only the 2D office`() {
        val grid = OfficeGrid(config)

        assertEquals(320f, grid.worldWidth)
        assertEquals(240f, grid.worldHeight)
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
        assertEquals(listOf(0f, 320f), entries.map { it.offsetX })
        assertEquals(640f, grid.worldWidth)
        assertEquals(240f, grid.worldHeight)
    }

    private fun snapshot(id: String, projectId: String) = AoSessionSnapshot(
        id = id,
        projectId = projectId,
        status = "working",
        activity = "active",
        attentionLevel = null,
        lastActivityAt = null
    )
}
