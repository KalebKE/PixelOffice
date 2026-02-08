package com.pixeloffice.parsing

/**
 * Parser for raw terminal output from Claude Code via tmux pipe-pane.
 *
 * Detects tool invocations from TUI-rendered lines like `⏺Read(file_path)`,
 * `⏺Bash(command)`, status text like `Reading 1 file…`, and build/test
 * result lines like `BUILD SUCCESSFUL` / `FAILED`.
 *
 * TUI redraws send the same content repeatedly, so detected tool signatures
 * are debounced within a 2-second window.
 */
class StreamParser {
    private var buffer = ""
    private val activeAgents = mutableMapOf<String, Map<String, Any>>()

    // Debouncing: last detected tool signature → timestamp (ms)
    private var lastToolSignature: String? = null
    private var lastToolTimestamp: Long = 0L
    private val DEBOUNCE_MS = 2000L

    // Track the last tool activity type for build/test result routing
    private var lastToolActivityType: ActivityType? = null
    private var lastToolTimestampForResult: Long = 0L
    private val RESULT_WINDOW_MS = 30_000L

    // Overlap from previous chunk to handle split tool names at boundaries
    private var previousChunkTail = ""
    private val OVERLAP_SIZE = 50

    // Tool detection: matches ToolName( preceded by a non-alpha character or start of string
    private val TOOL_PATTERN = Regex(
        """(?:^|[^A-Za-z])(Read|Edit|Write|Bash|Task|Glob|Grep|WebSearch|WebFetch|EnterPlanMode|AskUserQuestion|NotebookEdit)\("""
    )

    // Extract command from Bash(command) — captures content inside parens
    private val BASH_COMMAND_PATTERN = Regex(
        """(?:^|[^A-Za-z])Bash\(([^)]*)\)"""
    )

    // Status text patterns (auto-approved tools show these instead of ToolName(...))
    private val STATUS_FILE_READ = Regex("""[Rr]eading \d+ file""")
    private val STATUS_SEARCH = Regex("""[Ss]earch(?:ed|ing) for \d+ pattern""")
    private val STATUS_EDIT = Regex("""accept edits on""")
    private val STATUS_WROTE = Regex("""Wrote to """)
    private val STATUS_PLAN_MODE = Regex("""plan mode on""", RegexOption.IGNORE_CASE)
    private val STATUS_THINKING = Regex("""\(thinking\)""")

    // Build/test result patterns (checked within RESULT_WINDOW_MS of a Bash command)
    private val BUILD_SUCCESS_PATTERN = Regex("""\bBUILD SUCCESSFUL\b""")
    private val BUILD_FAILED_PATTERN = Regex("""\bBUILD FAILED\b""")
    private val TEST_PASSED_PATTERN = Regex("""\bPASSED\b""")
    private val TEST_FAILED_PATTERN = Regex("""\bFAILED\b""")

    /**
     * Feed data to the parser and return detected activities.
     *
     * @param data Raw string data from the tmux pipe-pane stream.
     * @return List of detected activities for the visualization.
     */
    fun feed(data: String): List<DetectedActivity> {
        val activities = mutableListOf<DetectedActivity>()

        // Strip ANSI escape codes and normalize
        val clean = AnsiStripper.stripAnsiAndNormalize(data)

        // Prepend overlap from previous chunk to handle split tool names
        val searchText = previousChunkTail + clean

        // Save tail for next chunk
        previousChunkTail = if (clean.length > OVERLAP_SIZE) {
            clean.substring(clean.length - OVERLAP_SIZE)
        } else {
            clean
        }

        val now = System.currentTimeMillis()

        // 1. Detect tool invocations
        for (match in TOOL_PATTERN.findAll(searchText)) {
            val toolName = match.groupValues[1]
            val signature = toolName

            // Debounce: skip if same tool detected within window
            if (signature == lastToolSignature && (now - lastToolTimestamp) < DEBOUNCE_MS) {
                continue
            }
            lastToolSignature = signature
            lastToolTimestamp = now

            val activityType = when (toolName) {
                "Bash" -> {
                    // Try to extract and classify the bash command
                    val bashMatch = BASH_COMMAND_PATTERN.find(searchText, match.range.first)
                    val command = bashMatch?.groupValues?.get(1) ?: ""
                    val classified = if (command.isNotEmpty()) {
                        Patterns.classifyBashCommand(command)
                    } else {
                        ActivityType.BASH_EXECUTION
                    }
                    classified
                }
                else -> Patterns.detectToolActivityFromName(toolName)
            }

            // Track for result routing
            if (activityType == ActivityType.BUILD_EXECUTION ||
                activityType == ActivityType.TEST_EXECUTION ||
                activityType == ActivityType.BASH_EXECUTION) {
                lastToolActivityType = activityType
                lastToolTimestampForResult = now
            }

            activities.add(DetectedActivity(
                type = activityType,
                toolName = toolName
            ))
        }

        // 2. Status text fallback (auto-approved tools don't show ToolName(...))
        if (activities.isEmpty()) {
            val statusMatch = when {
                STATUS_FILE_READ.containsMatchIn(searchText) -> "status_file_read" to ActivityType.FILE_READ
                STATUS_SEARCH.containsMatchIn(searchText) -> "status_search" to ActivityType.FILE_READ
                STATUS_EDIT.containsMatchIn(searchText) -> "status_edit" to ActivityType.CODE_EDITING
                STATUS_WROTE.containsMatchIn(searchText) -> "status_wrote" to ActivityType.CODE_WRITING
                STATUS_PLAN_MODE.containsMatchIn(searchText) -> "status_plan_mode" to ActivityType.PLANNING
                STATUS_THINKING.containsMatchIn(searchText) -> "status_thinking" to ActivityType.THINKING
                else -> null
            }
            if (statusMatch != null) {
                val (sig, activityType) = statusMatch
                if (sig != lastToolSignature || (now - lastToolTimestamp) >= DEBOUNCE_MS) {
                    lastToolSignature = sig
                    lastToolTimestamp = now
                    activities.add(DetectedActivity(type = activityType))
                }
            }
        }

        // 3. Build/test results (within result window of a Bash-type command)
        if (lastToolActivityType != null && (now - lastToolTimestampForResult) < RESULT_WINDOW_MS) {
            val resultActivity = detectBuildTestResult(searchText)
            if (resultActivity != null) {
                activities.add(DetectedActivity(type = resultActivity))
                // Clear so we don't re-detect the same result
                lastToolActivityType = null
            }
        }

        return activities
    }

    /**
     * Detect build/test result patterns in text.
     */
    private fun detectBuildTestResult(text: String): ActivityType? {
        // Check failures first (higher priority)
        if (BUILD_FAILED_PATTERN.containsMatchIn(text)) return ActivityType.BUILD_FAILURE
        if (TEST_FAILED_PATTERN.containsMatchIn(text)) {
            // Only treat as test failure if last tool was a test command
            if (lastToolActivityType == ActivityType.TEST_EXECUTION) {
                return ActivityType.TEST_FAILURE
            }
            // For build commands, use the full Patterns detector
            return Patterns.detectBuildResult(text) ?: Patterns.detectTestResult(text)
        }
        if (BUILD_SUCCESS_PATTERN.containsMatchIn(text)) return ActivityType.BUILD_SUCCESS
        if (TEST_PASSED_PATTERN.containsMatchIn(text)) {
            if (lastToolActivityType == ActivityType.TEST_EXECUTION) {
                return ActivityType.TEST_SUCCESS
            }
            return Patterns.detectBuildResult(text) ?: Patterns.detectTestResult(text)
        }
        return null
    }

    /**
     * Reset parser state.
     */
    fun reset() {
        buffer = ""
        activeAgents.clear()
        lastToolSignature = null
        lastToolTimestamp = 0L
        lastToolActivityType = null
        lastToolTimestampForResult = 0L
        previousChunkTail = ""
    }

    /**
     * Get currently tracked active agents.
     */
    fun getActiveAgents(): Map<String, Map<String, Any>> = activeAgents.toMap()
}
