package com.pixeloffice.world

import com.pixeloffice.core.Config
import com.pixeloffice.entities.*
import kotlin.math.sqrt

/**
 * A desk in the office.
 */
data class Desk(
    val id: String,
    val x: Float,
    val y: Float,
    var occupiedBy: String? = null // Developer entity ID
)

/**
 * A whiteboard in the office.
 */
data class Whiteboard(
    val id: String,
    val x: Float,
    val y: Float
)

/**
 * Manages the office layout and all entities within it.
 *
 * Handles desk allocation, entity spawning, and provides
 * the world state for rendering.
 */
class Office(private val config: Config) {

    // Layout
    private val desks = mutableMapOf<String, Desk>()
    private val whiteboards = mutableMapOf<String, Whiteboard>()
    private val tileSize = config.office.tileSize

    // Entities
    private val developers = mutableMapOf<String, Developer>()
    private var projectManager: ProjectManager? = null
    private var projectOwner: ProjectOwner? = null
    private val effects = mutableListOf<BaseEntity>() // Ghosts, bubbles, etc.

    // Entity ID counter
    private var nextEntityId = 0

    init {
        setupLayout()
    }

    private fun setupLayout() {
        // Set up desks from config
        for (pos in config.office.deskPositions) {
            desks[pos.id] = Desk(
                id = pos.id,
                x = pos.x.toFloat(),
                y = pos.y.toFloat()
            )
        }

        // Set up whiteboards from config
        for (pos in config.office.whiteboardPositions) {
            whiteboards[pos.id] = Whiteboard(
                id = pos.id,
                x = pos.x.toFloat(),
                y = pos.y.toFloat()
            )
        }
    }

    private fun generateEntityId(prefix: String = "entity"): String {
        nextEntityId++
        return "${prefix}_$nextEntityId"
    }

    // Developer management

    /**
     * Spawn a new developer for an agent.
     *
     * @param agentId The Claude agent ID.
     * @param colorVariant Optional color variant (auto-assigned if null).
     * @return The spawned developer, or null if no desk available.
     */
    fun spawnDeveloper(agentId: String, colorVariant: Int? = null): Developer? {
        // Find available desk
        val desk = getAvailableDesk() ?: return null

        // Assign color variant
        val variant = colorVariant ?: (developers.size % 4)

        // Find nearest whiteboard
        val whiteboard = getNearestWhiteboard(desk.x, desk.y)

        // Create developer
        val entityId = generateEntityId("dev")
        val developer = Developer(
            x = desk.x,
            y = desk.y,
            entityId = entityId,
            agentId = agentId,
            colorVariant = variant,
            walkSpeed = config.developer.walkSpeed,
            thinkingDuration = config.developer.thinkingDuration,
            despairDuration = config.developer.despairDuration
        )

        // Set up positions
        developer.setDeskPosition(desk.x, desk.y)
        whiteboard?.let {
            developer.setWhiteboardPosition(it.x, it.y)
        }

        // Set up effect spawners
        developer.setGhostSpawner { dev -> spawnGhost(dev) }
        developer.setBubbleSpawner { dev -> spawnThoughtBubble(dev) }

        // Register
        desk.occupiedBy = entityId
        developers[agentId] = developer
        developer.start()

        return developer
    }

    /**
     * Remove a developer when agent finishes.
     */
    fun removeDeveloper(agentId: String) {
        val developer = developers.remove(agentId) ?: return
        // Free up the desk
        for (desk in desks.values) {
            if (desk.occupiedBy == developer.entityId) {
                desk.occupiedBy = null
                break
            }
        }
    }

    /**
     * Get developer by agent ID.
     */
    fun getDeveloper(agentId: String): Developer? = developers[agentId]

    /**
     * Get all developers.
     */
    fun getAllDevelopers(): List<Developer> = developers.values.toList()

    private fun getAvailableDesk(): Desk? {
        return desks.values.firstOrNull { it.occupiedBy == null }
    }

    private fun getNearestWhiteboard(x: Float, y: Float): Whiteboard? {
        var nearest: Whiteboard? = null
        var minDist = Float.MAX_VALUE

        for (wb in whiteboards.values) {
            val dx = wb.x - x
            val dy = wb.y - y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < minDist) {
                minDist = dist
                nearest = wb
            }
        }

        return nearest
    }

    // PM management

    /**
     * Spawn the Project Manager.
     */
    fun spawnProjectManager(): ProjectManager {
        if (projectManager == null) {
            projectManager = ProjectManager(
                entityId = "pm",
                patrolSpeed = config.projectManager.patrolSpeed,
                interruptChance = config.projectManager.interruptChance,
                interruptDuration = config.projectManager.interruptDuration
            )

            // Set patrol path
            val patrolPoints = config.office.pmPatrolPath.map { p ->
                Pair(p.x.toFloat(), p.y.toFloat())
            }
            if (patrolPoints.isNotEmpty()) {
                projectManager?.setPatrolPath(patrolPoints)
            }
        }

        return projectManager!!
    }

    /**
     * Get the Project Manager.
     */
    fun getProjectManager(): ProjectManager? = projectManager

    // PO management

    /**
     * Spawn Project Owner to ask a question.
     *
     * @param targetAgentId Agent ID of the developer to ask.
     * @return The PO, or null if target developer not found.
     */
    fun spawnProjectOwner(targetAgentId: String): ProjectOwner? {
        val developer = developers[targetAgentId] ?: return null

        if (projectOwner == null) {
            projectOwner = ProjectOwner(
                entityId = "po",
                walkSpeed = config.projectOwner.walkSpeed,
                questionTimeout = config.projectOwner.questionTimeout
            )
        }

        projectOwner?.spawnForQuestion(developer)
        return projectOwner
    }

    /**
     * Called when user answers the question.
     */
    fun dismissProjectOwner() {
        projectOwner?.answerReceived()
    }

    /**
     * Get the Project Owner.
     */
    fun getProjectOwner(): ProjectOwner? = projectOwner

    // Effect management

    private fun spawnGhost(developer: Developer): Ghost {
        val ghost = Ghost(
            x = developer.x,
            y = developer.y,
            entityId = generateEntityId("ghost")
        )
        effects.add(ghost)
        return ghost
    }

    private fun spawnThoughtBubble(developer: Developer): ThoughtBubble {
        val bubble = ThoughtBubble(
            x = developer.x + 8,
            y = developer.y - 16,
            entityId = generateEntityId("bubble")
        )
        bubble.show()
        effects.add(bubble)
        return bubble
    }

    // Update

    /**
     * Update all entities.
     */
    fun update(dt: Float) {
        // Update developers
        for (developer in developers.values) {
            developer.update(dt)
        }

        // Update PM
        projectManager?.let { pm ->
            pm.setDevelopers(developers.values.toList())
            pm.update(dt)
        }

        // Update PO
        projectOwner?.update(dt)

        // Update effects and clean up finished ones
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            if (!effect.active) {
                iterator.remove()
            } else {
                effect.update(dt)
            }
        }
    }

    // Rendering data

    /**
     * Get all data needed for rendering.
     */
    fun getRenderData(): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "desks" to desks.values.map { d ->
                mapOf(
                    "id" to d.id,
                    "x" to d.x,
                    "y" to d.y,
                    "occupied" to (d.occupiedBy != null)
                )
            },
            "whiteboards" to whiteboards.values.map { w ->
                mapOf(
                    "id" to w.id,
                    "x" to w.x,
                    "y" to w.y
                )
            },
            "developers" to developers.values.map { dev ->
                dev.getRenderInfo()
            },
            "effects" to effects.filter { it.visible }.map { effect ->
                effect.getRenderInfo()
            }
        )

        projectManager?.let { pm ->
            data["project_manager"] = pm.getRenderInfo()
        }

        projectOwner?.let { po ->
            if (po.isActive()) {
                data["project_owner"] = po.getRenderInfo()
            }
        }

        return data
    }

    /**
     * Get all renderable entities in z-order.
     */
    fun getAllEntities(): List<BaseEntity> {
        val entities = mutableListOf<BaseEntity>()

        // Add characters (sorted by y for depth)
        val characters = mutableListOf<BaseEntity>()
        characters.addAll(developers.values)
        projectManager?.let { characters.add(it) }
        projectOwner?.let { po ->
            if (po.isActive()) {
                characters.add(po)
            }
        }

        characters.sortBy { it.y }
        entities.addAll(characters)

        // Add effects on top
        entities.addAll(effects)

        return entities
    }
}
