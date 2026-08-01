package com.pixeloffice.integration

class AgentSessionStore(
    private val staleAfterMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val sessions = linkedMapOf<String, AgentSessionSnapshot>()

    fun applyEvent(event: AgentEvent): Boolean {
        if (event.version != 1) return false

        val key = key(event.provider, event.sessionId, event.agentId)
        val current = sessions[key]
        if (current != null && event.occurredAt < current.lastActivityAt) return false

        return when (event.kind) {
            AgentEventKind.END -> sessions.remove(key) != null
            AgentEventKind.TOUCH -> {
                if (current == null) {
                    false
                } else {
                    sessions[key] = current.copy(lastActivityAt = event.occurredAt)
                    false
                }
            }
            AgentEventKind.UPSERT -> {
                val state = event.state ?: return false
                val next = AgentSessionSnapshot(
                    id = key,
                    provider = event.provider,
                    sessionId = event.sessionId,
                    agentId = event.agentId,
                    projectId = event.projectId,
                    projectLabel = event.projectLabel,
                    state = state,
                    activity = event.activity,
                    lastActivityAt = event.occurredAt
                )
                sessions[key] = next
                current?.copy(lastActivityAt = next.lastActivityAt) != next
            }
        }
    }

    fun expireStaleSessions(now: Long = clock()): Boolean {
        val staleKeys = sessions
            .filterValues { now - it.lastActivityAt > staleAfterMs }
            .keys
        staleKeys.forEach(sessions::remove)
        return staleKeys.isNotEmpty()
    }

    fun snapshots(): List<AgentSessionSnapshot> = sessions.values.toList()

    fun size(): Int = sessions.size

    private fun key(provider: String, sessionId: String, agentId: String): String =
        "$provider:$sessionId:$agentId"
}
