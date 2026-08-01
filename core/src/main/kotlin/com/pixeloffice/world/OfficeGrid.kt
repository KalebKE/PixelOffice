package com.pixeloffice.world

import com.badlogic.gdx.Gdx
import com.pixeloffice.core.Config
import com.pixeloffice.integration.AgentSessionSnapshot
import com.pixeloffice.integration.AgentState
import com.pixeloffice.integration.AgentStateMapper
import com.pixeloffice.rendering.OfficeLayout
import com.pixeloffice.rendering.RenderData
import com.pixeloffice.ui.SettingsConfig
import kotlin.math.ceil
import kotlin.math.min

/**
 * Manages multiple Office instances, one per project.
 * Visualizes event-driven agent sessions across projects.
 */
class OfficeGrid(
    private val config: Config,
    columns: Int = 3,
    private val gutterX: Float = 0f,
    private val gutterY: Float = 0f
) {
    private val columns = columns.coerceAtLeast(1)
    private val offices = mutableMapOf<String, Office>()
    private val projectOrder = mutableListOf<String>()
    private val projectLabels = mutableMapOf<String, String>()
    private val appliedStates = mutableMapOf<String, AgentState>()
    private val loungeOrientationByProject = mutableMapOf<String, Boolean>()

    init {
        // Always have at least one office so there's always something to render
        val defaultOffice = createOffice("default")
        offices["default"] = defaultOffice
        projectOrder.add("default")
        projectLabels["default"] = "default"
    }

    // Overflow counter: sessions that exceeded available desks.
    private val overflowCounts = mutableMapOf<String, Int>()

    val officeWidth: Float get() = config.display.width.toFloat()
    val officeHeight: Float get() = config.display.height.toFloat()
    val officeContentHeight: Float get() = OfficeLayout.contentHeight(officeHeight)

    val rowCount: Int
        get() = ceil(projectOrder.size.coerceAtLeast(1).toFloat() / columns).toInt()

    /** Width of the occupied columns. A partial final row remains left-aligned. */
    val worldWidth: Float
        get() {
            val count = projectOrder.size.coerceAtLeast(1)
            val occupiedColumns = min(count, columns)
            return occupiedColumns * officeWidth + (occupiedColumns - 1) * gutterX
        }

    /** The first row includes sky; later rows contribute wall and floor only. */
    val worldHeight: Float
        get() = officeHeight + (rowCount - 1) * (officeContentHeight + gutterY)

    /** Total wall/floor height rendered beneath the one shared sky band. */
    val officeWorldHeight: Float
        get() = rowCount * officeContentHeight + (rowCount - 1) * gutterY

    /** Bottom of the Y-up projection that keeps the first office anchored at the top. */
    val projectionBottom: Float
        get() = officeHeight - worldHeight

    fun syncWithSnapshots(snapshots: List<AgentSessionSnapshot>) {
        Gdx.app?.log("OfficeGrid", "syncWithSnapshots: ${snapshots.size} snapshots")
        for (s in snapshots) {
            Gdx.app?.log("OfficeGrid", "  session id=${s.id} state=${s.state} activity=${s.activity}")
        }
        // Group by project
        val byProject = snapshots.groupBy { it.projectId }

        val removedProjects = projectOrder.filter { it != "default" && it !in byProject }
        for (projectId in removedProjects) {
            offices.remove(projectId)
            projectOrder.remove(projectId)
            projectLabels.remove(projectId)
            overflowCounts.remove(projectId)
        }

        if (byProject.isEmpty() && "default" !in offices) {
            val defaultOffice = createOffice("default")
            offices["default"] = defaultOffice
            projectOrder.add("default")
            projectLabels["default"] = "default"
        }

        // Create offices for new projects
        for (projectId in byProject.keys) {
            if (projectId !in offices) {
                // Remove the default placeholder once a real project arrives
                if ("default" in offices && projectId != "default") {
                    offices.remove("default")
                    projectOrder.remove("default")
                    projectLabels.remove("default")
                }
                val office = createOffice(projectId)
                offices[projectId] = office
                projectOrder.add(projectId)
            }
            projectLabels[projectId] = byProject[projectId]
                ?.firstOrNull()
                ?.projectLabel
                ?.ifBlank { projectId }
                ?: projectId
        }

        // Sync each office's developers with its sessions
        for ((projectId, sessions) in byProject) {
            val office = offices[projectId] ?: continue
            syncOffice(projectId, office, sessions)
        }
        appliedStates.keys.retainAll(snapshots.mapTo(mutableSetOf()) { it.id })
    }

    private fun syncOffice(projectId: String, office: Office, sessions: List<AgentSessionSnapshot>) {
        val activeSessions = sessions
        Gdx.app?.log("OfficeGrid", "syncOffice($projectId): ${sessions.size} active")
        val currentDevIds = office.getAllDevelopers().map { it.agentId }.toSet()
        val activeDevIds = activeSessions.map { it.id }.toSet()

        // Track overflow for sessions exceeding desk capacity
        var overflow = 0

        // Spawn new developers for sessions we don't have
        for (session in activeSessions) {
            if (session.id !in currentDevIds) {
                val dev = office.spawnDeveloper(session.id)
                if (dev != null) {
                    // A session may move projects or be recreated after an
                    // office reset while keeping the same presentation state.
                    // Force that state onto the newly created entity.
                    appliedStates.remove(session.id)
                    Gdx.app?.log("OfficeGrid", "Spawned developer ${session.id} for $projectId")
                } else {
                    overflow++
                    Gdx.app?.log("OfficeGrid", "Desk overflow for $projectId: no desk for ${session.id}")
                }
            }
        }
        overflowCounts[projectId] = overflow

        // Despawn developers for sessions that ended.
        for (devId in currentDevIds) {
            if (devId !in activeDevIds) {
                office.removeDeveloper(devId)
                appliedStates.remove(devId)
            }
        }

        // Update developer states
        for (session in activeSessions) {
            if (appliedStates[session.id] == session.state) continue
            val dev = office.getDeveloper(session.id) ?: continue
            dev.handleEvent(AgentStateMapper.toEntityEvent(session.state))
            appliedStates[session.id] = session.state
        }
    }

    fun update(dt: Float) {
        for (office in offices.values) {
            office.update(dt)
        }
    }

    private fun createOffice(projectId: String): Office {
        val loungeOnLeft = loungeOrientationByProject.getOrPut(projectId) {
            val activeOrientations = projectOrder
                .asSequence()
                .filterNot { it == "default" }
                .mapNotNull(loungeOrientationByProject::get)
                .toList()
            val leftCount = activeOrientations.count { it }
            val rightCount = activeOrientations.size - leftCount
            when {
                leftCount < rightCount -> true
                rightCount < leftCount -> false
                else -> SettingsConfig.stableLoungeOnLeftForProject(projectId)
            }
        }
        return Office(config, projectId).also {
            it.setupDefaultDeskColumns(loungeOnLeft)
        }
    }

    fun getOfficeRenderData(): List<OfficeRenderEntry> {
        return projectOrder.mapIndexedNotNull { index, projectId ->
            val office = offices[projectId] ?: return@mapIndexedNotNull null
            val column = index % columns
            val row = index / columns
            val offsetX = column * (officeWidth + gutterX)
            val offsetY = row * (officeContentHeight + gutterY)
            OfficeRenderEntry(
                projectId = projectId,
                projectLabel = projectLabels[projectId] ?: projectId,
                renderData = office.getRenderData(),
                offsetX = offsetX,
                offsetY = offsetY,
                column = column,
                row = row
            )
        }
    }

    /**
     * Background-only cells needed to complete a partial lower row. They keep
     * unused space from looking like another sky without creating fake projects.
     */
    fun getEmptyCellOffsets(): List<GridCellOffset> {
        if (rowCount <= 1) return emptyList()
        val count = projectOrder.size.coerceAtLeast(1)
        val occupiedInLastRow = ((count - 1) % columns) + 1
        if (occupiedInLastRow == columns) return emptyList()

        val row = rowCount - 1
        return (occupiedInLastRow until columns).map { column ->
            GridCellOffset(
                offsetX = column * (officeWidth + gutterX),
                offsetY = row * (officeContentHeight + gutterY),
                column = column,
                row = row
            )
        }
    }

    fun getOffice(projectId: String): Office? = offices[projectId]

    fun getAllOffices(): Map<String, Office> = offices.toMap()

    /**
     * Get overflow count for a project (sessions that had no available desk).
     */
    fun getOverflowCount(projectId: String): Int = overflowCounts[projectId] ?: 0
}

data class OfficeRenderEntry(
    val projectId: String,
    val projectLabel: String,
    val renderData: RenderData,
    val offsetX: Float,
    val offsetY: Float,
    val column: Int,
    val row: Int
)

data class GridCellOffset(
    val offsetX: Float,
    val offsetY: Float,
    val column: Int,
    val row: Int
)
