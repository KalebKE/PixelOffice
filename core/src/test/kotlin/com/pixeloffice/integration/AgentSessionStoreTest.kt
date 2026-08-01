package com.pixeloffice.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentSessionStoreTest {
    private var now = 1_000L
    private val store = AgentSessionStore(staleAfterMs = 500L) { now }

    @Test
    fun `upserts touches ignores old updates and ends sessions`() {
        assertTrue(store.applyEvent(event(state = AgentState.CODING, occurredAt = 1_000L)))
        assertEquals(AgentState.CODING, store.snapshots().single().state)

        assertFalse(store.applyEvent(event(kind = AgentEventKind.TOUCH, occurredAt = 1_100L)))
        assertEquals(1_100L, store.snapshots().single().lastActivityAt)

        assertFalse(store.applyEvent(event(state = AgentState.PLANNING, occurredAt = 1_050L)))
        assertEquals(AgentState.CODING, store.snapshots().single().state)

        assertTrue(store.applyEvent(event(kind = AgentEventKind.END, occurredAt = 1_200L)))
        assertTrue(store.snapshots().isEmpty())
    }

    @Test
    fun `expires abandoned sessions`() {
        store.applyEvent(event(state = AgentState.IDLE, occurredAt = 1_000L))
        now = 1_500L
        assertFalse(store.expireStaleSessions())
        now = 1_501L
        assertTrue(store.expireStaleSessions())
        assertEquals(0, store.size())
    }

    @Test
    fun `rejects unsupported versions and upserts without state`() {
        assertFalse(
            store.applyEvent(
                event(state = AgentState.THINKING, occurredAt = 1_000L).copy(version = 2)
            )
        )
        assertFalse(store.applyEvent(event(occurredAt = 1_000L)))
        assertTrue(store.snapshots().isEmpty())
    }

    private fun event(
        kind: AgentEventKind = AgentEventKind.UPSERT,
        state: AgentState? = null,
        occurredAt: Long
    ) = AgentEvent(
        provider = "codex",
        sessionId = "session-1",
        projectId = "/tmp/project",
        projectLabel = "project",
        kind = kind,
        state = state,
        occurredAt = occurredAt
    )
}
