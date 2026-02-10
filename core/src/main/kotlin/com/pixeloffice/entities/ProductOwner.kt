package com.pixeloffice.entities

import com.pixeloffice.rendering.CharacterRenderInfo
import com.pixeloffice.rendering.EffectRenderInfo
import com.pixeloffice.world.Pathfinder
import kotlin.random.Random

/**
 * The Product Owner that patrols the office and responds to user questions.
 *
 * PO patrols between desks like the Project Manager, but can be interrupted
 * to handle USER_QUESTION events. When a question comes in, the PO walks to
 * the relevant developer's desk, waits for the answer, then resumes patrol.
 */
class ProductOwner(
    x: Float = -32f, // Start off-screen
    y: Float = 100f,
    entityId: String = "po",
    private val walkSpeed: Float = 35f,
    private val questionTimeout: Float = 30f
) : BaseEntity(x, y, entityId) {

    // State: inactive, patrolling, walking_to_desk, at_desk, patrol_waiting,
    //        question_entering, question_waiting, question_leaving, chatting, sitting
    private var state = "inactive"
    private var waitTimer = 0f

    // Assigned desk (for sitting at a specific desk)
    private var assignedDeskId: String? = null
    private var assignedDeskPosition: Pair<Float, Float>? = null
    private var chairPosition: Pair<Float, Float>? = null

    // Chatting (when colliding with PM)
    private val chattingDuration = 3f
    private var chatCooldown = 0f
    private val chatCooldownDuration = 1f  // 1 second cooldown after chat
    private var targetDeveloper: BaseEntity? = null
    private val exitPosition = Pair(-32f, 100f) // Where to exit to

    // Pathfinding
    private var pathfinder: Pathfinder? = null
    private var currentPath: MutableList<Pair<Float, Float>> = mutableListOf()
    private var currentPathIndex = 0

    // Patrol targets
    private var deskTargets: MutableList<Triple<String, Float, Float>> = mutableListOf()
    private var remainingDesks: MutableList<Triple<String, Float, Float>> = mutableListOf()
    private var currentDeskId: String? = null

    // Developer references
    private var developers: List<BaseEntity> = emptyList()

    // Patrol timing
    private val patrolSpeed = 15f
    private val patrolWaitDuration = 2f

    // Sit-patrol-return cycle
    var sitDuration = 0f  // 0 = sit forever (default)
    private var sitTimer = 0f
    private var returningToDesk = false
    private var patrolDesksVisited = 0
    private val maxPatrolDesks = 3

    // Working animation while sitting (idle/typing alternation)
    private var workAnimTimer = 0f
    private var workAnimDuration = 0f
    private var isTyping = false

    // External event control (subagent routing)
    var controlled = false
        private set

    // Bubble support
    private var thoughtBubble: ThoughtBubble? = null
    private var showBubble = false
    private var onSpawnBubble: ((BaseEntity, String) -> ThoughtBubble)? = null

    // Callbacks for when PO arrives/leaves
    var onArrive: ((ProductOwner) -> Unit)? = null
    var onLeave: ((ProductOwner) -> Unit)? = null

    // Setter methods for patrol configuration
    fun setPathfinder(pf: Pathfinder) { pathfinder = pf }

    fun setDeskTargets(desks: List<Triple<String, Float, Float>>) {
        deskTargets = desks.toMutableList()
        remainingDesks = desks.toMutableList().also { it.shuffle() }
    }

    fun setDevelopers(devs: List<BaseEntity>) { developers = devs }

    fun setBubbleSpawner(spawner: (BaseEntity, String) -> ThoughtBubble) {
        onSpawnBubble = spawner
    }

    /**
     * Start patrolling between desks.
     */
    fun startPatrol() {
        if (deskTargets.isEmpty()) return
        active = true
        visible = true
        state = "patrolling"
        pickNextDesk()
    }

    /**
     * Spawn the PO to ask a question to a developer.
     * If already patrolling, redirects to the target developer.
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
        this.targetDeveloper = targetDeveloper
        waitTimer = 0f

        // If already patrolling, just redirect to the target developer
        if (state.startsWith("patrol") || state == "walking_to_desk" || state == "at_desk") {
            // Calculate path to developer
            val targetX = targetDeveloper.x - 24f
            val targetY = targetDeveloper.y
            currentPath = pathfinder?.calculatePath(x, y, targetX, targetY)?.toMutableList()
                ?: mutableListOf(Pair(targetX, targetY))
            currentPathIndex = 0
            state = "question_entering"
        } else {
            // Not patrolling, spawn from off-screen
            x = spawnX
            y = spawnY
            state = "question_entering"
            active = true
            visible = true
        }
    }

    /**
     * Called when the user answers the question.
     * Resumes patrol if desk targets are configured, otherwise becomes inactive.
     */
    fun answerReceived() {
        if (state == "question_waiting") {
            (targetDeveloper as? Developer)?.handleEvent("interrupt_ended")
            targetDeveloper = null
            onLeave?.invoke(this)

            // Resume patrol instead of leaving
            if (deskTargets.isNotEmpty()) {
                pickNextDesk()
            } else {
                state = "inactive"
                active = false
                visible = false
            }
        }
    }

    override fun update(dt: Float) {
        if (!active) return

        // Decrement chat cooldown
        if (chatCooldown > 0f) {
            chatCooldown -= dt
        }

        when (state) {
            "patrolling", "walking_to_desk" -> updateWalkingToDesk(dt)
            "at_desk" -> arriveAtDesk()
            "patrol_waiting" -> updatePatrolWaiting(dt)
            "question_entering" -> updateQuestionEntering(dt)
            "question_waiting" -> updateQuestionWaiting(dt)
            "question_leaving" -> updateQuestionLeaving(dt)
            "chatting" -> updateChatting(dt)
            "sitting" -> {
                if (!controlled) {
                    // Working animation: alternate idle/typing
                    workAnimTimer += dt
                    if (workAnimTimer >= workAnimDuration) {
                        isTyping = !isTyping
                        workAnimDuration = if (isTyping) {
                            3f + Random.nextFloat() * 3f // typing 3-6s
                        } else {
                            2f + Random.nextFloat() * 2f // idle 2-4s
                        }
                        workAnimTimer = 0f
                    }
                    setAnimation(if (isTyping) "typing" else "idle")

                    if (sitDuration > 0f) {
                        sitTimer += dt
                        if (sitTimer >= sitDuration) {
                            sitTimer = 0f
                            beginPatrol()
                        }
                    }
                }
            }
        }

        // Update thought bubble position
        if (thoughtBubble != null && showBubble) {
            thoughtBubble?.attachTo(x + 8, y - 16)
            thoughtBubble?.update(dt)
        }
    }

    // Patrol methods

    private fun pickNextDesk() {
        // If we've hit patrol limit, return to assigned desk
        if (assignedDeskId != null && patrolDesksVisited >= maxPatrolDesks) {
            returnToAssignedDesk()
            return
        }
        if (remainingDesks.isEmpty()) {
            if (assignedDeskId != null) {
                returnToAssignedDesk()
                return
            }
            remainingDesks = deskTargets.toMutableList().also { it.shuffle() }
        }
        if (remainingDesks.isEmpty()) {
            state = "patrolling"
            return
        }

        patrolDesksVisited++

        val nextDesk = remainingDesks.removeAt(0)
        currentDeskId = nextDesk.first
        // Stand at the midpoint between aisle and desk
        val midpoint = pathfinder?.getDeskMidpoint(nextDesk.first)
        val deskX = midpoint?.first ?: (nextDesk.second - 20f)
        val deskY = midpoint?.second ?: nextDesk.third

        val pf = pathfinder
        currentPath = if (pf != null) {
            val path = pf.calculatePath(x, y, deskX, deskY).toMutableList()
            val mid = deskMidpoint
            if (mid != null && isAtAssignedDesk()) {
                path.add(0, Pair(mid.first, mid.second))
            }
            path
        } else {
            mutableListOf(Pair(deskX, deskY))
        }
        currentPathIndex = 0
        state = "walking_to_desk"
    }

    private fun updateWalkingToDesk(dt: Float) {
        if (currentPath.isEmpty() || currentPathIndex >= currentPath.size) {
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
        if (returningToDesk) {
            assignedDeskPosition?.let { (dx, dy) -> x = dx; y = dy }
            returningToDesk = false
            state = "sitting"
            randomizeSitDuration()
            resetWorkAnimation()
            setAnimation("idle")
            return
        }

        state = "at_desk"
        setAnimation("idle")

        val developer = findDeveloperAtCurrentDesk()
        if (developer != null) {
            showThoughtBubble("question")  // PO asks questions
            developer.showAnnoyedBubble()
        } else {
            showThoughtBubble("thinking")
        }

        waitTimer = 0f
        state = "patrol_waiting"
    }

    private fun updatePatrolWaiting(dt: Float) {
        waitTimer += dt
        if (waitTimer >= patrolWaitDuration) {
            hideThoughtBubble()
            pickNextDesk()
        }
    }

    private fun beginPatrol() {
        snapToMidpoint()
        patrolDesksVisited = 0
        remainingDesks = deskTargets
            .filter { it.first != assignedDeskId }
            .toMutableList()
            .also { it.shuffle() }
        returningToDesk = false
        if (remainingDesks.isNotEmpty()) {
            pickNextDesk()
        }
    }

    private fun returnToAssignedDesk() {
        val deskPos = assignedDeskPosition ?: return
        returningToDesk = true
        currentDeskId = assignedDeskId
        val pf = pathfinder
        currentPath = if (pf != null) {
            pf.calculatePath(x, y, deskPos.first, deskPos.second).toMutableList()
        } else {
            mutableListOf(Pair(deskPos.first, deskPos.second))
        }
        currentPathIndex = 0
        state = "walking_to_desk"
    }

    fun setSitTimerStart(startValue: Float) {
        sitTimer = startValue
    }

    private fun findDeveloperAtCurrentDesk(): Developer? {
        val deskId = currentDeskId ?: return null
        for (dev in developers) {
            val developer = dev as? Developer ?: continue
            val deskTarget = deskTargets.find { it.first == deskId } ?: continue
            val dx = kotlin.math.abs(developer.x - deskTarget.second)
            val dy = kotlin.math.abs(developer.y - deskTarget.third)
            if (dx < 10f && dy < 10f) return developer
        }
        return null
    }

    // Question handling methods

    private fun updateQuestionEntering(dt: Float) {
        // If we have a path, follow it
        if (currentPath.isNotEmpty() && currentPathIndex < currentPath.size) {
            val (targetX, targetY) = currentPath[currentPathIndex]
            val reached = moveTowards(targetX, targetY, walkSpeed, dt)

            if (reached) {
                currentPathIndex++
                if (currentPathIndex >= currentPath.size) {
                    arriveForQuestion()
                }
            }
            setAnimation("walking")
        } else {
            // No path, move directly to developer
            targetDeveloper?.let { target ->
                val targetX = target.x - 24f
                val targetY = target.y
                val reached = moveTowards(targetX, targetY, walkSpeed, dt)

                if (reached) {
                    arriveForQuestion()
                } else {
                    setAnimation("walking")
                }
            }
        }
    }

    private fun arriveForQuestion() {
        state = "question_waiting"
        setAnimation("idle")

        // Interrupt the developer
        (targetDeveloper as? Developer)?.handleEvent("interrupted")

        onArrive?.invoke(this)
    }

    private fun updateQuestionWaiting(dt: Float) {
        waitTimer += dt

        if (waitTimer >= questionTimeout) {
            // Timeout - resume patrol or leave
            (targetDeveloper as? Developer)?.handleEvent("interrupt_ended")
            targetDeveloper = null
            onLeave?.invoke(this)

            if (deskTargets.isNotEmpty()) {
                pickNextDesk()
            } else {
                state = "question_leaving"
            }
        }
    }

    private fun updateQuestionLeaving(dt: Float) {
        val reached = moveTowards(exitPosition.first, exitPosition.second, walkSpeed, dt)

        if (reached) {
            state = "inactive"
            targetDeveloper = null
            active = false
            visible = false
        }

        setAnimation("walking")
    }

    // Bubble methods

    fun showThoughtBubble(type: String) {
        showBubble = true
        if (thoughtBubble == null) {
            onSpawnBubble?.let { spawner -> thoughtBubble = spawner(this, type) }
        } else {
            thoughtBubble?.bubbleType = type
            thoughtBubble?.show()
        }
    }

    fun hideThoughtBubble() {
        showBubble = false
        thoughtBubble?.hide()
    }

    // Chatting methods (collision with PM)

    fun startChatting() {
        if ((state == "walking_to_desk" || state == "patrolling") && chatCooldown <= 0f) {
            state = "chatting"
            waitTimer = 0f
            setAnimation("idle")
            showThoughtBubble("blah")
        }
    }

    fun isWalking(): Boolean = state == "walking_to_desk" || state == "patrolling"

    private fun updateChatting(dt: Float) {
        waitTimer += dt
        if (waitTimer >= chattingDuration) {
            hideThoughtBubble()
            chatCooldown = chatCooldownDuration  // Start cooldown
            state = "walking_to_desk"  // Resume patrol
        }
    }

    // Use the onArrive and onLeave properties directly

    override fun getRenderInfo(): Map<String, Any> {
        // Determine posture based on state and desk assignment
        val posture = if (state == "sitting" && isAtAssignedDesk()) "sitting" else "standing"
        val renderX = if (posture == "sitting") chairPosition?.first ?: x else x
        val renderY = if (posture == "sitting") chairPosition?.second ?: y else y
        val effectiveFacing = if (posture == "sitting") deskFacingDirection ?: facingDirection else facingDirection

        val info = mutableMapOf<String, Any>(
            "type" to "product_owner",
            "x" to renderX,
            "y" to renderY,
            "animation" to currentAnimation,
            "facing" to effectiveFacing,
            "variant" to spriteVariant,
            "visible" to visible,
            "state" to state,
            "posture" to posture,
            "entity_id" to entityId
        )

        val children = mutableListOf<Map<String, Any>>()
        if (thoughtBubble != null && showBubble) {
            children.add(thoughtBubble!!.getRenderInfo())
        }
        if (children.isNotEmpty()) {
            info["children"] = children
        }

        return info
    }

    fun getTypedRenderInfo(): CharacterRenderInfo {
        val posture = if (state == "sitting" && isAtAssignedDesk()) "sitting" else "standing"
        val renderX = if (posture == "sitting") chairPosition?.first ?: x else x
        val renderY = if (posture == "sitting") chairPosition?.second ?: y else y
        val effectiveFacing = if (posture == "sitting") deskFacingDirection ?: facingDirection else facingDirection

        val children = mutableListOf<EffectRenderInfo>()
        if (thoughtBubble != null && showBubble) {
            children.add(thoughtBubble!!.toEffectRenderInfo())
        }

        return CharacterRenderInfo(
            type = "product_owner",
            entityId = entityId,
            x = renderX,
            y = renderY,
            animation = currentAnimation,
            facing = effectiveFacing,
            variant = spriteVariant,
            posture = posture,
            visible = visible,
            state = state,
            children = children
        )
    }

    /**
     * Check if PO is currently active (not in inactive state).
     */
    fun isActive(): Boolean = state != "inactive" || assignedDeskId != null

    /**
     * Check if PO is currently patrolling (for debug/status purposes).
     */
    fun isPatrolling(): Boolean = state in listOf("patrolling", "walking_to_desk", "at_desk", "patrol_waiting")

    // Desk assignment methods

    /**
     * Assign the PO to a specific desk.
     * When assigned, PO will sit at the desk instead of patrolling.
     */
    fun setAssignedDesk(deskId: String, deskX: Float, deskY: Float, chairX: Float = deskX, chairY: Float = deskY) {
        assignedDeskId = deskId
        assignedDeskPosition = Pair(deskX, deskY)
        chairPosition = Pair(chairX, chairY)
        state = "sitting"
        active = true
        visible = true
        randomizeSitDuration()
        resetWorkAnimation()
        setAnimation("idle")
    }

    /**
     * Clear the desk assignment and resume normal patrol.
     */
    fun clearAssignedDesk() {
        assignedDeskId = null
        assignedDeskPosition = null
        if (state == "sitting") {
            if (deskTargets.isNotEmpty()) {
                startPatrol()
            } else {
                state = "inactive"
                active = false
                visible = false
            }
        }
    }

    /**
     * Get the assigned desk ID.
     */
    fun getAssignedDeskId(): String? = assignedDeskId

    /**
     * Check if the PO is at their assigned desk.
     */
    fun isAtAssignedDesk(): Boolean {
        val deskPos = assignedDeskPosition ?: return false
        val dx = kotlin.math.abs(x - deskPos.first)
        val dy = kotlin.math.abs(y - deskPos.second)
        return dx < 15f && dy < 15f
    }

    // Sit duration randomization

    private fun randomizeSitDuration() {
        sitDuration = 45f + Random.nextFloat() * 30f // 45-75s
    }

    private fun resetWorkAnimation() {
        isTyping = false
        workAnimTimer = 0f
        workAnimDuration = 2f + Random.nextFloat() * 2f
    }

    // External event handling (subagent routing)

    /**
     * Handle an external event (from subagent routing).
     * When controlled, PO stays at desk and shows appropriate animation/bubble.
     */
    fun handleEvent(event: String) {
        when (event) {
            "thinking_started" -> {
                controlled = true
                showThoughtBubble("thinking")
                setAnimation("idle")
                if (state != "sitting") goToDesk()
            }
            "code_writing_started" -> {
                controlled = true
                hideThoughtBubble()
                setAnimation("typing")
                if (state != "sitting") goToDesk()
            }
            "researching_started" -> {
                controlled = true
                showThoughtBubble("reading")
                setAnimation("idle")
                if (state != "sitting") goToDesk()
            }
            "command_started" -> {
                controlled = true
                hideThoughtBubble()
                setAnimation("idle")
                if (state != "sitting") goToDesk()
            }
            "tests_failed" -> {
                controlled = true
                showThoughtBubble("annoyed")
                if (state != "sitting") goToDesk()
            }
            "command_succeeded" -> {
                controlled = true
                showThoughtBubble("success")
                if (state != "sitting") goToDesk()
            }
            "idle" -> {
                controlled = false
                hideThoughtBubble()
                if (state == "sitting") {
                    resetWorkAnimation()
                    randomizeSitDuration()
                    sitTimer = 0f
                }
            }
        }
    }

    /**
     * Navigate back to assigned desk if not already there.
     */
    private fun goToDesk() {
        if (assignedDeskId != null && !isAtAssignedDesk()) {
            returnToAssignedDesk()
        } else if (assignedDeskId != null) {
            assignedDeskPosition?.let { (dx, dy) -> x = dx; y = dy }
            state = "sitting"
        }
    }
}
