package com.pixeloffice.world

import com.pixeloffice.core.Config
import com.pixeloffice.entities.*
import com.pixeloffice.parsing.ActivityType
import com.pixeloffice.rendering.*
import com.pixeloffice.ui.ColumnSettings
import com.pixeloffice.ui.SettingsConfig
import kotlin.math.sqrt

/**
 * A desk in the office.
 */
data class Desk(
    val id: String,
    val x: Float,
    val y: Float,
    val chairX: Float = x,
    val chairY: Float = y,
    val side: DeskSide? = null,
    var occupiedBy: String? = null,      // Entity ID (any character type)
    var occupantType: String? = null     // "developer", "project_manager", "product_owner"
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

    companion object {
        // PM/PO default spawn positions (upper corridor)
        const val PM_START_X = 64f
        const val PM_START_Y = 110f
        const val PO_PATROL_START_X = 150f
        const val PO_PATROL_START_Y = 110f

        // Collision detection
        const val MANAGER_COLLISION_DIST = 15f

        // Bubble Y offset above entity
        const val BUBBLE_Y_OFFSET = -21f
    }

    // Layout
    private val desks = mutableMapOf<String, Desk>()
    private val whiteboards = mutableMapOf<String, Whiteboard>()
    private val occupiedWhiteboards = mutableSetOf<String>()
    private val tileSize = config.office.tileSize

    // Desk columns (data-driven desk configuration)
    var deskColumn1: DeskColumn? = null
        private set
    var deskColumn2: DeskColumn? = null
        private set

    // Named location registry (maps string names to NavPoints for routing)
    private val namedLocations = mutableMapOf<String, NavPoint>()

    // Entities
    private val developers = mutableMapOf<String, Developer>()
    private var projectManager: ProjectManager? = null
    private var productOwner: ProductOwner? = null
    private val effects = mutableListOf<BaseEntity>() // Ghosts, bubbles, etc.

    // Per-agent activity tracking
    private val activityTracker = AgentActivityTracker()

    // Entity ID counter
    private var nextEntityId = 0

    // Line-based pathfinder for developer navigation
    private val lineNetwork = LineNetwork(config)
    private val pathfinder: Pathfinder = LinePathfinder(lineNetwork)

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
        val desk = getAvailableDesk() ?: return null
        val variant = colorVariant ?: (developers.size % 4)
        return setupDeveloperAtDesk(agentId, desk, variant)
    }

    /**
     * Spawn a new developer for an agent at a specific desk.
     *
     * @param agentId The Claude agent ID.
     * @param deskId The desk ID to assign the developer to.
     * @param colorVariant Optional color variant (auto-assigned if null).
     * @return The spawned developer, or null if desk not available.
     */
    fun assignDeveloperToDesk(agentId: String, deskId: String, colorVariant: Int? = null): Developer? {
        val desk = getDeskById(deskId) ?: return null
        if (!isDeskAvailable(deskId)) return null
        val variant = colorVariant ?: (developers.size % 4)
        return setupDeveloperAtDesk(agentId, desk, variant)
    }

    private fun setupDeveloperAtDesk(agentId: String, desk: Desk, colorVariant: Int): Developer {
        val whiteboard = getNearestWhiteboard(desk.x, desk.y)
        val entityId = generateEntityId("dev")
        val developer = Developer(
            x = desk.x,
            y = desk.y,
            entityId = entityId,
            agentId = agentId,
            colorVariant = colorVariant,
            walkSpeed = config.developer.walkSpeed,
            thinkingDuration = config.developer.thinkingDuration,
            despairDuration = config.developer.despairDuration
        )

        developer.setDeskPosition(desk.x, desk.y)
        developer.setChairPosition(desk.chairX, desk.chairY)
        developer.setDeskFacing(facingForDeskSide(desk))
        pathfinder.getDeskMidpoint(desk.id)?.let { (mx, my) -> developer.setDeskMidpoint(mx, my) }
        whiteboard?.let {
            developer.setWhiteboardPosition(it.x, it.y, it.id)
            claimWhiteboard(it.id)
        }
        developer.setOffice(this)
        developer.setPathfinder(pathfinder)
        developer.setGhostSpawner { dev -> spawnGhost(dev) }
        developer.setBubbleSpawner { entity, bubbleType -> spawnThoughtBubble(entity, bubbleType) }

        desk.occupiedBy = entityId
        desk.occupantType = "developer"
        developers[agentId] = developer
        developer.start()

        return developer
    }

    /**
     * Remove a developer when agent finishes.
     */
    fun removeDeveloper(agentId: String) {
        val developer = developers.remove(agentId) ?: return
        activityTracker.removeAgent(agentId)
        // Free up the desk
        for (desk in desks.values) {
            if (desk.occupiedBy == developer.entityId) {
                desk.occupiedBy = null
                desk.occupantType = null
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

    // Activity tracking

    fun getActivityTracker(): AgentActivityTracker = activityTracker

    fun recordAgentActivity(agentId: String, type: ActivityType, toolName: String? = null, context: String? = null) {
        activityTracker.recordActivity(agentId, type, toolName, context)
    }

    fun getAgentActivity(agentId: String): ActivityRecord? = activityTracker.getCurrentActivity(agentId)

    private fun getAvailableDesk(): Desk? {
        return desks.values.firstOrNull { it.occupiedBy == null }
    }

    /**
     * Get the next available (unoccupied) desk ID.
     */
    fun getNextAvailableDeskId(): String? {
        return desks.values.firstOrNull { it.occupiedBy == null }?.id
    }

    // Desk assignment API

    /**
     * Get a desk by its ID.
     */
    fun getDeskById(deskId: String): Desk? = desks[deskId]

    /**
     * Check if a desk is available for assignment.
     */
    fun isDeskAvailable(deskId: String): Boolean {
        val desk = desks[deskId] ?: return false
        return desk.occupiedBy == null
    }

    /**
     * Unassign a desk, freeing it for other entities.
     */
    fun unassignDesk(deskId: String) {
        val desk = desks[deskId] ?: return
        desk.occupiedBy = null
        desk.occupantType = null
    }

    /**
     * Assign the Project Manager to a specific desk.
     *
     * @param deskId The desk ID to assign the PM to.
     * @return The PM, or null if desk not available.
     */
    fun assignPMToDesk(deskId: String): ProjectManager? {
        val desk = getDeskById(deskId) ?: return null
        if (!isDeskAvailable(deskId)) return null

        // Create or get PM
        val pm = projectManager ?: ProjectManager(
            entityId = "pm",
            patrolSpeed = config.projectManager.patrolSpeed,
            interruptChance = config.projectManager.interruptChance,
            interruptDuration = config.projectManager.interruptDuration
        )

        if (projectManager == null) {
            projectManager = pm
            pm.setPathfinder(pathfinder)
            pm.setBubbleSpawner { entity, bubbleType ->
                spawnThoughtBubble(entity, bubbleType)
            }
        }

        // Assign to desk
        pm.setAssignedDesk(deskId, desk.x, desk.y, desk.chairX, desk.chairY)
        pm.setDeskFacing(facingForDeskSide(desk))
        pathfinder.getDeskMidpoint(deskId)?.let { (mx, my) -> pm.setDeskMidpoint(mx, my) }
        pm.x = desk.x
        pm.y = desk.y

        // Mark desk as occupied
        desk.occupiedBy = pm.entityId
        desk.occupantType = "project_manager"

        // Set desk targets for patrol and developer references
        val deskList = desks.values.map { d -> Triple(d.id, d.x, d.y) }
        pm.setDeskTargets(deskList, startPatrol = false)
        pm.setDevelopers(developers.values.toList())

        return pm
    }

    /**
     * Assign the Product Owner to a specific desk.
     *
     * @param deskId The desk ID to assign the PO to.
     * @return The PO, or null if desk not available.
     */
    fun assignPOToDesk(deskId: String): ProductOwner? {
        val desk = getDeskById(deskId) ?: return null
        if (!isDeskAvailable(deskId)) return null

        // Create or get PO
        val po = productOwner ?: ProductOwner(
            entityId = "po",
            walkSpeed = config.productOwner.walkSpeed,
            questionTimeout = config.productOwner.questionTimeout
        )

        if (productOwner == null) {
            productOwner = po
            po.setPathfinder(pathfinder)
            po.setBubbleSpawner { entity, bubbleType ->
                spawnThoughtBubble(entity, bubbleType)
            }
        }

        // Assign to desk
        po.setAssignedDesk(deskId, desk.x, desk.y, desk.chairX, desk.chairY)
        po.setDeskFacing(facingForDeskSide(desk))
        pathfinder.getDeskMidpoint(deskId)?.let { (mx, my) -> po.setDeskMidpoint(mx, my) }
        po.x = desk.x
        po.y = desk.y
        po.active = true
        po.visible = true

        // Mark desk as occupied
        desk.occupiedBy = po.entityId
        desk.occupantType = "product_owner"

        // Set desk targets for patrol and developer references
        val deskList = desks.values.map { d -> Triple(d.id, d.x, d.y) }
        po.setDeskTargets(deskList)
        po.setDevelopers(developers.values.toList())

        return po
    }

    private fun facingForDeskSide(desk: Desk): String {
        return when (desk.side) {
            DeskSide.EAST -> "left"
            DeskSide.WEST -> "right"
            else -> "down"
        }
    }

    private fun getNearestWhiteboard(x: Float, y: Float): Whiteboard? {
        var nearest: Whiteboard? = null
        var minDist = Float.MAX_VALUE

        for (wb in whiteboards.values) {
            // Skip occupied whiteboards
            if (occupiedWhiteboards.contains(wb.id)) continue

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

    fun claimWhiteboard(whiteboardId: String) {
        occupiedWhiteboards.add(whiteboardId)
    }

    fun releaseWhiteboard(whiteboardId: String) {
        occupiedWhiteboards.remove(whiteboardId)
    }

    // Desk Column management

    /**
     * Set up desk column 1 (west/left side, baseX = 45f) using the DSL.
     */
    fun setupDeskColumn1(init: DeskColumnBuilder.() -> Unit) {
        deskColumn1 = DeskColumnBuilder("deskColumn1", DeskColumn.LEFT_COLUMN_X).apply(init).build()
        registerColumnDesks(deskColumn1!!)
    }

    /**
     * Set up desk column 2 (east/right side, baseX = 175f) using the DSL.
     */
    fun setupDeskColumn2(init: DeskColumnBuilder.() -> Unit) {
        deskColumn2 = DeskColumnBuilder("deskColumn2", DeskColumn.RIGHT_COLUMN_X).apply(init).build()
        registerColumnDesks(deskColumn2!!)
    }

    /**
     * Register desks from a DeskColumn into the desks map for occupancy tracking.
     */
    private fun registerColumnDesks(column: DeskColumn) {
        for ((rowIndex, row) in column.rows.withIndex()) {
            row.westDesk?.let {
                val deskId = column.getDeskId((rowIndex + 1) * 2) // even = west
                val (x, y) = DeskColumn.getDeskPosition(column.baseX, rowIndex, isLeftDesk = true)
                val (cx, cy) = DeskColumn.getChairPosition(column.baseX, rowIndex, isLeftDesk = true)
                desks[deskId] = Desk(id = deskId, x = x, y = y, chairX = cx, chairY = cy, side = DeskSide.WEST)
                registerNamedLocation(deskId, x, y)
            }
            row.eastDesk?.let {
                val deskId = column.getDeskId((rowIndex + 1) * 2 - 1) // odd = east
                val (x, y) = DeskColumn.getDeskPosition(column.baseX, rowIndex, isLeftDesk = false)
                val (cx, cy) = DeskColumn.getChairPosition(column.baseX, rowIndex, isLeftDesk = false)
                desks[deskId] = Desk(id = deskId, x = x, y = y, chairX = cx, chairY = cy, side = DeskSide.EAST)
                registerNamedLocation(deskId, x, y)
            }
        }
    }

    /**
     * Set up default desk columns that reproduce the current hardcoded furniture layout.
     * Delegates to SettingsConfig.fromDefaults() as the single source of truth.
     */
    fun setupDefaultDeskColumns() {
        // Clear config.json desks so only column-registered desks are used
        desks.clear()

        val defaults = SettingsConfig.fromDefaults()
        applyDeskColumns(defaults)
    }

    /**
     * Build desk columns from a SettingsConfig.
     * Shared between setupDefaultDeskColumns() and resetAndApply().
     */
    private fun applyDeskColumns(settingsConfig: SettingsConfig) {
        fun applyColumn(
            columnSettings: ColumnSettings,
            setup: (DeskColumnBuilder.() -> Unit) -> Unit
        ) {
            setup {
                for (rowSetting in columnSettings.rows) {
                    row(wallY = rowSetting.wallY) {
                        rowSetting.westDesk?.let { ds ->
                            if (ds.enabled) {
                                westDesk {
                                    equipment = ds.equipment
                                    chairColor = ds.chairColor
                                    wallDecor = ds.wallDecor
                                    deskItems = ds.deskItems
                                }
                            }
                        }
                        rowSetting.eastDesk?.let { ds ->
                            if (ds.enabled) {
                                eastDesk {
                                    equipment = ds.equipment
                                    chairColor = ds.chairColor
                                    wallDecor = ds.wallDecor
                                    deskItems = ds.deskItems
                                }
                            }
                        }
                    }
                }
            }
        }

        applyColumn(settingsConfig.column1, ::setupDeskColumn1)
        applyColumn(settingsConfig.column2, ::setupDeskColumn2)

        // Feed registered desk positions into the line network for pathfinding
        syncDeskPositionsToLineNetwork()
    }

    /**
     * Push current desk positions into the LineNetwork so pathfinding
     * uses the DeskColumn-registered positions instead of config.json.
     */
    private fun syncDeskPositionsToLineNetwork() {
        val navPositions = desks.values.map { DeskNavPosition(it.id, it.x, it.y) }
        lineNetwork.setDeskPositions(navPositions)
    }

    /**
     * Clear all entities and rebuild the office from a SettingsConfig.
     */
    fun resetAndApply(settingsConfig: SettingsConfig) {
        // Clear all developers
        for (agentId in developers.keys.toList()) {
            removeDeveloper(agentId)
        }
        developers.clear()
        activityTracker.clear()

        // Clear PM/PO
        projectManager?.let { pm ->
            pm.getAssignedDeskId()?.let { unassignDesk(it) }
        }
        projectManager = null

        productOwner?.let { po ->
            po.getAssignedDeskId()?.let { unassignDesk(it) }
        }
        productOwner = null

        // Clear effects and occupied whiteboards
        effects.clear()
        occupiedWhiteboards.clear()

        // Clear all desks — column desks will be re-registered below
        desks.clear()

        // Rebuild desk columns from settings
        applyDeskColumns(settingsConfig)

        // Spawn developers
        for (devSetting in settingsConfig.developers) {
            if (devSetting.agentId.isBlank()) continue
            if (devSetting.assignedColumnId != null && devSetting.assignedDeskNumber != null) {
                assignDeveloperToColumnDesk(
                    devSetting.agentId,
                    devSetting.assignedColumnId!!,
                    devSetting.assignedDeskNumber!!,
                    devSetting.colorVariant
                )
            } else {
                spawnDeveloper(devSetting.agentId, devSetting.colorVariant)
            }
        }

        // Spawn PM
        if (settingsConfig.spawnPM) {
            if (settingsConfig.pmDeskId != null) {
                assignPMToDesk(settingsConfig.pmDeskId!!)
            } else {
                spawnProjectManager()
            }
        }

        // Spawn PO
        if (settingsConfig.spawnPO) {
            if (settingsConfig.poDeskId != null) {
                assignPOToDesk(settingsConfig.poDeskId!!)
            } else {
                spawnProductOwnerPatrol()
            }
        }
    }

    // Named location registry

    /**
     * Register a named location for routing.
     * Snaps the location to the nearest NavPoint in the line network.
     */
    fun registerNamedLocation(name: String, x: Float, y: Float) {
        val navPoint = lineNetwork.getOrCreateNamedPoint(name, x, y)
        if (navPoint != null) {
            namedLocations[name] = navPoint
        }
    }

    /**
     * Get a named location NavPoint for routing.
     */
    fun getNamedLocation(name: String): NavPoint? = namedLocations[name]

    /**
     * Assign a developer to a desk using the column API.
     *
     * @param agentId The Claude agent ID.
     * @param columnId "deskColumn1" or "deskColumn2"
     * @param deskNumber Desk number within the column (1-6).
     * @param colorVariant Optional color variant.
     * @return The spawned developer, or null if desk not available.
     */
    fun assignDeveloperToColumnDesk(
        agentId: String,
        columnId: String,
        deskNumber: Int,
        colorVariant: Int? = null
    ): Developer? {
        val column = when (columnId) {
            "deskColumn1" -> deskColumn1
            "deskColumn2" -> deskColumn2
            else -> null
        } ?: return null

        val deskId = column.getDeskId(deskNumber)
        return assignDeveloperToDesk(agentId, deskId, colorVariant)
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

            // Set up pathfinder for navigation
            projectManager?.setPathfinder(pathfinder)

            // Set desk targets for patrol
            val deskList = desks.values.map { desk ->
                Triple(desk.id, desk.x, desk.y)
            }
            projectManager?.setDeskTargets(deskList)

            // Set up bubble spawner for PM
            projectManager?.setBubbleSpawner { entity, bubbleType ->
                spawnThoughtBubble(entity, bubbleType)
            }

            // Set initial position to upper corridor
            projectManager?.x = PM_START_X
            projectManager?.y = PM_START_Y
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
    fun spawnProductOwner(targetAgentId: String): ProductOwner? {
        val developer = developers[targetAgentId] ?: return null

        if (productOwner == null) {
            productOwner = ProductOwner(
                entityId = "po",
                walkSpeed = config.productOwner.walkSpeed,
                questionTimeout = config.productOwner.questionTimeout
            )

            // Configure patrol (like ProjectManager)
            productOwner?.setPathfinder(pathfinder)
            val deskList = desks.values.map { desk -> Triple(desk.id, desk.x, desk.y) }
            productOwner?.setDeskTargets(deskList)
            productOwner?.setBubbleSpawner { entity, bubbleType -> spawnThoughtBubble(entity, bubbleType) }
        }

        productOwner?.spawnForQuestion(developer)
        return productOwner
    }

    /**
     * Spawn Product Owner for patrol only (no question).
     */
    fun spawnProductOwnerPatrol(): ProductOwner? {
        if (productOwner == null) {
            productOwner = ProductOwner(
                x = PO_PATROL_START_X,
                y = PO_PATROL_START_Y,
                entityId = "po",
                walkSpeed = config.productOwner.walkSpeed,
                questionTimeout = config.productOwner.questionTimeout
            )
            productOwner?.setPathfinder(pathfinder)
            val deskList = desks.values.map { desk -> Triple(desk.id, desk.x, desk.y) }
            productOwner?.setDeskTargets(deskList)
            productOwner?.setBubbleSpawner { entity, bubbleType -> spawnThoughtBubble(entity, bubbleType) }
        }
        productOwner?.startPatrol()
        return productOwner
    }

    /**
     * Called when user answers the question.
     */
    fun dismissProductOwner() {
        productOwner?.answerReceived()
    }

    /**
     * Get the Product Owner.
     */
    fun getProductOwner(): ProductOwner? = productOwner

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

    private fun spawnThoughtBubble(entity: BaseEntity, bubbleType: String): ThoughtBubble {
        val bubble = ThoughtBubble(
            x = entity.x + 8,
            y = entity.y + BUBBLE_Y_OFFSET,
            entityId = generateEntityId("bubble"),
            bubbleType = bubbleType
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
        productOwner?.let { po ->
            po.setDevelopers(developers.values.toList())
            po.update(dt)
        }

        // Check PM/PO collision for chatting
        checkManagerCollision()

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

    /**
     * Check if PM and PO collide while walking, triggering a chat.
     */
    private fun checkManagerCollision() {
        val pm = projectManager ?: return
        val po = productOwner ?: return

        // Only check if both are walking
        if (!pm.isWalking() || !po.isWalking()) return

        // Check distance
        val dx = pm.x - po.x
        val dy = pm.y - po.y
        val distSq = dx * dx + dy * dy
        if (distSq < MANAGER_COLLISION_DIST * MANAGER_COLLISION_DIST) {
            pm.startChatting()
            po.startChatting()
        }
    }

    // Rendering data

    /**
     * Get all data needed for rendering.
     */
    fun getRenderData(): RenderData {
        val columns = mutableListOf<DeskColumn>()
        deskColumn1?.let { columns.add(it) }
        deskColumn2?.let { columns.add(it) }

        return RenderData(
            desks = desks.values.map { d ->
                DeskRenderInfo(d.id, d.x, d.y, d.occupiedBy != null)
            },
            whiteboards = whiteboards.values.map { w ->
                WhiteboardRenderInfo(w.id, w.x, w.y)
            },
            developers = developers.values.map { it.getTypedRenderInfo() },
            effects = effects.filter { it.visible }.map { it.toEffectRenderInfo() },
            deskColumns = columns,
            projectManager = projectManager?.getTypedRenderInfo(),
            productOwner = productOwner?.takeIf { it.isActive() }?.getTypedRenderInfo()
        )
    }

    /**
     * Get the line network for debug rendering.
     */
    fun getLineNetwork(): LineNetwork = lineNetwork

    /**
     * Get all renderable entities in z-order.
     */
    fun getAllEntities(): List<BaseEntity> {
        val entities = mutableListOf<BaseEntity>()

        // Add characters (sorted by y for depth)
        val characters = mutableListOf<BaseEntity>()
        characters.addAll(developers.values)
        projectManager?.let { characters.add(it) }
        productOwner?.let { po ->
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
