package com.pixeloffice.world

import com.pixeloffice.parsing.ActivityType

/**
 * A recorded activity for an agent.
 */
data class ActivityRecord(
    val type: ActivityType,
    val toolName: String?,
    val context: String?,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * Tracks the current and recent activities for each agent.
 *
 * Uses a ring buffer per agent to maintain a bounded history.
 */
class AgentActivityTracker(private val historySize: Int = 8) {

    private val currentActivities = mutableMapOf<String, ActivityRecord>()
    private val histories = mutableMapOf<String, ArrayDeque<ActivityRecord>>()

    /**
     * Record a new activity for an agent. The previous activity (if any)
     * is pushed to the history ring buffer.
     */
    fun recordActivity(agentId: String, type: ActivityType, toolName: String? = null, context: String? = null) {
        val record = ActivityRecord(type, toolName, context)

        // Push previous current activity to history
        currentActivities[agentId]?.let { prev ->
            val history = histories.getOrPut(agentId) { ArrayDeque() }
            history.addLast(prev)
            if (history.size > historySize) {
                history.removeFirst()
            }
        }

        currentActivities[agentId] = record
    }

    /**
     * Get the current activity for an agent, or null if none recorded.
     */
    fun getCurrentActivity(agentId: String): ActivityRecord? = currentActivities[agentId]

    /**
     * Get the activity history for an agent (oldest first), excluding the current activity.
     */
    fun getHistory(agentId: String): List<ActivityRecord> {
        return histories[agentId]?.toList() ?: emptyList()
    }

    /**
     * Get all agent IDs currently being tracked.
     */
    fun getTrackedAgentIds(): Set<String> = currentActivities.keys.toSet()

    /**
     * Remove all tracking data for an agent.
     */
    fun removeAgent(agentId: String) {
        currentActivities.remove(agentId)
        histories.remove(agentId)
    }

    /**
     * Clear all tracking data.
     */
    fun clear() {
        currentActivities.clear()
        histories.clear()
    }
}
