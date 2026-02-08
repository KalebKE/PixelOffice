package com.pixeloffice.parsing

/**
 * Types of developer activities detected from Claude Code output.
 */
enum class ActivityType {
    AGENT_SPAWN,
    THINKING,
    PLANNING,
    CODE_WRITING,
    CODE_EDITING,
    TEST_EXECUTION,
    TEST_FAILURE,
    TEST_SUCCESS,
    BUILD_EXECUTION,
    BUILD_FAILURE,
    BUILD_SUCCESS,
    COMMITTING,
    INSTALLING_DEPS,
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

    // Patterns to detect build commands
    private val BUILD_COMMAND_PATTERNS = listOf(
        Regex("""\bgradlew?\b.*\b(compileKotlin|build|assemble)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmake\b(?!\s+test)""", RegexOption.IGNORE_CASE),
        Regex("""\bnpm\s+run\s+build\b""", RegexOption.IGNORE_CASE),
        Regex("""\byarn\s+build\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcargo\s+build\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgcc\b""", RegexOption.IGNORE_CASE),
        Regex("""\bg\+\+\b""", RegexOption.IGNORE_CASE),
        Regex("""\bjavac\b""", RegexOption.IGNORE_CASE),
        Regex("""\btsc\b"""),
        Regex("""\bcmake\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmvn\b""", RegexOption.IGNORE_CASE),
        Regex("""\bswiftc\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpnpm\s+run\s+build\b""", RegexOption.IGNORE_CASE)
    )

    // Patterns to detect commit/push commands
    private val COMMIT_COMMAND_PATTERNS = listOf(
        Regex("""\bgit\s+commit\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgit\s+push\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgh\s+pr\s+create\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgh\s+pr\s+merge\b""", RegexOption.IGNORE_CASE)
    )

    // Patterns to detect dependency installation commands
    private val INSTALL_COMMAND_PATTERNS = listOf(
        Regex("""\bnpm\s+install\b""", RegexOption.IGNORE_CASE),
        Regex("""\byarn\s+add\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpnpm\s+add\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpip\s+install\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcargo\s+add\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgo\s+get\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbundle\s+install\b""", RegexOption.IGNORE_CASE)
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

    // Patterns to detect build failures in output
    private val BUILD_FAILURE_PATTERNS = listOf(
        Regex("""\bBUILD FAILED\b"""),
        Regex("""\bcompilation error\b""", RegexOption.IGNORE_CASE),
        Regex("""\berror\[E\d+]"""),  // Rust compiler errors
        Regex("""\bTS\d+:"""),        // TypeScript errors
        Regex("""\bfatal error\b""", RegexOption.IGNORE_CASE),
        Regex("""\bBUILD FAILURE\b"""),
        Regex("""\bCompile error\b""", RegexOption.IGNORE_CASE)
    )

    // Patterns to detect build success in output
    private val BUILD_SUCCESS_PATTERNS = listOf(
        Regex("""\bBUILD SUCCESSFUL\b"""),
        Regex("""\bBuild complete\b""", RegexOption.IGNORE_CASE),
        Regex("""\b0 errors\b"""),
        Regex("""\bBUILD SUCCESS\b"""),
        Regex("""\bCompilation complete\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Detect activity type from a tool name alone (no input needed).
     * Used by streaming mode where tool_use_start has the name but not yet the input.
     * All tools except Bash are fully classifiable from name.
     * For Bash, returns BASH_EXECUTION as initial classification (refined later when input arrives).
     */
    fun detectToolActivityFromName(toolName: String): ActivityType {
        return when (toolName) {
            "Task" -> ActivityType.AGENT_SPAWN
            "AskUserQuestion" -> ActivityType.USER_QUESTION
            "EnterPlanMode" -> ActivityType.PLANNING
            "Write" -> ActivityType.CODE_WRITING
            "Edit", "NotebookEdit" -> ActivityType.CODE_EDITING
            "Read", "Glob", "Grep" -> ActivityType.FILE_READ
            "WebSearch", "WebFetch" -> ActivityType.WEB_SEARCH
            "Bash" -> ActivityType.BASH_EXECUTION
            else -> ActivityType.UNKNOWN
        }
    }

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
            "EnterPlanMode" -> ActivityType.PLANNING
            "Write" -> ActivityType.CODE_WRITING
            "Edit", "NotebookEdit" -> ActivityType.CODE_EDITING
            "Bash" -> classifyBashCommand(toolInput?.get("command") as? String ?: "")
            "Read", "Glob", "Grep" -> ActivityType.FILE_READ
            "WebSearch", "WebFetch" -> ActivityType.WEB_SEARCH
            else -> ActivityType.UNKNOWN
        }
    }

    /**
     * Classify a Bash command in priority order: test > build > commit > install > generic.
     */
    fun classifyBashCommand(command: String): ActivityType {
        if (isTestCommand(command)) return ActivityType.TEST_EXECUTION
        if (isBuildCommand(command)) return ActivityType.BUILD_EXECUTION
        if (isCommitCommand(command)) return ActivityType.COMMITTING
        if (isInstallCommand(command)) return ActivityType.INSTALLING_DEPS
        return ActivityType.BASH_EXECUTION
    }

    /**
     * Check if a bash command is a test execution.
     *
     * @param command The bash command string.
     * @return True if this appears to be a test command.
     */
    fun isTestCommand(command: String): Boolean {
        return TEST_COMMAND_PATTERNS.any { it.containsMatchIn(command) }
    }

    fun isBuildCommand(command: String): Boolean {
        return BUILD_COMMAND_PATTERNS.any { it.containsMatchIn(command) }
    }

    fun isCommitCommand(command: String): Boolean {
        return COMMIT_COMMAND_PATTERNS.any { it.containsMatchIn(command) }
    }

    fun isInstallCommand(command: String): Boolean {
        return INSTALL_COMMAND_PATTERNS.any { it.containsMatchIn(command) }
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
     * Analyze tool output to detect build results.
     *
     * @param output The output from a build execution.
     * @return BUILD_FAILURE, BUILD_SUCCESS, or null if not determinable.
     */
    fun detectBuildResult(output: String): ActivityType? {
        for (pattern in BUILD_FAILURE_PATTERNS) {
            if (pattern.containsMatchIn(output)) {
                return ActivityType.BUILD_FAILURE
            }
        }

        for (pattern in BUILD_SUCCESS_PATTERNS) {
            if (pattern.containsMatchIn(output)) {
                return ActivityType.BUILD_SUCCESS
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
