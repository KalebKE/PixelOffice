package com.pixeloffice.parsing

/**
 * Types of developer activities detected from Claude Code output.
 */
enum class ActivityType {
    AGENT_SPAWN,
    THINKING,
    CODE_WRITING,
    CODE_EDITING,
    TEST_EXECUTION,
    TEST_FAILURE,
    TEST_SUCCESS,
    USER_QUESTION,
    BASH_EXECUTION,
    WEB_SEARCH,
    FILE_READ,
    UNKNOWN
}

/**
 * A detected activity from parsing.
 */
data class DetectedActivity(
    val type: ActivityType,
    var agentId: String? = null,
    val toolName: String? = null,
    var details: Map<String, Any>? = null
)

/**
 * Pattern matchers for detecting Claude Code activities.
 */
object Patterns {
    // Tool names that indicate code writing
    private val CODE_WRITE_TOOLS = setOf("Write", "Edit", "NotebookEdit")

    // Tool names that indicate test execution
    private val TEST_TOOLS = setOf("Bash")

    // Patterns to detect test commands in Bash tool use
    private val TEST_COMMAND_PATTERNS = listOf(
        Regex("""\bpytest\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnpm\s+test\b""", RegexOption.IGNORE_CASE),
        Regex("""\byarn\s+test\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcargo\s+test\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgo\s+test\b""", RegexOption.IGNORE_CASE),
        Regex("""\bjest\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmocha\b""", RegexOption.IGNORE_CASE),
        Regex("""\brspec\b""", RegexOption.IGNORE_CASE),
        Regex("""\bunittest\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmake\s+test\b""", RegexOption.IGNORE_CASE)
    )

    // Patterns to detect test failures in output
    private val TEST_FAILURE_PATTERNS = listOf(
        Regex("""\bFAILED\b"""),
        Regex("""\bfailed\b"""),
        Regex("""\bError:\b"""),
        Regex("""\bERROR\b"""),
        Regex("""\bAssertionError\b"""),
        Regex("""\bexited with code [1-9]"""),
        Regex("""tests? failed""", RegexOption.IGNORE_CASE),
        Regex("""\bpanic\b"""),
        Regex("""\bFAIL\b""")
    )

    // Patterns to detect test success
    private val TEST_SUCCESS_PATTERNS = listOf(
        Regex("""\bPASSED\b"""),
        Regex("""\bpassed\b"""),
        Regex("""tests? passed""", RegexOption.IGNORE_CASE),
        Regex("""\bOK\b"""),
        Regex("""All tests passed""", RegexOption.IGNORE_CASE),
        Regex("""\b0 failed\b""")
    )

    /**
     * Detect activity type from a tool use.
     *
     * @param toolName Name of the tool being used.
     * @param toolInput Optional input parameters for the tool.
     * @return Detected activity type.
     */
    fun detectToolActivity(toolName: String, toolInput: Map<String, Any>? = null): ActivityType {
        return when (toolName) {
            "Task" -> ActivityType.AGENT_SPAWN
            "AskUserQuestion" -> ActivityType.USER_QUESTION
            "Write" -> ActivityType.CODE_WRITING
            "Edit", "NotebookEdit" -> ActivityType.CODE_EDITING
            "Bash" -> {
                val command = toolInput?.get("command") as? String ?: ""
                if (isTestCommand(command)) {
                    ActivityType.TEST_EXECUTION
                } else {
                    ActivityType.BASH_EXECUTION
                }
            }
            "Read", "Glob", "Grep" -> ActivityType.FILE_READ
            "WebSearch", "WebFetch" -> ActivityType.WEB_SEARCH
            else -> ActivityType.UNKNOWN
        }
    }

    /**
     * Check if a bash command is a test execution.
     *
     * @param command The bash command string.
     * @return True if this appears to be a test command.
     */
    fun isTestCommand(command: String): Boolean {
        return TEST_COMMAND_PATTERNS.any { pattern ->
            pattern.containsMatchIn(command)
        }
    }

    /**
     * Analyze tool output to detect test results.
     *
     * @param output The output from a tool execution.
     * @return TEST_FAILURE, TEST_SUCCESS, or null if not determinable.
     */
    fun detectTestResult(output: String): ActivityType? {
        // Check for failures first (takes precedence)
        for (pattern in TEST_FAILURE_PATTERNS) {
            if (pattern.containsMatchIn(output)) {
                return ActivityType.TEST_FAILURE
            }
        }

        // Check for success indicators
        for (pattern in TEST_SUCCESS_PATTERNS) {
            if (pattern.containsMatchIn(output)) {
                return ActivityType.TEST_SUCCESS
            }
        }

        return null
    }

    /**
     * Extract agent information from Task tool input.
     *
     * @param toolInput The Task tool input parameters.
     * @return Map with agent details.
     */
    fun extractAgentInfo(toolInput: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "description" to (toolInput["description"] ?: ""),
            "prompt" to (toolInput["prompt"] ?: ""),
            "subagent_type" to (toolInput["subagent_type"] ?: ""),
            "name" to (toolInput["name"] ?: "")
        )
    }
}
