package com.pixeloffice.integration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AgentEventKind {
    @SerialName("upsert")
    UPSERT,

    @SerialName("touch")
    TOUCH,

    @SerialName("end")
    END
}

@Serializable
enum class AgentState {
    @SerialName("idle")
    IDLE,

    @SerialName("thinking")
    THINKING,

    @SerialName("planning")
    PLANNING,

    @SerialName("researching")
    RESEARCHING,

    @SerialName("coding")
    CODING,

    @SerialName("running")
    RUNNING,

    @SerialName("waiting")
    WAITING,

    @SerialName("success")
    SUCCESS,

    @SerialName("failure")
    FAILURE
}

@Serializable
data class AgentEvent(
    val version: Int = 1,
    val provider: String,
    val sessionId: String,
    val agentId: String = "main",
    val projectId: String,
    val projectLabel: String,
    val kind: AgentEventKind,
    val state: AgentState? = null,
    val activity: String? = null,
    val occurredAt: Long
)

data class AgentSessionSnapshot(
    val id: String,
    val provider: String,
    val sessionId: String,
    val agentId: String,
    val projectId: String,
    val projectLabel: String,
    val state: AgentState,
    val activity: String?,
    val lastActivityAt: Long
)
