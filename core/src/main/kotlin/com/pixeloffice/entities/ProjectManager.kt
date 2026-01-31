package com.pixeloffice.entities

import kotlin.random.Random

/**
 * The Project Manager that patrols the office.
 *
 * PM walks along a predefined path and occasionally interrupts
 * developers by walking to their desk.
 */
class ProjectManager(
    x: Float = 0f,
    y: Float = 0f,
    entityId: String = "pm",
    private val patrolSpeed: Float = 30f,
    private val interruptChance: Float = 0.1f,
    private val interruptDuration: Float = 3f
) : BaseEntity(x, y, entityId) {

    // Patrol path
    private var patrolPoints = listOf<Pair<Float, Float>>()
    private var currentPatrolIndex = 0

    // State
    private var state = "patrolling" // patrolling, interrupting, returning
    private var interruptTimer = 0f
    private var interruptTarget: BaseEntity? = null
    private var returnPosition: Pair<Float, Float>? = null

    // Reference to developers for interruption
    private var developers = listOf<BaseEntity>()

    fun setPatrolPath(points: List<Pair<Float, Float>>) {
        patrolPoints = points
        if (points.isNotEmpty()) {
            x = points[0].first
            y = points[0].second
        }
    }

    fun setDevelopers(devs: List<BaseEntity>) {
        developers = devs
    }

    override fun update(dt: Float) {
        if (!active || patrolPoints.isEmpty()) return

        when (state) {
            "patrolling" -> updatePatrol(dt)
            "interrupting" -> updateInterrupt(dt)
            "returning" -> updateReturn(dt)
        }
    }

    private fun updatePatrol(dt: Float) {
        val target = patrolPoints[currentPatrolIndex]
        val reached = moveTowards(target.first, target.second, patrolSpeed, dt)

        if (reached) {
            // Move to next patrol point
            currentPatrolIndex = (currentPatrolIndex + 1) % patrolPoints.size

            // Maybe interrupt a developer
            if (Random.nextFloat() < interruptChance * dt) {
                tryInterrupt()
            }
        }

        setAnimation("walking")
    }

    private fun updateInterrupt(dt: Float) {
        interruptTarget?.let { target ->
            val targetX = target.x - 20
            val targetY = target.y
            val reached = moveTowards(targetX, targetY, patrolSpeed, dt)

            if (reached) {
                setAnimation("idle")
                interruptTimer += dt

                if (interruptTimer >= interruptDuration) {
                    // End interruption
                    (target as? Developer)?.handleEvent("interrupt_ended")
                    state = "returning"
                    interruptTarget = null
                }
            } else {
                setAnimation("walking")
            }
        }
    }

    private fun updateReturn(dt: Float) {
        returnPosition?.let { pos ->
            val reached = moveTowards(pos.first, pos.second, patrolSpeed, dt)
            if (reached) {
                state = "patrolling"
                returnPosition = null
            }
        }

        setAnimation("walking")
    }

    private fun tryInterrupt() {
        if (developers.isEmpty()) return

        // Filter for active developers not already interrupted
        val available = developers.filter { dev ->
            dev.active && (dev as? Developer)?.getState() != "being_interrupted"
        }

        if (available.isEmpty()) return

        val target = available.random()
        interruptTarget = target
        returnPosition = Pair(x, y)
        interruptTimer = 0f
        state = "interrupting"

        // Notify the developer
        (target as? Developer)?.handleEvent("interrupted")
    }

    override fun getRenderInfo(): Map<String, Any> {
        return mapOf(
            "type" to "project_manager",
            "x" to x,
            "y" to y,
            "animation" to currentAnimation,
            "facing" to facingDirection,
            "variant" to spriteVariant,
            "visible" to visible,
            "state" to state
        )
    }
}
