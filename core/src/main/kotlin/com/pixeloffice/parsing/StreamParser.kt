package com.pixeloffice.parsing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A parsed message from the Claude Code stream.
 */
data class ParsedMessage(
    val type: String,
    val content: String? = null,
    val toolName: String? = null,
    val toolInput: Map<String, Any>? = null,
    val toolResult: String? = null,
    val agentId: String? = null,
    val raw: JsonObject? = null
)

/**
 * Parser for Claude Code JSON streaming output.
 *
 * Parses the stream-json format from Claude Code CLI and detects
 * activities that should trigger developer animations.
 */
class StreamParser {
    private var buffer = ""
    private var currentAgentId: String? = null
    private val activeAgents = mutableMapOf<String, Map<String, Any>>()

    // Track last tool so tool_result can be routed to the correct detector
    private var lastToolName: String? = null
    private var lastToolActivityType: ActivityType? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Feed data to the parser and return detected activities.
     *
     * @param data Raw string data from the Claude Code stream.
     * @return List of detected activities for the visualization.
     */
    fun feed(data: String): List<DetectedActivity> {
        val activities = mutableListOf<DetectedActivity>()
        buffer += data

        // Process complete lines (JSON objects are one per line)
        while ("\n" in buffer) {
            val newlineIndex = buffer.indexOf("\n")
            val line = buffer.substring(0, newlineIndex).trim()
            buffer = buffer.substring(newlineIndex + 1)

            if (line.isEmpty()) continue

            try {
                val parsed = parseLine(line)
                if (parsed != null) {
                    activities.addAll(processMessage(parsed))
                }
            } catch (e: Exception) {
                // Skip malformed JSON
                continue
            }
        }

        return activities
    }

    private fun parseLine(line: String): ParsedMessage? {
        // Strip any ANSI codes that might have leaked through
        val cleanLine = AnsiStripper.stripAnsi(line)

        val data: JsonObject = try {
            json.parseToJsonElement(cleanLine).jsonObject
        } catch (e: Exception) {
            return null
        }

        val msgType = data["type"]?.jsonPrimitive?.contentOrNull ?: ""

        when (msgType) {
            "content_block_start" -> {
                val contentBlock = data["content_block"]?.jsonObject
                val blockType = contentBlock?.get("type")?.jsonPrimitive?.contentOrNull ?: ""

                return when (blockType) {
                    "tool_use" -> ParsedMessage(
                        type = "tool_use_start",
                        toolName = contentBlock?.get("name")?.jsonPrimitive?.contentOrNull,
                        raw = data
                    )
                    "text" -> ParsedMessage(
                        type = "text_start",
                        raw = data
                    )
                    else -> null
                }
            }
            "content_block_delta" -> {
                val delta = data["delta"]?.jsonObject
                val deltaType = delta?.get("type")?.jsonPrimitive?.contentOrNull ?: ""

                return when (deltaType) {
                    "input_json_delta" -> ParsedMessage(
                        type = "tool_input_delta",
                        content = delta?.get("partial_json")?.jsonPrimitive?.contentOrNull,
                        raw = data
                    )
                    "text_delta" -> ParsedMessage(
                        type = "text_delta",
                        content = delta?.get("text")?.jsonPrimitive?.contentOrNull,
                        raw = data
                    )
                    else -> null
                }
            }
            "content_block_stop" -> {
                return ParsedMessage(
                    type = "content_block_stop",
                    raw = data
                )
            }
            "message_start" -> {
                return ParsedMessage(
                    type = "message_start",
                    raw = data
                )
            }
            "message_stop" -> {
                return ParsedMessage(
                    type = "message_stop",
                    raw = data
                )
            }
            "tool_result" -> {
                return ParsedMessage(
                    type = "tool_result",
                    toolResult = data["content"]?.jsonPrimitive?.contentOrNull,
                    raw = data
                )
            }
        }

        // Handle direct tool use messages (non-streaming format)
        if (data.containsKey("tool_use")) {
            val toolUse = data["tool_use"]?.jsonObject
            val toolInput = toolUse?.get("input")?.jsonObject?.let { input ->
                input.entries.associate { (k, v) ->
                    k to (v.jsonPrimitive.contentOrNull ?: "")
                }
            }

            return ParsedMessage(
                type = "tool_use",
                toolName = toolUse?.get("name")?.jsonPrimitive?.contentOrNull,
                toolInput = toolInput,
                raw = data
            )
        }

        return null
    }

    private fun processMessage(msg: ParsedMessage): List<DetectedActivity> {
        val activities = mutableListOf<DetectedActivity>()

        when (msg.type) {
            "tool_use_start" -> {
                if (msg.toolName != null) {
                    val activityType = Patterns.detectToolActivityFromName(msg.toolName)
                    lastToolName = msg.toolName
                    lastToolActivityType = activityType

                    activities.add(DetectedActivity(
                        type = activityType,
                        agentId = currentAgentId,
                        toolName = msg.toolName
                    ))
                }
            }
            "tool_use" -> {
                if (msg.toolName != null) {
                    // Direct tool use message
                    val activityType = Patterns.detectToolActivity(msg.toolName, msg.toolInput)

                    // Track for result routing
                    lastToolName = msg.toolName
                    lastToolActivityType = activityType

                    val activity = DetectedActivity(
                        type = activityType,
                        agentId = currentAgentId,
                        toolName = msg.toolName,
                        details = msg.toolInput
                    )

                    // Handle agent spawn specially
                    if (activityType == ActivityType.AGENT_SPAWN && msg.toolInput != null) {
                        val agentInfo = Patterns.extractAgentInfo(msg.toolInput)
                        val agentId = msg.toolInput["name"] as? String
                            ?: agentInfo["description"] as? String
                        if (agentId != null) {
                            activeAgents[agentId] = agentInfo
                            activity.agentId = agentId
                            activity.details = agentInfo
                        }
                    }

                    activities.add(activity)
                }
            }
            "text_start", "text_delta" -> {
                // Text output indicates thinking
                activities.add(DetectedActivity(
                    type = ActivityType.THINKING,
                    agentId = currentAgentId
                ))
            }
            "tool_result" -> {
                if (msg.toolResult != null) {
                    val resultType = detectResultForLastTool(msg.toolResult)
                    if (resultType != null) {
                        activities.add(DetectedActivity(
                            type = resultType,
                            agentId = currentAgentId
                        ))
                    }
                }
                // Clear tool context after processing result
                lastToolName = null
                lastToolActivityType = null
            }
        }

        return activities
    }

    /**
     * Route tool_result to the correct detector based on the last tool's activity type.
     */
    private fun detectResultForLastTool(output: String): ActivityType? {
        return when (lastToolActivityType) {
            ActivityType.BUILD_EXECUTION -> Patterns.detectBuildResult(output)
            ActivityType.TEST_EXECUTION -> Patterns.detectTestResult(output)
            else -> {
                // Unknown or unclassified — try test first, then build (preserves existing behavior)
                Patterns.detectTestResult(output) ?: Patterns.detectBuildResult(output)
            }
        }
    }

    /**
     * Reset parser state.
     */
    fun reset() {
        buffer = ""
        currentAgentId = null
        activeAgents.clear()
        lastToolName = null
        lastToolActivityType = null
    }

    /**
     * Get currently tracked active agents.
     */
    fun getActiveAgents(): Map<String, Map<String, Any>> = activeAgents.toMap()
}
