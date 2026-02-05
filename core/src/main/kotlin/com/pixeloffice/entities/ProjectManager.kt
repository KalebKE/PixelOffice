package com.pixeloffice.entities

import com.pixeloffice.world.Pathfinder
import kotlin.random.Random

/**
 * The Project Manager that patrols the office checking on developers.
 *
 * PM walks to desks randomly using pathfinding and interrupts developers
 * by showing a "blah" bubble while developers show "annoyed" bubbles.
 */
class ProjectManager(
    x: Float = 0f,
    y: Float = 0f,
    entityId: String = "pm",
    private val patrolSpeed: Float = 15f,
    private val interruptChance: Float = 0.1f,
    private val interruptDuration: Float = 3f
) : BaseEntity(x, y, entityId) {

    // Pathfinding
    private var pathfinder: Pathfinder? = null
    private var deskTargets = mutableListOf<Triple<String, Float, Float>>()  // id, x, y
    private var remainingDesks = mutableListOf<Triple<String, Float, Float>>()
    private var currentPath = mutableListOf<Pair<Float, Float>>()
    private var currentPathIndex = 0
    private var currentDeskId: String? = null

    // State
    private var state = "idle" // idle, walking_to_desk, at_desk, waiting, chatting, sitting
    private var waitTimer = 0f
    private var waitDuration = 0f

    // Assigned desk (for sitting at a specific desk)
    private var assignedDeskId: String? = null
    private var assignedDeskPosition: Pair<Float, Float>? = null

    // Chatting (when colliding with PO)
    private val chattingDuration = 3f
    private var chatCooldown = 0f
    private val chatCooldownDuration = 1f  // 1 second cooldown after chat

    // Bubble support
    private var thoughtBubble: ThoughtBubble? = null
    private var showBubble = false
    private var onSpawnBubble: ((ProjectManager, String) -> ThoughtBubble)? = null

    // Reference to developers for interruption
    private var developers = listOf<BaseEntity>()

    // Callback for when PM is at a developer's desk
    var onInterruptDeveloper: ((Developer) -> Unit)? = null

    fun setPathfinder(pf: Pathfinder) {
        pathfinder = pf
    }

    fun setDeskTargets(desks: List<Triple<String, Float, Float>>) {
        deskTargets = desks.toMutableList()
        remainingDesks = desks.toMutableList().also { it.shuffle() }
        if (remainingDesks.isNotEmpty()) {
            pickNextDesk()
        }
    }

    fun setDevelopers(devs: List<BaseEntity>) {
        developers = devs
    }

    fun setBubbleSpawner(spawner: (ProjectManager, String) -> ThoughtBubble) {
        onSpawnBubble = spawner
    }

    override fun update(dt: Float) {
        if (!active) return

        // Decrement chat cooldown
        if (chatCooldown > 0f) {
            chatCooldown -= dt
        }

        when (state) {
            "idle" -> {
                // Start patrolling if we have desks to visit
                if (deskTargets.isNotEmpty() && currentPath.isEmpty()) {
                    pickNextDesk()
                }
            }
            "walking_to_desk" -> updateWalkingToDesk(dt)
            "at_desk" -> updateAtDesk(dt)
            "waiting" -> updateWaiting(dt)
            "chatting" -> updateChatting(dt)
            "sitting" -> {
                // Stay at assigned desk, do nothing
                setAnimation("idle")
            }
        }

        // Update thought bubble position if visible
        if (thoughtBubble != null && showBubble) {
            thoughtBubble?.attachTo(x + 8, y - 16)
            thoughtBubble?.update(dt)
        }
    }

    private fun updateWalkingToDesk(dt: Float) {
        if (currentPath.isEmpty() || currentPathIndex >= currentPath.size) {
            // Reached destination
            arriveAtDesk()
            return
        }

        val (targetX, targetY) = currentPath[currentPathIndex]
        val reached = moveTowards(targetX, targetY, patrolSpeed, dt)

        if (reached) {
            currentPathIndex++
            if (currentPathIndex >= currentPath.size) {
                arriveAtDesk()
            }
        }

        setAnimation("walking")
    }

    private fun arriveAtDesk() {
        state = "at_desk"
        setAnimation("idle")

        // Check if there's a developer at this desk
        val developer = findDeveloperAtCurrentDesk()

        if (developer != null) {
            // Show "blah" bubble and interrupt developer
            showThoughtBubble("blah")
            developer.showAnnoyedBubble()
            developer.handleEvent("interrupted")
            waitDuration = interruptDuration
        } else {
            // Show "question" bubble briefly, then move on
            showThoughtBubble("question")
            waitDuration = 1.0f  // Brief pause at empty desk
        }

        waitTimer = 0f
        state = "waiting"
    }

    private fun updateAtDesk(dt: Float) {
        // This state is transitioned through quickly
        state = "waiting"
    }

    private fun updateWaiting(dt: Float) {
        waitTimer += dt

        if (waitTimer >= waitDuration) {
            // Done waiting, move to next desk
            hideThoughtBubble()

            // Notify developer that interruption ended
            val developer = findDeveloperAtCurrentDesk()
            developer?.handleEvent("interrupt_ended")

            pickNextDesk()
        }
    }

    private fun pickNextDesk() {
        // If we've visited all desks, reshuffle and start over
        if (remainingDesks.isEmpty()) {
            remainingDesks = deskTargets.toMutableList().also { it.shuffle() }
        }

        if (remainingDesks.isEmpty()) {
            state = "idle"
            return
        }

        // Pick the next desk
        val nextDesk = remainingDesks.removeAt(0)
        currentDeskId = nextDesk.first

        // Calculate path to desk (stand to the left of the desk)
        val deskX = nextDesk.second - 20f  // Stand 20 pixels to the left
        val deskY = nextDesk.third

        val pf = pathfinder
        currentPath = if (pf != null) {
            pf.calculatePath(x, y, deskX, deskY).toMutableList()
        } else {
            mutableListOf(Pair(deskX, deskY))
        }
        currentPathIndex = 0
        state = "walking_to_desk"
    }

    private fun findDeveloperAtCurrentDesk(): Developer? {
        val deskId = currentDeskId ?: return null

        for (dev in developers) {
            val developer = dev as? Developer ?: continue
            // Check if developer is near the current desk
            val deskTarget = deskTargets.find { it.first == deskId } ?: continue
            val dx = kotlin.math.abs(developer.x - deskTarget.second)
            val dy = kotlin.math.abs(developer.y - deskTarget.third)

            if (dx < 10f && dy < 10f && developer.getState() != "walking_to_whiteboard") {
                return developer
            }
        }
        return null
    }

    fun showThoughtBubble(type: String) {
        showBubble = true
        if (thoughtBubble == null) {
            onSpawnBubble?.let { spawner ->
                thoughtBubble = spawner(this, type)
            }
        } else {
            thoughtBubble?.bubbleType = type
            thoughtBubble?.show()
        }
    }

    fun hideThoughtBubble() {
        showBubble = false
        thoughtBubble?.hide()
    }

    // Chatting methods (collision with PO)

    fun startChatting() {
        if (state == "walking_to_desk" && chatCooldown <= 0f) {
            state = "chatting"
            waitTimer = 0f
            setAnimation("idle")
            showThoughtBubble("blah")
        }
    }

    fun isWalking(): Boolean = state == "walking_to_desk"

    private fun updateChatting(dt: Float) {
        waitTimer += dt
        if (waitTimer >= chattingDuration) {
            hideThoughtBubble()
            chatCooldown = chatCooldownDuration  // Start cooldown
            state = "walking_to_desk"  // Resume walking
        }
    }

    override fun getRenderInfo(): Map<String, Any> {
        // Determine posture based on state and desk assignment
        val posture = if (state == "sitting" && isAtAssignedDesk()) "sitting" else "standing"

        val info = mutableMapOf<String, Any>(
            "type" to "project_manager",
            "x" to x,
            "y" to y,
            "animation" to currentAnimation,
            "facing" to facingDirection,
            "variant" to spriteVariant,
            "visible" to visible,
            "state" to state,
            "posture" to posture,
            "entity_id" to entityId
        )

        // Include child entities (thought bubble)
        val children = mutableListOf<Map<String, Any>>()
        if (thoughtBubble != null && showBubble) {
            children.add(thoughtBubble!!.getRenderInfo())
        }

        if (children.isNotEmpty()) {
            info["children"] = children
        }

        return info
    }

    // Legacy methods for backward compatibility (no longer used)
    fun setPatrolPath(points: List<Pair<Float, Float>>) {
        // No longer used - PM now patrols desks randomly
        if (points.isNotEmpty()) {
            x = points[0].first
            y = points[0].second
        }
    }

    // Desk assignment methods

    /**
     * Assign the PM to a specific desk.
     * When assigned, PM will sit at the desk instead of patrolling.
     */
    fun setAssignedDesk(deskId: String, deskX: Float, deskY: Float) {
        assignedDeskId = deskId
        assignedDeskPosition = Pair(deskX, deskY)
        state = "sitting"
        setAnimation("idle")
    }

    /**
     * Clear the desk assignment and resume normal patrol.
     */
    fun clearAssignedDesk() {
        assignedDeskId = null
        assignedDeskPosition = null
        if (state == "sitting") {
            state = "idle"
            if (deskTargets.isNotEmpty()) {
                pickNextDesk()
            }
        }
    }

    /**
     * Get the assigned desk ID.
     */
    fun getAssignedDeskId(): String? = assignedDeskId

    /**
     * Check if the PM is at their assigned desk.
     */
    fun isAtAssignedDesk(): Boolean {
        val deskPos = assignedDeskPosition ?: return false
        val dx = kotlin.math.abs(x - deskPos.first)
        val dy = kotlin.math.abs(y - deskPos.second)
        return dx < 15f && dy < 15f
    }
}
