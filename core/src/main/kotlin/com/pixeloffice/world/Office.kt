package com.pixeloffice.world

import com.pixeloffice.core.Config
import com.pixeloffice.entities.*
import com.pixeloffice.rendering.*
import com.pixeloffice.states.DeveloperStateNames
import com.pixeloffice.ui.ColumnSettings
import com.pixeloffice.ui.SettingsConfig
import kotlin.math.sqrt
import kotlin.random.Random

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
    var occupiedBy: String? = null,
    var occupantType: String? = null
)

/**
 * A whiteboard in the office.
 */
data class Whiteboard(
    val id: String,
    val x: Float,
    val y: Float
)

private enum class PatrolPhase {
    IDLE_DELAY,
    WALKING,
    PAUSING,
    TALKING,
    RETURNING
}

private data class DeveloperPatrol(
    var phase: PatrolPhase = PatrolPhase.IDLE_DELAY,
    var idleElapsed: Float = 0f,
    var triggerAfter: Float = 0f,
    var stopsVisited: Int = 0,
    var targetDeskId: String? = null,
    var pauseRemaining: Float = 0f,
    var socialCooldown: Float = 0f,
    val visitedDeskIds: MutableSet<String> = mutableSetOf()
)

private enum class ConversationKind { DESK_VISIT, PATROL_COLLISION }

private data class DeveloperConversation(
    val firstAgentId: String,
    val secondAgentId: String,
    val visitorAgentId: String?,
    val interruptedAgentId: String?,
    val kind: ConversationKind,
    var remaining: Float
) {
    fun participants(): List<String> = listOf(firstAgentId, secondAgentId)
}

/**
 * Manages the office layout and all entities within it.
 *
 * Handles desk allocation, entity spawning, and provides
 * the world state for rendering.
 */
class Office(
    private val config: Config,
    private val projectId: String = "default",
    private val randomFloat: () -> Float = { Random.nextFloat() }
) {

    companion object {
        const val SOCIAL_COLLISION_DISTANCE = 15f
        const val SOCIAL_COLLISION_COOLDOWN_SECONDS = 2f

        private const val PET_START_SALT = 0x1872A1
        private const val CAT_RANDOM_SALT = 0xCA7001
        private const val DOG_RANDOM_SALT = 0xD06001

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
    private val patrols = mutableMapOf<String, DeveloperPatrol>()
    private val conversationsByParticipant = mutableMapOf<String, DeveloperConversation>()
    private val effects = mutableListOf<BaseEntity>() // Ghosts, bubbles, etc.
    private val pets = linkedMapOf<PetType, OfficePet>()
    private var petAnchors = emptyList<PetAnchor>()

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
        val desk = getAvailableDesk()
        if (desk == null) {
            com.badlogic.gdx.Gdx.app?.log("Office", "No available desk for agent: $agentId")
            return null
        }
        val variant = colorVariant ?: stableVariantFor(agentId)
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
        val variant = colorVariant ?: stableVariantFor(agentId)
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
            despairDuration = config.developer.despairDuration,
            interruptDuration = config.developer.socialDurationSeconds
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
        patrols[agentId] = DeveloperPatrol(triggerAfter = nextPatrolDelay())

        return developer
    }

    /**
     * Remove a developer when agent finishes.
     */
    fun removeDeveloper(agentId: String) {
        val developer = developers[agentId] ?: return
        conversationsByParticipant[agentId]?.let { endConversation(it, cancelledAgentId = agentId) }
        cancelPatrol(agentId)
        developers.remove(agentId)
        patrols.remove(agentId)
        // Release whiteboard
        developer.getAssignedWhiteboardId()?.let { whiteboardId ->
            releaseWhiteboard(whiteboardId)
        }

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

    internal fun getPatrolPhase(agentId: String): String? = patrols[agentId]?.phase?.name

    internal fun getPatrolStopsVisited(agentId: String): Int = patrols[agentId]?.stopsVisited ?: 0

    internal fun getActiveConversationCount(): Int =
        conversationsByParticipant.values.distinctBy { System.identityHashCode(it) }.size

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
     * Set up the taller desk column at its selected physical side.
     */
    fun setupDeskColumn1(
        baseX: Float = DeskColumn.LEFT_COLUMN_X,
        init: DeskColumnBuilder.() -> Unit
    ) {
        deskColumn1 = DeskColumnBuilder("deskColumn1", baseX).apply(init).build()
        registerColumnDesks(deskColumn1!!)
    }

    /**
     * Set up the shorter lounge column at its selected physical side.
     */
    fun setupDeskColumn2(
        baseX: Float = DeskColumn.RIGHT_COLUMN_X,
        init: DeskColumnBuilder.() -> Unit
    ) {
        deskColumn2 = DeskColumnBuilder("deskColumn2", baseX).apply(init).build()
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
    fun setupDefaultDeskColumns(loungeOnLeft: Boolean? = null): SettingsConfig {
        // Clear config.json desks so only column-registered desks are used
        desks.clear()

        val generated = if (loungeOnLeft == null) {
            SettingsConfig.randomizedForProject(projectId)
        } else {
            SettingsConfig.randomizedForProject(projectId, loungeOnLeft)
        }
        applyDeskColumns(generated)
        setupPets()
        return generated.deepCopy()
    }

    /**
     * Build desk columns from a SettingsConfig.
     * Shared between setupDefaultDeskColumns() and resetAndApply().
     */
    private fun applyDeskColumns(settingsConfig: SettingsConfig) {
        fun applyColumn(
            columnSettings: ColumnSettings,
            setup: (Float, DeskColumnBuilder.() -> Unit) -> Unit
        ) {
            setup(columnSettings.baseX) {
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
        val loungeBaseX = listOfNotNull(deskColumn1, deskColumn2)
            .minByOrNull { it.rows.size }
            ?.baseX
            ?: DeskColumn.RIGHT_COLUMN_X
        lineNetwork.setDeskPositions(
            navPositions,
            loungeOnLeft = loungeBaseX == DeskColumn.LEFT_COLUMN_X
        )
    }

    private fun setupPets() {
        pets.clear()
        petAnchors = emptyList()
        if (!config.pets.enabled) return

        val deskAnchors = desks.values.sortedBy { it.id }.mapNotNull { desk ->
            lineNetwork.getDeskMidpoint(desk.id)?.let { point ->
                val restingFacing = when (desk.side) {
                    DeskSide.WEST -> "right"
                    DeskSide.EAST -> "left"
                    null -> null
                }
                PetAnchor("desk:${desk.id}", point.x, point.y, restingFacing)
            }
        }
        val shortColumnBaseX = listOfNotNull(deskColumn1, deskColumn2)
            .minByOrNull { it.rows.size }
            ?.baseX
            ?: DeskColumn.RIGHT_COLUMN_X
        val loungePointIds = if (shortColumnBaseX == DeskColumn.LEFT_COLUMN_X) {
            listOf("bottom_corridor_left", "bottom_corridor_center")
        } else {
            listOf("bottom_corridor_center", "bottom_corridor_right")
        }
        val loungeAnchors = loungePointIds.mapNotNull { pointId ->
            lineNetwork.getPoint(pointId)?.let {
                PetAnchor("lounge:${pointId.removePrefix("bottom_corridor_")}", it.x, it.y)
            }
        }
        petAnchors = deskAnchors + loungeAnchors
        if (petAnchors.isEmpty()) return

        val starts = petAnchors.shuffled(projectRandom(PET_START_SALT))
        val catStart = starts.first()
        val dogStart = starts.getOrElse(1) { starts.first() }
        val catRandom = projectRandom(CAT_RANDOM_SALT)
        val dogRandom = projectRandom(DOG_RANDOM_SALT)
        pets[PetType.CAT] = OfficePet(
            PetType.CAT,
            catStart,
            pathfinder,
            config.pets,
            catRandom::nextFloat
        )
        pets[PetType.DOG] = OfficePet(
            PetType.DOG,
            dogStart,
            pathfinder,
            config.pets,
            dogRandom::nextFloat
        )
    }

    private fun projectRandom(salt: Int): Random = Random(31 * projectId.hashCode() + salt)

    private fun updatePets(dt: Float) {
        for (pet in pets.values) {
            pet.update(dt)
            if (!pet.isReadyToRoam()) continue

            val occupiedByOtherPets = pets.values
                .filterNot { it === pet }
                .flatMap { listOfNotNull(it.currentAnchorId, it.targetAnchorId) }
                .toSet()
            val candidates = petAnchors.filter {
                it.id != pet.currentAnchorId && it.id !in occupiedByOtherPets
            }
            if (candidates.isNotEmpty()) {
                pet.startRoam(candidates[pet.chooseIndex(candidates.size)])
            }
        }
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

        // Clear effects and occupied whiteboards
        effects.clear()
        occupiedWhiteboards.clear()

        // Clear all desks — column desks will be re-registered below
        desks.clear()

        // Rebuild desk columns from settings
        applyDeskColumns(settingsConfig)
        setupPets()

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
        return bubble
    }

    // Update

    /**
     * Update all entities.
     */
    fun update(dt: Float) {
        for (developer in developers.values) {
            developer.update(dt)
        }

        updatePets(dt)

        updateConversations(dt)
        updatePatrols(dt)
        checkPatrollerCollisions()

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

    /** Cancel transient social behavior before applying an authoritative agent event. */
    fun beforeDeveloperAgentEvent(agentId: String) {
        conversationsByParticipant[agentId]?.let {
            endConversation(it, cancelledAgentId = agentId)
        }
        cancelPatrol(agentId)
    }

    private fun stableVariantFor(agentId: String): Int {
        val count = config.sprites.colorVariants.size.coerceAtLeast(1)
        return Math.floorMod(agentId.hashCode(), count)
    }

    private fun unitRandom(): Float = randomFloat().coerceIn(0f, 0.999999f)

    private fun nextPatrolDelay(): Float {
        val min = minOf(config.developer.idlePatrolMinSeconds, config.developer.idlePatrolMaxSeconds)
            .coerceAtLeast(0f)
        val max = maxOf(config.developer.idlePatrolMinSeconds, config.developer.idlePatrolMaxSeconds)
            .coerceAtLeast(min)
        return min + (max - min) * unitRandom()
    }

    private fun updatePatrols(dt: Float) {
        for ((agentId, patrol) in patrols.toMap()) {
            val developer = developers[agentId] ?: continue
            patrol.socialCooldown = (patrol.socialCooldown - dt).coerceAtLeast(0f)

            when (patrol.phase) {
                PatrolPhase.IDLE_DELAY -> {
                    if (developer.getState() == DeveloperStateNames.IDLE &&
                        developer.isAtDesk() &&
                        agentId !in conversationsByParticipant
                    ) {
                        patrol.idleElapsed += dt
                        if (patrol.idleElapsed >= patrol.triggerAfter) {
                            startPatrol(agentId, patrol)
                        }
                    } else {
                        patrol.idleElapsed = 0f
                    }
                }
                PatrolPhase.WALKING -> {
                    if (developer.getState() != DeveloperStateNames.IDLE) {
                        cancelPatrol(agentId)
                    } else if (developer.hasReachedTarget()) {
                        arriveAtPatrolStop(agentId, patrol)
                    }
                }
                PatrolPhase.PAUSING -> {
                    patrol.pauseRemaining -= dt
                    if (patrol.pauseRemaining <= 0f) {
                        developer.showThoughtBubble(false)
                        continuePatrol(agentId, patrol)
                    }
                }
                PatrolPhase.TALKING -> Unit // Conversation timing is coordinated separately.
                PatrolPhase.RETURNING -> {
                    if (developer.getState() != DeveloperStateNames.IDLE) {
                        cancelPatrol(agentId)
                    } else if (developer.hasReachedTarget()) {
                        finishPatrol(agentId, patrol)
                    }
                }
            }
        }
    }

    private fun startPatrol(agentId: String, patrol: DeveloperPatrol) {
        patrol.stopsVisited = 0
        patrol.visitedDeskIds.clear()
        patrol.targetDeskId = null
        moveToNextPatrolStop(agentId, patrol)
    }

    private fun moveToNextPatrolStop(agentId: String, patrol: DeveloperPatrol) {
        if (patrol.stopsVisited >= config.developer.idlePatrolMaxStops.coerceAtLeast(0)) {
            beginReturnToDesk(agentId, patrol)
            return
        }

        val developer = developers[agentId] ?: return
        val target = choosePatrolDesk(agentId, patrol)
        if (target == null) {
            beginReturnToDesk(agentId, patrol)
            return
        }

        patrol.phase = PatrolPhase.WALKING
        patrol.targetDeskId = target.id
        developer.setMovementPaused(false)
        developer.setAnimation("walking")
        if (developer.isAtDesk() && patrol.stopsVisited == 0) {
            developer.walkFromDeskWithPathfinding(target.x, target.y)
        } else {
            developer.walkToWithPathfinding(target.x, target.y)
        }
    }

    private fun choosePatrolDesk(agentId: String, patrol: DeveloperPatrol): Desk? {
        val homeDeskId = deskForDeveloper(agentId)?.id
        val candidates = desks.values.filter {
            it.id != homeDeskId && it.id !in patrol.visitedDeskIds
        }
        if (candidates.isEmpty()) return null

        val talkable = candidates.filter { desk ->
            val target = developerAtDesk(desk)
            target != null &&
                target.agentId != agentId &&
                target.isAtDesk() &&
                target.agentId !in conversationsByParticipant
        }
        val pool = talkable.ifEmpty { candidates }
        return pool[(unitRandom() * pool.size).toInt().coerceIn(0, pool.lastIndex)]
    }

    private fun arriveAtPatrolStop(agentId: String, patrol: DeveloperPatrol) {
        val developer = developers[agentId] ?: return
        val desk = patrol.targetDeskId?.let(desks::get)
        desk?.let { patrol.visitedDeskIds.add(it.id) }
        patrol.stopsVisited++
        developer.stopWalking()
        developer.setAnimation("idle")

        val target = desk?.let(::developerAtDesk)?.takeIf {
            it.agentId != agentId &&
                it.isAtDesk() &&
                it.agentId !in conversationsByParticipant
        }
        if (target != null) {
            startDeskConversation(agentId, target.agentId, patrol)
        } else {
            patrol.phase = PatrolPhase.PAUSING
            patrol.pauseRemaining = config.developer.socialDurationSeconds.coerceAtLeast(0f)
            developer.showBubbleOfType(if (unitRandom() < 0.5f) "thinking" else "question")
        }
    }

    private fun startDeskConversation(
        visitorAgentId: String,
        targetAgentId: String,
        patrol: DeveloperPatrol
    ) {
        val visitor = developers[visitorAgentId] ?: return
        val target = developers[targetAgentId] ?: return
        val conversation = DeveloperConversation(
            firstAgentId = visitorAgentId,
            secondAgentId = targetAgentId,
            visitorAgentId = visitorAgentId,
            interruptedAgentId = targetAgentId,
            kind = ConversationKind.DESK_VISIT,
            remaining = config.developer.socialDurationSeconds.coerceAtLeast(0f)
        )
        patrol.phase = PatrolPhase.TALKING
        visitor.setMovementPaused(true)
        visitor.showBubbleOfType(if (unitRandom() < 0.5f) "blah" else "question")
        target.beginInterruption()
        patrols[targetAgentId]?.let {
            it.idleElapsed = 0f
            it.triggerAfter = nextPatrolDelay()
        }
        conversationsByParticipant[visitorAgentId] = conversation
        conversationsByParticipant[targetAgentId] = conversation
    }

    private fun continuePatrol(agentId: String, patrol: DeveloperPatrol) {
        if (patrol.stopsVisited >= config.developer.idlePatrolMaxStops.coerceAtLeast(0)) {
            beginReturnToDesk(agentId, patrol)
        } else {
            moveToNextPatrolStop(agentId, patrol)
        }
    }

    private fun beginReturnToDesk(agentId: String, patrol: DeveloperPatrol) {
        val developer = developers[agentId] ?: return
        val desk = deskForDeveloper(agentId)
        if (desk == null || developer.isAtDesk()) {
            finishPatrol(agentId, patrol)
            return
        }
        patrol.phase = PatrolPhase.RETURNING
        patrol.targetDeskId = desk.id
        developer.setMovementPaused(false)
        developer.setAnimation("walking")
        developer.walkToWithPathfinding(desk.x, desk.y)
    }

    private fun finishPatrol(agentId: String, patrol: DeveloperPatrol) {
        developers[agentId]?.let {
            it.setMovementPaused(false)
            it.stopWalking()
            it.showThoughtBubble(false)
            it.setAnimation("idle")
        }
        resetPatrol(patrol)
    }

    private fun resetPatrol(patrol: DeveloperPatrol) {
        patrol.phase = PatrolPhase.IDLE_DELAY
        patrol.idleElapsed = 0f
        patrol.triggerAfter = nextPatrolDelay()
        patrol.stopsVisited = 0
        patrol.targetDeskId = null
        patrol.pauseRemaining = 0f
        patrol.visitedDeskIds.clear()
    }

    private fun cancelPatrol(agentId: String) {
        val patrol = patrols[agentId] ?: return
        developers[agentId]?.let {
            it.setMovementPaused(false)
            it.stopWalking()
            it.showThoughtBubble(false)
        }
        resetPatrol(patrol)
    }

    private fun updateConversations(dt: Float) {
        val conversations = conversationsByParticipant.values.toSet()
        for (conversation in conversations) {
            if (conversation.participants().any { it !in developers }) {
                endConversation(conversation)
                continue
            }
            conversation.remaining -= dt
            if (conversation.remaining <= 0f) {
                endConversation(conversation)
            }
        }
    }

    private fun endConversation(
        conversation: DeveloperConversation,
        cancelledAgentId: String? = null
    ) {
        if (conversation.participants().none { conversationsByParticipant[it] === conversation }) return

        conversation.participants().forEach { conversationsByParticipant.remove(it) }
        conversation.participants().forEach { agentId ->
            developers[agentId]?.let { developer ->
                developer.showThoughtBubble(false)
                developer.setMovementPaused(false)
            }
        }
        conversation.interruptedAgentId
            ?.takeIf { it != cancelledAgentId }
            ?.let { developers[it]?.endInterruption() }

        when (conversation.kind) {
            ConversationKind.DESK_VISIT -> {
                val visitorId = conversation.visitorAgentId ?: return
                val patrol = patrols[visitorId] ?: return
                if (visitorId != cancelledAgentId &&
                    developers[visitorId]?.getState() == DeveloperStateNames.IDLE
                ) {
                    continuePatrol(visitorId, patrol)
                }
            }
            ConversationKind.PATROL_COLLISION -> {
                for (agentId in conversation.participants()) {
                    val patrol = patrols[agentId] ?: continue
                    patrol.socialCooldown = SOCIAL_COLLISION_COOLDOWN_SECONDS
                    if (agentId != cancelledAgentId &&
                        developers[agentId]?.getState() == DeveloperStateNames.IDLE
                    ) {
                        patrol.phase = PatrolPhase.WALKING
                        developers[agentId]?.setAnimation("walking")
                    }
                }
            }
        }
    }

    private fun checkPatrollerCollisions() {
        val walking = patrols.entries.filter { (agentId, patrol) ->
            patrol.phase == PatrolPhase.WALKING &&
                patrol.socialCooldown <= 0f &&
                agentId !in conversationsByParticipant
        }
        for (firstIndex in walking.indices) {
            for (secondIndex in firstIndex + 1 until walking.size) {
                val firstId = walking[firstIndex].key
                val secondId = walking[secondIndex].key
                if (firstId in conversationsByParticipant || secondId in conversationsByParticipant) continue
                val first = developers[firstId] ?: continue
                val second = developers[secondId] ?: continue
                if (first.distanceTo(second) >= SOCIAL_COLLISION_DISTANCE) continue

                val conversation = DeveloperConversation(
                    firstAgentId = firstId,
                    secondAgentId = secondId,
                    visitorAgentId = null,
                    interruptedAgentId = null,
                    kind = ConversationKind.PATROL_COLLISION,
                    remaining = config.developer.socialDurationSeconds.coerceAtLeast(0f)
                )
                walking[firstIndex].value.phase = PatrolPhase.TALKING
                walking[secondIndex].value.phase = PatrolPhase.TALKING
                first.setMovementPaused(true)
                second.setMovementPaused(true)
                first.setAnimation("idle")
                second.setAnimation("idle")
                first.showBubbleOfType("blah")
                second.showBubbleOfType("blah")
                conversationsByParticipant[firstId] = conversation
                conversationsByParticipant[secondId] = conversation
            }
        }
    }

    private fun deskForDeveloper(agentId: String): Desk? {
        val entityId = developers[agentId]?.entityId ?: return null
        return desks.values.firstOrNull { it.occupiedBy == entityId }
    }

    private fun developerAtDesk(desk: Desk): Developer? {
        val entityId = desk.occupiedBy ?: return null
        return developers.values.firstOrNull { it.entityId == entityId }
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
            pets = pets.values.map { it.getTypedRenderInfo() },
            effects = effects.filter { it.visible }.map { it.toEffectRenderInfo() },
            deskColumns = columns,
            accentPalette = OfficeAccentPalette.forProject(projectId)
        )
    }

    /**
     * Get the line network for debug rendering.
     */
    fun getLineNetwork(): LineNetwork = lineNetwork

    internal fun getPets(): List<OfficePet> = pets.values.toList()

    /**
     * Get all renderable entities in z-order.
     */
    fun getAllEntities(): List<BaseEntity> {
        val entities = mutableListOf<BaseEntity>()

        // Add characters (sorted by y for depth)
        val characters = mutableListOf<BaseEntity>()
        characters.addAll(developers.values)
        characters.addAll(pets.values)
        characters.sortBy { it.y }
        entities.addAll(characters)

        // Add effects on top
        entities.addAll(effects)

        return entities
    }
}
