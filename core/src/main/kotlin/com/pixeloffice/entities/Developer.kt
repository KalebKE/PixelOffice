package com.pixeloffice.entities

import com.pixeloffice.states.DeveloperStateMachine
import com.pixeloffice.world.OfficePathfinder

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
    thinkingDuration: Float = 3f,
    despairDuration: Float = 2f
) : BaseEntity(x, y, entityId) {

    init {
        spriteVariant = colorVariant
    }

    // Position references
    private var deskPosition: Pair<Float, Float>? = null
    private var whiteboardPosition: Pair<Float, Float>? = null

    // Walk target
    private var walkTarget: Pair<Float, Float>? = null
    private var reachedTarget = false

    // Waypoint-based pathfinding
    private var waypointPath: MutableList<Pair<Float, Float>> = mutableListOf()
    private var currentWaypointIndex = 0
    private var pathfinder: OfficePathfinder? = null

    // State machine
    private val stateMachine = DeveloperStateMachine(
        this,
        thinkingDuration = thinkingDuration,
        despairDuration = despairDuration
    )

    // Child entities
    private var thoughtBubble: ThoughtBubble? = null
    private var ghost: Ghost? = null
    private var showBubble = false

    // Callbacks for creating child entities
    var onSpawnGhost: ((Developer) -> Ghost)? = null
    var onSpawnBubble: ((Developer) -> ThoughtBubble)? = null

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
        if (waypointPath.isNotEmpty() && currentWaypointIndex < waypointPath.size) {
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
        } else {
            // Handle legacy single-target walking
            walkTarget?.let { (targetX, targetY) ->
                val reached = moveTowards(targetX, targetY, walkSpeed, dt)
                if (reached) {
                    reachedTarget = true
                    walkTarget = null
                }
            }
        }

        // Update thought bubble position if visible
        if (thoughtBubble != null && showBubble) {
            thoughtBubble?.x = x + 8
            thoughtBubble?.y = y - 16
            thoughtBubble?.update(dt)
        }

        // Update ghost if active
        ghost?.let { g ->
            if (g.active) {
                g.update(dt)
            }
        }
    }

    override fun getRenderInfo(): Map<String, Any> {
        val info = mutableMapOf<String, Any>(
            "type" to "developer",
            "x" to x,
            "y" to y,
            "animation" to currentAnimation,
            "facing" to facingDirection,
            "variant" to spriteVariant,
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

    // Position management

    fun setDeskPosition(x: Float, y: Float) {
        deskPosition = Pair(x, y)
    }

    fun getDeskPosition(): Pair<Float, Float>? = deskPosition

    fun setWhiteboardPosition(x: Float, y: Float) {
        whiteboardPosition = Pair(x, y)
    }

    fun getWhiteboardPosition(): Pair<Float, Float>? = whiteboardPosition

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
     * Set the pathfinder for waypoint-based navigation.
     */
    fun setPathfinder(pf: OfficePathfinder) {
        pathfinder = pf
    }

    // State machine interface

    fun handleEvent(event: String, data: Any? = null) {
        stateMachine.handleEvent(event, data)
    }

    fun getState(): String? = stateMachine.currentStateName

    // Child entity management

    fun showThoughtBubble(show: Boolean) {
        showBubble = show
        if (show && thoughtBubble == null) {
            onSpawnBubble?.let { spawner ->
                thoughtBubble = spawner(this)
            }
        }
    }

    fun spawnGhost() {
        onSpawnGhost?.let { spawner ->
            ghost = spawner(this)
        }
    }

    fun setGhostSpawner(spawner: (Developer) -> Ghost) {
        onSpawnGhost = spawner
    }

    fun setBubbleSpawner(spawner: (Developer) -> ThoughtBubble) {
        onSpawnBubble = spawner
    }

    fun setThoughtBubble(bubble: ThoughtBubble) {
        thoughtBubble = bubble
    }

    fun setGhost(newGhost: Ghost) {
        ghost = newGhost
    }
}
