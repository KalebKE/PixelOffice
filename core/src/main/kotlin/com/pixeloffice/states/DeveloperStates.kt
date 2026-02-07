package com.pixeloffice.states

import com.pixeloffice.entities.Developer

/**
 * Developer state names.
 */
object DeveloperStateNames {
    const val IDLE = "idle"
    const val THINKING = "thinking"
    const val WALKING_TO_WHITEBOARD = "walking_to_whiteboard"
    const val AT_WHITEBOARD = "at_whiteboard"
    const val WALKING_TO_DESK = "walking_to_desk"
    const val WRITING_CODE = "writing_code"
    const val TESTS_FAILING = "tests_failing"
    const val BEING_INTERRUPTED = "being_interrupted"
}

/**
 * Developer is idle at their desk.
 */
class IdleState : State<Developer>(DeveloperStateNames.IDLE) {
    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("idle")
    }

    override fun update(entity: Developer, dt: Float): String? = null

    override fun exit(entity: Developer, nextState: State<Developer>?) {}

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "thinking_started" -> DeveloperStateNames.THINKING
            "code_writing_started" -> DeveloperStateNames.WRITING_CODE
            "interrupted" -> DeveloperStateNames.BEING_INTERRUPTED
            else -> null
        }
    }
}

/**
 * Developer is thinking, showing thought bubble.
 */
class ThinkingState(private val duration: Float = 3.0f) : State<Developer>(DeveloperStateNames.THINKING) {
    private var timer = 0f

    override fun enter(entity: Developer, prevState: State<Developer>?) {
        if (entity.isAtDesk()) {
            entity.snapToMidpoint()
        }
        entity.setAnimation("thinking")
        timer = 0f
    }

    override fun update(entity: Developer, dt: Float): String? {
        timer += dt
        if (timer >= duration) {
            return DeveloperStateNames.WALKING_TO_WHITEBOARD
        }
        return null
    }

    override fun exit(entity: Developer, nextState: State<Developer>?) {}

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "done_thinking" -> DeveloperStateNames.WRITING_CODE
            "walk_to_whiteboard" -> DeveloperStateNames.WALKING_TO_WHITEBOARD
            "interrupted" -> DeveloperStateNames.BEING_INTERRUPTED
            else -> null
        }
    }
}

/**
 * Developer is walking to the whiteboard.
 */
class WalkingToWhiteboardState : State<Developer>(DeveloperStateNames.WALKING_TO_WHITEBOARD) {
    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("walking")
        // Claim the whiteboard before walking
        entity.claimWhiteboard()
        entity.getWhiteboardPosition()?.let { (x, y) ->
            entity.walkFromDeskWithPathfinding(x, y)
        }
    }

    override fun update(entity: Developer, dt: Float): String? {
        if (entity.hasReachedTarget()) {
            return DeveloperStateNames.AT_WHITEBOARD
        }
        return null
    }

    override fun exit(entity: Developer, nextState: State<Developer>?) {}

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "arrived" -> DeveloperStateNames.AT_WHITEBOARD
            "interrupted" -> DeveloperStateNames.BEING_INTERRUPTED
            else -> null
        }
    }
}

/**
 * Developer is at the whiteboard, drawing/planning.
 */
class AtWhiteboardState(private val duration: Float = 5.0f) : State<Developer>(DeveloperStateNames.AT_WHITEBOARD) {
    private var timer = 0f

    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("thinking")
        entity.showThoughtBubble(true)
        timer = 0f
    }

    override fun update(entity: Developer, dt: Float): String? {
        timer += dt
        if (timer >= duration) {
            return DeveloperStateNames.WALKING_TO_DESK
        }
        return null
    }

    override fun exit(entity: Developer, nextState: State<Developer>?) {
        entity.showThoughtBubble(false)
        entity.releaseWhiteboard()
    }

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "done" -> DeveloperStateNames.WALKING_TO_DESK
            "interrupted" -> DeveloperStateNames.BEING_INTERRUPTED
            else -> null
        }
    }
}

/**
 * Developer is walking back to their desk.
 */
class WalkingToDeskState : State<Developer>(DeveloperStateNames.WALKING_TO_DESK) {
    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("walking")
        entity.getDeskPosition()?.let { (x, y) ->
            entity.walkToWithPathfinding(x, y)
        }
    }

    override fun update(entity: Developer, dt: Float): String? {
        if (entity.hasReachedTarget()) {
            return DeveloperStateNames.WRITING_CODE
        }
        return null
    }

    override fun exit(entity: Developer, nextState: State<Developer>?) {}

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "arrived" -> DeveloperStateNames.WRITING_CODE
            "interrupted" -> DeveloperStateNames.BEING_INTERRUPTED
            else -> null
        }
    }
}

/**
 * Developer is typing/writing code at their desk.
 */
class WritingCodeState : State<Developer>(DeveloperStateNames.WRITING_CODE) {
    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("typing")
    }

    override fun update(entity: Developer, dt: Float): String? = null

    override fun exit(entity: Developer, nextState: State<Developer>?) {}

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "code_writing_ended" -> DeveloperStateNames.IDLE
            "tests_failed" -> DeveloperStateNames.TESTS_FAILING
            "thinking_started" -> DeveloperStateNames.THINKING
            "interrupted" -> DeveloperStateNames.BEING_INTERRUPTED
            else -> null
        }
    }
}

/**
 * Developer is in despair - tests failed, ghost rises.
 */
class TestsFailingState(private val duration: Float = 2.0f) : State<Developer>(DeveloperStateNames.TESTS_FAILING) {
    private var timer = 0f

    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("despair")
        entity.spawnGhost()
        timer = 0f
    }

    override fun update(entity: Developer, dt: Float): String? {
        timer += dt
        if (timer >= duration) {
            return DeveloperStateNames.AT_WHITEBOARD
        }
        return null
    }

    override fun exit(entity: Developer, nextState: State<Developer>?) {}

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "despair_complete" -> DeveloperStateNames.WALKING_TO_WHITEBOARD
            else -> null
        }
    }
}

/**
 * Developer is being interrupted by PM or PO.
 */
class BeingInterruptedState(private val duration: Float = 3.0f) : State<Developer>(DeveloperStateNames.BEING_INTERRUPTED) {
    private var timer = 0f
    private var previousState: String = DeveloperStateNames.IDLE

    override fun enter(entity: Developer, prevState: State<Developer>?) {
        entity.setAnimation("idle")
        timer = 0f
        previousState = prevState?.name ?: DeveloperStateNames.IDLE
        // Show annoyed bubble when PM interrupts
        entity.showAnnoyedBubble()
    }

    override fun update(entity: Developer, dt: Float): String? {
        timer += dt
        if (timer >= duration) {
            return previousState
        }
        return null
    }

    override fun exit(entity: Developer, nextState: State<Developer>?) {
        // Hide the annoyed bubble when interrupt ends
        entity.showThoughtBubble(false)
    }

    override fun onEvent(entity: Developer, event: String, data: Any?): String? {
        return when (event) {
            "interrupt_ended" -> previousState
            else -> null
        }
    }
}

/**
 * Pre-configured state machine for Developer entities.
 */
class DeveloperStateMachine(
    entity: Developer,
    thinkingDuration: Float = 3.0f,
    despairDuration: Float = 2.0f,
    interruptDuration: Float = 3.0f
) : StateMachine<Developer>(entity) {
    init {
        addState(IdleState(), initial = true)
        addState(ThinkingState(thinkingDuration))
        addState(WalkingToWhiteboardState())
        addState(AtWhiteboardState())
        addState(WalkingToDeskState())
        addState(WritingCodeState())
        addState(TestsFailingState(despairDuration))
        addState(BeingInterruptedState(interruptDuration))
    }
}
