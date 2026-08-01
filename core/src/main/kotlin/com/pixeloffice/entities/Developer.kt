package com.pixeloffice.entities

import com.pixeloffice.PixelOfficeGame
import com.pixeloffice.rendering.CharacterRenderInfo
import com.pixeloffice.rendering.EffectRenderInfo
import com.pixeloffice.states.DeveloperStateMachine
import com.pixeloffice.states.DeveloperStateNames
import com.pixeloffice.world.Office
import com.pixeloffice.world.Pathfinder
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A developer sprite representing a Claude agent.
 *
 * Developers have state machines controlling their behavior,
 * can walk around the office, type at desks, think at whiteboards,
 * and despair when tests fail.
 */
class Developer(
    x: Float = 0f,
    y: Float = 0f,
    entityId: String = "",
    val agentId: String = "",
    colorVariant: Int = 0,
    private val walkSpeed: Float = 40f,
    despairDuration: Float = 2f,
    interruptDuration: Float = 3f
) : BaseEntity(x, y, entityId) {

    init {
        spriteVariant = colorVariant
    }

    // Position references
    private var deskPosition: Pair<Float, Float>? = null
    private var chairPosition: Pair<Float, Float>? = null
    private var whiteboardPosition: Pair<Float, Float>? = null
    private var assignedWhiteboardId: String? = null
    private var office: Office? = null

    // Walk target
    private var walkTarget: Pair<Float, Float>? = null
    private var reachedTarget = false
    private var movementPaused = false

    // Waypoint-based pathfinding
    private var waypointPath: MutableList<Pair<Float, Float>> = mutableListOf()
    private var currentWaypointIndex = 0
    private var pathfinder: Pathfinder? = null
    private var deskArrivalState = DeveloperStateNames.WRITING_CODE

    // State machine
    private val stateMachine = DeveloperStateMachine(
        this,
        despairDuration = despairDuration,
        interruptDuration = interruptDuration
    )

    // Child entities
    private var thoughtBubble: ThoughtBubble? = null
    private var ghost: Ghost? = null
    private var showBubble = false

    // Callbacks for creating child entities
    var onSpawnGhost: ((Developer) -> Ghost)? = null
    var onSpawnBubble: ((BaseEntity, String) -> ThoughtBubble)? = null

    /**
     * Initialize the state machine.
     */
    fun start() {
        stateMachine.start()
    }

    override fun update(dt: Float) {
        if (!active) return

        // Update state machine
        stateMachine.update(dt)

        // Handle waypoint-based walking
        if (!movementPaused && waypointPath.isNotEmpty() && currentWaypointIndex < waypointPath.size) {
            val (targetX, targetY) = waypointPath[currentWaypointIndex]
            val reached = moveTowards(targetX, targetY, walkSpeed, dt)
            if (reached) {
                currentWaypointIndex++
                if (currentWaypointIndex >= waypointPath.size) {
                    // Reached final waypoint
                    reachedTarget = true
                    waypointPath.clear()
                    currentWaypointIndex = 0
                }
            }
        } else if (!movementPaused) {
            // Handle legacy single-target walking
            walkTarget?.let { (targetX, targetY) ->
                val reached = moveTowards(targetX, targetY, walkSpeed, dt)
                if (reached) {
                    reachedTarget = true
                    walkTarget = null
                }
            }
        }

        // Update thought bubble relative to the chair while sitting and to the
        // character while walking or socializing away from their own desk.
        if (thoughtBubble != null && showBubble) {
            // Unstarted entities are used by sprite-position tests and retain
            // the historical chair-relative behavior. Live patrollers have an
            // active state and anchor bubbles to their current position.
            val sitting = shouldUseSittingPosture() ||
                (stateMachine.currentStateName == null && chairPosition != null)
            val anchorX = if (sitting) chairPosition?.first ?: x else x
            val anchorY = if (sitting) chairPosition?.second ?: y else y
            val facesRight = if (sitting) deskFacingDirection == "right" else facingDirection == "right"
            thoughtBubble?.facingLeft = facesRight
            val bubbleX = if (facesRight) anchorX - 16f else anchorX + 16f
            thoughtBubble?.attachTo(bubbleX, anchorY - 26f)
            thoughtBubble?.update(dt)
        }

        // Update ghost if active
        ghost?.let { g ->
            if (g.active) {
                g.update(dt)
            }
        }
    }

    /**
     * Check if the developer is at their desk (within 15 pixels).
     */
    fun isAtDesk(): Boolean {
        val deskPos = getDeskPosition() ?: return false
        val distance = sqrt((x - deskPos.first).pow(2) + (y - deskPos.second).pow(2))
        return distance < 15f
    }

    override fun getRenderInfo(): Map<String, Any> {
        val shouldSit = shouldUseSittingPosture()
        val posture = if (shouldSit) "sitting" else "standing"

        val renderX = if (posture == "sitting") chairPosition?.first ?: x else x
        val renderY = if (posture == "sitting") chairPosition?.second ?: y else y

        val effectiveFacing = if (posture == "sitting") deskFacingDirection ?: facingDirection else facingDirection

        val info = mutableMapOf<String, Any>(
            "type" to "developer",
            "x" to renderX,
            "y" to renderY,
            "animation" to currentAnimation,
            "facing" to effectiveFacing,
            "variant" to spriteVariant,
            "posture" to posture,
            "visible" to visible,
            "state" to (stateMachine.currentStateName ?: ""),
            "entity_id" to entityId
        )

        // Include child entities
        val children = mutableListOf<Map<String, Any>>()
        if (thoughtBubble != null && showBubble) {
            children.add(thoughtBubble!!.getRenderInfo())
        }
        ghost?.let { g ->
            if (g.active) {
                children.add(g.getRenderInfo())
            }
        }

        if (children.isNotEmpty()) {
            info["children"] = children
        }

        return info
    }

    fun getTypedRenderInfo(): CharacterRenderInfo {
        val shouldSit = shouldUseSittingPosture()
        val posture = if (shouldSit) "sitting" else "standing"

        val children = mutableListOf<EffectRenderInfo>()
        if (thoughtBubble != null && showBubble) {
            children.add(thoughtBubble!!.toEffectRenderInfo())
        }
        ghost?.let { g ->
            if (g.active) {
                children.add(g.toEffectRenderInfo())
            }
        }

        val renderX = if (posture == "sitting") chairPosition?.first ?: x else x
        val renderY = if (posture == "sitting") chairPosition?.second ?: y else y
        val effectiveFacing = if (posture == "sitting") deskFacingDirection ?: facingDirection else facingDirection

        return CharacterRenderInfo(
            entityId = entityId,
            x = renderX,
            y = renderY,
            animation = currentAnimation,
            facing = effectiveFacing,
            variant = spriteVariant,
            posture = posture,
            visible = visible,
            state = stateMachine.currentStateName ?: "",
            children = children
        )
    }

    // Position management

    fun setDeskPosition(x: Float, y: Float) {
        deskPosition = Pair(x, y)
    }

    fun getDeskPosition(): Pair<Float, Float>? = deskPosition

    fun setChairPosition(x: Float, y: Float) {
        chairPosition = Pair(x, y)
    }

    fun setWhiteboardPosition(x: Float, y: Float, whiteboardId: String? = null) {
        whiteboardPosition = Pair(x, y)
        assignedWhiteboardId = whiteboardId
    }

    fun getWhiteboardPosition(): Pair<Float, Float>? = whiteboardPosition

    fun getAssignedWhiteboardId(): String? = assignedWhiteboardId

    fun setOffice(office: Office) {
        this.office = office
    }

    fun getOffice(): Office? = office

    fun claimWhiteboard() {
        assignedWhiteboardId?.let { id ->
            office?.claimWhiteboard(id)
        }
    }

    fun releaseWhiteboard() {
        assignedWhiteboardId?.let { id ->
            office?.releaseWhiteboard(id)
        }
    }

    // Walking

    fun setWalkTarget(x: Float, y: Float) {
        walkTarget = Pair(x, y)
        reachedTarget = false
    }

    fun hasReachedTarget(): Boolean = reachedTarget

    fun stopWalking() {
        walkTarget = null
        waypointPath.clear()
        currentWaypointIndex = 0
        velocity.x = 0f
        velocity.y = 0f
    }

    /**
     * Set a multi-waypoint path for the developer to follow.
     */
    fun setWalkPath(path: List<Pair<Float, Float>>) {
        waypointPath = path.toMutableList()
        currentWaypointIndex = 0
        reachedTarget = false
    }

    /**
     * Calculate and set a path to the target using the pathfinder.
     * Falls back to direct walking if no pathfinder is set.
     */
    fun walkToWithPathfinding(targetX: Float, targetY: Float) {
        val pf = pathfinder
        if (pf != null) {
            val path = pf.calculatePath(x, y, targetX, targetY)
            setWalkPath(path)
        } else {
            setWalkTarget(targetX, targetY)
        }
    }

    /**
     * Walk from the current desk to a target, going through the desk midpoint first.
     * This prevents the character from appearing to stand on the desk surface.
     * Falls back to walkToWithPathfinding if no midpoint is set.
     */
    fun walkFromDeskWithPathfinding(targetX: Float, targetY: Float) {
        val mid = deskMidpoint
        val pf = pathfinder
        if (isAtDesk() && mid != null && pf != null) {
            val pathFromMid = pf.calculatePath(mid.first, mid.second, targetX, targetY)
            setWalkPath(listOf(mid) + pathFromMid)
        } else {
            walkToWithPathfinding(targetX, targetY)
        }
    }

    /**
     * Set the pathfinder for waypoint-based navigation.
     */
    fun setPathfinder(pf: Pathfinder) {
        pathfinder = pf
    }

    fun setMovementPaused(paused: Boolean) {
        movementPaused = paused
        if (paused) {
            velocity.x = 0f
            velocity.y = 0f
        }
    }

    // State machine interface

    fun handleEvent(event: String, data: Any? = null) {
        val destinationState = authoritativeDestination(event)
        if (destinationState != null) {
            office?.beforeDeveloperAgentEvent(agentId)
            applyAuthoritativeState(destinationState)
            return
        }
        stateMachine.handleEvent(event, data)
    }

    private fun authoritativeDestination(event: String): String? = when (event) {
        "idle" -> DeveloperStateNames.IDLE
        "thinking_started" -> DeveloperStateNames.THINKING
        "planning_started" -> DeveloperStateNames.WALKING_TO_WHITEBOARD
        "researching_started" -> DeveloperStateNames.RESEARCHING
        "code_writing_started" -> DeveloperStateNames.WRITING_CODE
        "command_started" -> DeveloperStateNames.RUNNING_COMMAND
        "waiting_started" -> DeveloperStateNames.WAITING
        "command_succeeded" -> DeveloperStateNames.CELEBRATING
        "tests_failed" -> DeveloperStateNames.TESTS_FAILING
        else -> null
    }

    private fun applyAuthoritativeState(destinationState: String) {
        if (destinationState == DeveloperStateNames.IDLE) {
            if (isAtDesk()) {
                stateMachine.transitionTo(DeveloperStateNames.IDLE)
            } else {
                deskArrivalState = DeveloperStateNames.IDLE
                stateMachine.transitionTo(DeveloperStateNames.WALKING_TO_DESK)
            }
            return
        }

        if (destinationState == DeveloperStateNames.WALKING_TO_WHITEBOARD) {
            stateMachine.transitionTo(destinationState)
            return
        }

        if (!isAtDesk()) {
            deskArrivalState = destinationState
            stateMachine.transitionTo(DeveloperStateNames.WALKING_TO_DESK)
        } else {
            stateMachine.transitionTo(destinationState)
        }
    }

    fun beginInterruption() {
        stateMachine.transitionTo(DeveloperStateNames.BEING_INTERRUPTED)
    }

    fun endInterruption() {
        if (stateMachine.currentStateName == DeveloperStateNames.BEING_INTERRUPTED) {
            stateMachine.handleEvent("interrupt_ended")
        }
    }

    fun getState(): String? = stateMachine.currentStateName

    private fun shouldUseSittingPosture(): Boolean {
        if (PixelOfficeGame.forceSittingMode && spriteVariant == 1) return true
        if (!isAtDesk()) return false
        return stateMachine.currentStateName in setOf(
            DeveloperStateNames.IDLE,
            DeveloperStateNames.THINKING,
            DeveloperStateNames.WRITING_CODE,
            DeveloperStateNames.BEING_INTERRUPTED,
            DeveloperStateNames.TESTS_FAILING,
            DeveloperStateNames.RESEARCHING,
            DeveloperStateNames.RUNNING_COMMAND,
            DeveloperStateNames.WAITING,
            DeveloperStateNames.CELEBRATING
        )
    }

    fun consumeDeskArrivalState(): String {
        val state = deskArrivalState
        deskArrivalState = DeveloperStateNames.WRITING_CODE
        return state
    }

    fun setDeskArrivalState(state: String) {
        deskArrivalState = state
    }

    // Child entity management

    fun showThoughtBubble(show: Boolean) {
        showBubble = show
        if (show && thoughtBubble == null) {
            onSpawnBubble?.let { spawner ->
                thoughtBubble = spawner(this, "thinking")
            }
        } else if (!show && thoughtBubble != null) {
            thoughtBubble?.hide()
        }
    }

    /** Show an annoyed bubble when a coworker interrupts. */
    fun showAnnoyedBubble() {
        showBubbleOfType("annoyed")
    }

    /**
     * Show a specific type of thought bubble.
     */
    fun showBubbleOfType(bubbleType: String) {
        if (thoughtBubble == null) {
            onSpawnBubble?.let { spawner ->
                thoughtBubble = spawner(this, bubbleType)
            }
        } else {
            thoughtBubble?.bubbleType = bubbleType
            thoughtBubble?.show()
        }
        showBubble = true
    }

    fun spawnGhost() {
        onSpawnGhost?.let { spawner ->
            ghost = spawner(this)
        }
    }

    fun setGhostSpawner(spawner: (Developer) -> Ghost) {
        onSpawnGhost = spawner
    }

    fun setBubbleSpawner(spawner: (BaseEntity, String) -> ThoughtBubble) {
        onSpawnBubble = spawner
    }

    fun setThoughtBubble(bubble: ThoughtBubble) {
        thoughtBubble = bubble
    }

    fun setGhost(newGhost: Ghost) {
        ghost = newGhost
    }
}
