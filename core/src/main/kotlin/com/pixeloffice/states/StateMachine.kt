package com.pixeloffice.states

/**
 * Abstract base class for states in the state machine.
 *
 * Type parameter T represents the entity type that owns this state.
 */
abstract class State<T>(val name: String) {
    /**
     * Called when entering this state.
     *
     * @param entity The entity this state belongs to.
     * @param prevState The previous state, if any.
     */
    abstract fun enter(entity: T, prevState: State<T>?)

    /**
     * Update the state.
     *
     * @param entity The entity this state belongs to.
     * @param dt Delta time in seconds since last update.
     * @return Name of state to transition to, or null to stay in current state.
     */
    abstract fun update(entity: T, dt: Float): String?

    /**
     * Called when exiting this state.
     *
     * @param entity The entity this state belongs to.
     * @param nextState The next state, if any.
     */
    abstract fun exit(entity: T, nextState: State<T>?)

    /**
     * Handle an event while in this state.
     *
     * @param entity The entity this state belongs to.
     * @param event The event name.
     * @param data Optional event data.
     * @return Name of state to transition to, or null to stay in current state.
     */
    open fun onEvent(entity: T, event: String, data: Any? = null): String? = null
}

/**
 * A generic finite state machine.
 *
 * Manages state transitions for an entity of type T.
 */
open class StateMachine<T>(protected val entity: T) {
    private val states = mutableMapOf<String, State<T>>()
    private var currentStateInternal: State<T>? = null
    private var initialState: String? = null

    val currentState: State<T>?
        get() = currentStateInternal

    val currentStateName: String?
        get() = currentStateInternal?.name

    /**
     * Add a state to the machine.
     *
     * @param state The state to add.
     * @param initial If true, set this as the initial state.
     */
    fun addState(state: State<T>, initial: Boolean = false) {
        states[state.name] = state
        if (initial) {
            initialState = state.name
        }
    }

    /**
     * Start the state machine with the initial state.
     */
    fun start() {
        initialState?.let { transitionTo(it) }
    }

    /**
     * Transition to a new state.
     *
     * @param stateName Name of the state to transition to.
     * @return True if transition was successful.
     */
    fun transitionTo(stateName: String): Boolean {
        val newState = states[stateName] ?: return false

        val oldState = currentStateInternal
        oldState?.exit(entity, newState)

        currentStateInternal = newState
        newState.enter(entity, oldState)

        return true
    }

    /**
     * Update the current state.
     *
     * @param dt Delta time in seconds since last update.
     */
    fun update(dt: Float) {
        currentStateInternal?.let { state ->
            val nextState = state.update(entity, dt)
            nextState?.let { transitionTo(it) }
        }
    }

    /**
     * Send an event to the current state.
     *
     * @param event The event name.
     * @param data Optional event data.
     */
    fun handleEvent(event: String, data: Any? = null) {
        currentStateInternal?.let { state ->
            val nextState = state.onEvent(entity, event, data)
            nextState?.let { transitionTo(it) }
        }
    }

    /**
     * Check if currently in the given state.
     */
    fun isInState(stateName: String): Boolean {
        return currentStateInternal?.name == stateName
    }
}
