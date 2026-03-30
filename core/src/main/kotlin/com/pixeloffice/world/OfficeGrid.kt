package com.pixeloffice.world

import com.badlogic.gdx.Gdx
import com.pixeloffice.core.Config
import com.pixeloffice.integration.AoSessionSnapshot
import com.pixeloffice.integration.StateMapper
import com.pixeloffice.rendering.RenderData

/**
 * Detected role for a session based on ID/status heuristics.
 */
enum class SessionRole {
    DEVELOPER,
    PROJECT_MANAGER,
    PRODUCT_OWNER
}

/**
 * Manages multiple Office instances, one per project.
 * Used in AO integration mode to visualize sessions across projects.
 */
class OfficeGrid(
    private val config: Config,
    private val columns: Int = 2,
    private val gutterX: Float = 0f,
    private val gutterY: Float = 0f
) {
    private val offices = mutableMapOf<String, Office>()
    private val projectOrder = mutableListOf<String>()

    init {
        // Always have at least one office so there's always something to render
        val defaultOffice = Office(config)
        defaultOffice.setupDefaultDeskColumns()
        offices["default"] = defaultOffice
        projectOrder.add("default")
    }

    // Track which sessions are mapped to PM/PO per project (to avoid re-spawning)
    private val pmSessions = mutableMapOf<String, String>()   // projectId → sessionId
    private val poSessions = mutableMapOf<String, String>()   // projectId → sessionId

    // Overflow counter: sessions that exceeded available desks (6 worker desks)
    private val overflowCounts = mutableMapOf<String, Int>()

    val officeWidth: Float get() = config.display.width.toFloat()
    val officeHeight: Float get() = config.display.height.toFloat()

    /** All offices in a single horizontal row. */
    val worldWidth: Float
        get() {
            val count = projectOrder.size.coerceAtLeast(1)
            return count * officeWidth + (count - 1) * gutterX
        }

    /** Office height + conveyor strip below */
    val worldHeight: Float
        get() = officeHeight + 140f

    fun syncWithSnapshots(snapshots: List<AoSessionSnapshot>) {
        Gdx.app?.log("OfficeGrid", "syncWithSnapshots: ${snapshots.size} snapshots")
        for (s in snapshots) {
            Gdx.app?.log("OfficeGrid", "  session id=${s.id} status=${s.status} activity=${s.activity}")
        }
        // Group by project
        val byProject = snapshots.groupBy { it.projectId }

        // Create offices for new projects
        for (projectId in byProject.keys) {
            if (projectId !in offices) {
                // Remove the default placeholder once a real project arrives
                if ("default" in offices && projectId != "default") {
                    offices.remove("default")
                    projectOrder.remove("default")
                }
                val office = Office(config)
                office.setupDefaultDeskColumns()
                offices[projectId] = office
                projectOrder.add(projectId)
            }
        }

        // Sync each office's developers with its sessions
        for ((projectId, sessions) in byProject) {
            val office = offices[projectId] ?: continue
            syncOffice(projectId, office, sessions)
        }
    }

    /**
     * Detect the role of a session based on ID patterns and status heuristics.
     *
     * - Session IDs containing "orchestrator" or "lifecycle" → Project Manager
     * - Session IDs containing "review" OR status "reviewing"/"review_local" → Product Owner
     * - Everything else → Developer
     */
    private fun detectRole(session: AoSessionSnapshot): SessionRole {
        val idLower = session.id.lowercase()
        if (idLower.contains("orchestrator") || idLower.contains("lifecycle")) {
            return SessionRole.PROJECT_MANAGER
        }
        if (idLower.contains("review") || session.status in setOf("reviewing", "review_local", "local_review")) {
            return SessionRole.PRODUCT_OWNER
        }
        return SessionRole.DEVELOPER
    }

    private fun syncOffice(projectId: String, office: Office, sessions: List<AoSessionSnapshot>) {
        val activeSessions = sessions.filter { !StateMapper.isTerminal(it.status) }
        Gdx.app?.log("OfficeGrid", "syncOffice($projectId): ${sessions.size} total, ${activeSessions.size} active (${sessions.size - activeSessions.size} terminal)")

        // Classify sessions by role
        val sessionsByRole = activeSessions.groupBy { detectRole(it) }
        val pmCandidates = sessionsByRole[SessionRole.PROJECT_MANAGER] ?: emptyList()
        val poCandidates = sessionsByRole[SessionRole.PRODUCT_OWNER] ?: emptyList()
        val devSessions = sessionsByRole[SessionRole.DEVELOPER] ?: emptyList()
        Gdx.app?.log("OfficeGrid", "  roles: ${devSessions.size} devs, ${pmCandidates.size} PMs, ${poCandidates.size} POs")

        // --- Project Manager ---
        // Use the first PM candidate; clear PM if no candidates
        val currentPmSessionId = pmSessions[projectId]
        val newPmSession = pmCandidates.firstOrNull()
        if (newPmSession != null && currentPmSessionId != newPmSession.id) {
            // Spawn or update PM
            if (office.getProjectManager() == null) {
                office.spawnProjectManager()
            }
            pmSessions[projectId] = newPmSession.id
            Gdx.app?.log("OfficeGrid", "PM mapped: ${newPmSession.id} for project $projectId")
        } else if (newPmSession == null && currentPmSessionId != null) {
            // PM session is gone — idle the PM (don't remove permanent manager)
            office.getProjectManager()?.handleEvent("idle")
            pmSessions.remove(projectId)
        }

        // Update PM state from its session
        if (newPmSession != null) {
            val event = StateMapper.mapToEvent(newPmSession.status, newPmSession.activity)
            if (event != null && event != "__despawn__") {
                office.getProjectManager()?.handleEvent(event)
            }
        }

        // --- Product Owner ---
        val currentPoSessionId = poSessions[projectId]
        val newPoSession = poCandidates.firstOrNull()
        if (newPoSession != null && currentPoSessionId != newPoSession.id) {
            if (office.getProductOwner() == null) {
                office.spawnProductOwnerPatrol()
            }
            poSessions[projectId] = newPoSession.id
            Gdx.app?.log("OfficeGrid", "PO mapped: ${newPoSession.id} for project $projectId")
        } else if (newPoSession == null && currentPoSessionId != null) {
            office.getProductOwner()?.handleEvent("idle")
            poSessions.remove(projectId)
        }

        // Update PO state from its session
        if (newPoSession != null) {
            val event = StateMapper.mapToEvent(newPoSession.status, newPoSession.activity)
            if (event != null && event != "__despawn__") {
                office.getProductOwner()?.handleEvent(event)
            }
        }

        // --- Developers ---
        val currentDevIds = office.getAllDevelopers().map { it.agentId }.toSet()
        val activeDevIds = devSessions.map { it.id }.toSet()

        // Track overflow for sessions exceeding desk capacity
        var overflow = 0

        // Spawn new developers for sessions we don't have
        for (session in devSessions) {
            if (session.id !in currentDevIds) {
                val dev = office.spawnDeveloper(session.id)
                if (dev != null) {
                    Gdx.app?.log("OfficeGrid", "Spawned developer ${session.id} for $projectId")
                } else {
                    overflow++
                    Gdx.app?.log("OfficeGrid", "Desk overflow for $projectId: no desk for ${session.id}")
                }
            }
        }
        overflowCounts[projectId] = overflow

        // Despawn developers for sessions that are terminal or reclassified as PM/PO
        val pmPoIds = setOfNotNull(pmSessions[projectId], poSessions[projectId])
        for (devId in currentDevIds) {
            if (devId !in activeDevIds || devId in pmPoIds) {
                office.removeDeveloper(devId)
            }
        }

        // Update developer states
        for (session in devSessions) {
            val event = StateMapper.mapToEvent(session.status, session.activity) ?: continue
            if (event == "__despawn__") continue
            val dev = office.getDeveloper(session.id) ?: continue
            dev.handleEvent(event)
        }
    }

    fun update(dt: Float) {
        for (office in offices.values) {
            office.update(dt)
        }
    }

    fun getOfficeRenderData(): List<OfficeRenderEntry> {
        return projectOrder.mapIndexedNotNull { index, projectId ->
            val office = offices[projectId] ?: return@mapIndexedNotNull null
            val offsetX = index * (officeWidth + gutterX)
            val offsetY = 0f
            OfficeRenderEntry(
                projectId = projectId,
                renderData = office.getRenderData(),
                offsetX = offsetX,
                offsetY = offsetY
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
    val renderData: RenderData,
    val offsetX: Float,
    val offsetY: Float
)
