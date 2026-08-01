package com.pixeloffice.integration

object AgentStateMapper {
    fun toEntityEvent(state: AgentState): String {
        return when (state) {
            AgentState.IDLE -> "idle"
            AgentState.THINKING -> "thinking_started"
            AgentState.PLANNING -> "planning_started"
            AgentState.RESEARCHING -> "researching_started"
            AgentState.CODING -> "code_writing_started"
            AgentState.RUNNING -> "command_started"
            AgentState.WAITING -> "waiting_started"
            AgentState.SUCCESS -> "command_succeeded"
            AgentState.FAILURE -> "tests_failed"
        }
    }
}
