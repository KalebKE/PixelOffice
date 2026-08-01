package com.pixeloffice.integration

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentStateMapperTest {
    @Test
    fun `maps every canonical state to an entity event`() {
        val expected = mapOf(
            AgentState.IDLE to "idle",
            AgentState.THINKING to "thinking_started",
            AgentState.PLANNING to "planning_started",
            AgentState.RESEARCHING to "researching_started",
            AgentState.CODING to "code_writing_started",
            AgentState.RUNNING to "command_started",
            AgentState.WAITING to "waiting_started",
            AgentState.SUCCESS to "command_succeeded",
            AgentState.FAILURE to "tests_failed"
        )

        assertEquals(expected, AgentState.entries.associateWith(AgentStateMapper::toEntityEvent))
    }
}
