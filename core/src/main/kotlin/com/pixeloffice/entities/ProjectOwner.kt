package com.pixeloffice.entities

/**
 * The Project Owner that appears when a user question is asked.
 *
 * PO spawns off-screen, walks to the relevant developer's desk,
 * waits for the question to be answered, then leaves.
 */
class ProjectOwner(
    x: Float = -32f, // Start off-screen
    y: Float = 100f,
    entityId: String = "po",
    private val walkSpeed: Float = 35f,
    private val questionTimeout: Float = 30f
) : BaseEntity(x, y, entityId) {

    // State
    private var state = "inactive" // inactive, entering, waiting, leaving
    private var waitTimer = 0f
    private var targetDeveloper: BaseEntity? = null
    private val exitPosition = Pair(-32f, 100f) // Where to exit to

    // Callbacks for when PO arrives/leaves
    var onArrive: ((ProjectOwner) -> Unit)? = null
    var onLeave: ((ProjectOwner) -> Unit)? = null

    /**
     * Spawn the PO to ask a question to a developer.
     *
     * @param targetDeveloper The developer to ask the question to.
     * @param spawnX X position to spawn at (default off-screen left).
     * @param spawnY Y position to spawn at.
     */
    fun spawnForQuestion(
        targetDeveloper: BaseEntity,
        spawnX: Float = -32f,
        spawnY: Float = 100f
    ) {
        x = spawnX
        y = spawnY
        this.targetDeveloper = targetDeveloper
        state = "entering"
        waitTimer = 0f
        active = true
        visible = true
    }

    /**
     * Called when the user answers the question.
     */
    fun answerReceived() {
        if (state == "waiting") {
            state = "leaving"
            (targetDeveloper as? Developer)?.handleEvent("interrupt_ended")
            onLeave?.invoke(this)
        }
    }

    override fun update(dt: Float) {
        if (!active) return

        when (state) {
            "entering" -> updateEntering(dt)
            "waiting" -> updateWaiting(dt)
            "leaving" -> updateLeaving(dt)
        }
    }

    private fun updateEntering(dt: Float) {
        targetDeveloper?.let { target ->
            val targetX = target.x - 24
            val targetY = target.y
            val reached = moveTowards(targetX, targetY, walkSpeed, dt)

            if (reached) {
                state = "waiting"
                setAnimation("idle")

                // Interrupt the developer
                (target as? Developer)?.handleEvent("interrupted")

                onArrive?.invoke(this)
            } else {
                setAnimation("walking")
            }
        }
    }

    private fun updateWaiting(dt: Float) {
        waitTimer += dt

        if (waitTimer >= questionTimeout) {
            // Timeout - leave without answer
            state = "leaving"
            (targetDeveloper as? Developer)?.handleEvent("interrupt_ended")
            onLeave?.invoke(this)
        }
    }

    private fun updateLeaving(dt: Float) {
        val reached = moveTowards(exitPosition.first, exitPosition.second, walkSpeed, dt)

        if (reached) {
            state = "inactive"
            targetDeveloper = null
            active = false
            visible = false
        }

        setAnimation("walking")
    }

    // Use the onArrive and onLeave properties directly

    override fun getRenderInfo(): Map<String, Any> {
        return mapOf(
            "type" to "project_owner",
            "x" to x,
            "y" to y,
            "animation" to currentAnimation,
            "facing" to facingDirection,
            "variant" to spriteVariant,
            "visible" to visible,
            "state" to state
        )
    }

    /**
     * Check if PO is currently active (not in inactive state).
     */
    fun isActive(): Boolean = state != "inactive"
}
