package com.pixeloffice.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Types of events in the Pixel Office system.
 */
enum class EventType {
    // Agent lifecycle events
    AGENT_SPAWNED,
    AGENT_FINISHED,

    // Developer activity events
    THINKING_STARTED,
    THINKING_ENDED,
    CODE_WRITING_STARTED,
    CODE_WRITING_ENDED,
    TEST_STARTED,
    TEST_PASSED,
    TEST_FAILED,

    // Interaction events
    USER_QUESTION_ASKED,
    USER_QUESTION_ANSWERED,

    // Network events
    CONNECTION_ESTABLISHED,
    CONNECTION_LOST,
    DATA_RECEIVED,

    // Internal events
    DEVELOPER_ARRIVED_AT_DESK,
    DEVELOPER_ARRIVED_AT_WHITEBOARD,
    PM_INTERRUPT,
    PO_ARRIVED,
    PO_LEFT
}

/**
 * An event that can be published through the event bus.
 */
data class Event(
    val type: EventType,
    val data: Map<String, Any> = emptyMap(),
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Type alias for event handlers.
 */
typealias EventHandler = (Event) -> Unit

/**
 * A simple pub/sub event bus for decoupled component communication.
 *
 * Thread-safe implementation using concurrent collections.
 */
class EventBus {
    private val handlers = ConcurrentHashMap<EventType, CopyOnWriteArrayList<EventHandler>>()
    private val globalHandlers = CopyOnWriteArrayList<EventHandler>()

    /**
     * Subscribe a handler to a specific event type.
     */
    fun subscribe(eventType: EventType, handler: EventHandler) {
        handlers.computeIfAbsent(eventType) { CopyOnWriteArrayList() }.add(handler)
    }

    /**
     * Subscribe a handler to all event types.
     */
    fun subscribeAll(handler: EventHandler) {
        if (handler !in globalHandlers) {
            globalHandlers.add(handler)
        }
    }

    /**
     * Unsubscribe a handler from a specific event type.
     */
    fun unsubscribe(eventType: EventType, handler: EventHandler) {
        handlers[eventType]?.remove(handler)
    }

    /**
     * Unsubscribe a handler from all events.
     */
    fun unsubscribeAll(handler: EventHandler) {
        globalHandlers.remove(handler)
    }

    /**
     * Publish an event to all subscribed handlers.
     */
    fun publish(event: Event) {
        // Notify type-specific handlers
        handlers[event.type]?.forEach { handler ->
            handler(event)
        }

        // Notify global handlers
        globalHandlers.forEach { handler ->
            handler(event)
        }
    }

    /**
     * Convenience method to create and publish an event.
     */
    fun emit(
        eventType: EventType,
        data: Map<String, Any> = emptyMap(),
        source: String? = null
    ) {
        publish(Event(
            type = eventType,
            data = data,
            source = source
        ))
    }

    /**
     * Remove all handlers.
     */
    fun clear() {
        handlers.clear()
        globalHandlers.clear()
    }
}
