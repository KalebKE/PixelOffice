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

    // Separate debounce for status text patterns (independent of tool detection)
    private var lastStatusSignature: String? = null
    private var lastStatusTimestamp: Long = 0L

    // Track the last tool activity type for build/test result routing
    private var lastToolActivityType: ActivityType? = null
    private var lastToolTimestampForResult: Long = 0L
    private val RESULT_WINDOW_MS = 30_000L

    // Overlap from previous chunk to handle split tool names at boundaries
    private var previousChunkTail = ""
    private val OVERLAP_SIZE = 50

    // Plan mode tracking: suppress CODE_WRITING/CODE_EDITING while in plan mode
    private var inPlanMode = false

    // Code block tracking: toggled by ``` fences
    private var inCodeBlock = false

    // Tool detection: matches ToolName( preceded by a non-alpha character or start of string
    // Includes subagent type names (Explore, Plan) which TUI renders as AgentType(description)
    private val TOOL_PATTERN = Regex(
        """(?:^|[^A-Za-z])(Read|Edit|Write|Bash|Task|Glob|Grep|WebSearch|WebFetch|EnterPlanMode|ExitPlanMode|AskUserQuestion|NotebookEdit|Explore|Plan)\("""
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
    private val STATUS_AGENT_LAUNCH = Regex("""(?:(?:Launching|launching)\s+\w+\s+agent|(\d+)\s+\w+\s+agents?\s+launched)""")

    // Build/test result patterns (checked within RESULT_WINDOW_MS of a Bash command)
    private val BUILD_SUCCESS_PATTERN = Regex("""\bBUILD SUCCESSFUL\b""")
    private val BUILD_FAILED_PATTERN = Regex("""\bBUILD FAILED\b""")
    private val TEST_PASSED_PATTERN = Regex("""\bPASSED\b""")
    private val TEST_FAILED_PATTERN = Regex("""\bFAILED\b""")

    // Code fence detection (``` with optional language tag)
    private val CODE_FENCE_PATTERN = Regex("""```\w*""")

    // Code heuristics: high-confidence patterns for code output
    private val CODE_HEURISTIC_PATTERNS = listOf(
        Regex("""^\s*(fun|class|interface|object|import|package)\s+\w+""", RegexOption.MULTILINE),
        Regex("""^\s*(function|const|let|var|export)\s+\w+""", RegexOption.MULTILINE),
        Regex("""^\s*(def|class|from)\s+\w+""", RegexOption.MULTILINE),
        Regex("""^\s*(public|private|protected)\s+(static\s+)?(void|int|String|class|fun)\s""", RegexOption.MULTILINE)
    )

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

        // Track which ActivityTypes have been emitted in this chunk (for de-duplication)
        val emittedTypes = mutableSetOf<ActivityType>()

        // 1. Detect tool invocations
        for (match in TOOL_PATTERN.findAll(searchText)) {
            val toolName = match.groupValues[1]
            val isAgentSpawn = toolName in setOf("Task", "Explore", "Plan")
            val signature = if (isAgentSpawn) {
                // Include context after tool( to differentiate distinct agent calls
                val contextEnd = (match.range.last + 30).coerceAtMost(searchText.length)
                "$toolName:" + searchText.substring(match.range.first, contextEnd)
            } else {
                toolName
            }

            // Debounce: skip if same tool detected within window
            if (signature == lastToolSignature && (now - lastToolTimestamp) < DEBOUNCE_MS) {
                continue
            }
            lastToolSignature = signature
            lastToolTimestamp = now

            var activityType = when (toolName) {
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
                "ExitPlanMode" -> {
                    inPlanMode = false
                    ActivityType.THINKING
                }
                else -> Patterns.detectToolActivityFromName(toolName)
            }

            // Track plan mode entry
            if (activityType == ActivityType.PLANNING) {
                inPlanMode = true
            }

            // Suppress activities while in plan mode — keep developer at whiteboard with thinking bubble
            if (inPlanMode && activityType !in setOf(
                ActivityType.PLANNING, ActivityType.THINKING, ActivityType.FILE_READ,
                ActivityType.WEB_SEARCH, ActivityType.AGENT_SPAWN, ActivityType.USER_QUESTION
            )) {
                activityType = ActivityType.PLANNING
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
            emittedTypes.add(activityType)
        }

        // 2. Mode transition detection — runs on EVERY chunk regardless of tool matches
        val wasInPlanMode = inPlanMode
        when {
            STATUS_EDIT.containsMatchIn(searchText) || STATUS_WROTE.containsMatchIn(searchText) -> {
                inPlanMode = false
            }
            STATUS_PLAN_MODE.containsMatchIn(searchText) -> {
                if (!inPlanMode) {
                    inPlanMode = true
                    // Emit PLANNING immediately if not already emitted by tool detection
                    if (ActivityType.PLANNING !in emittedTypes) {
                        activities.add(DetectedActivity(type = ActivityType.PLANNING))
                        emittedTypes.add(ActivityType.PLANNING)
                    }
                }
            }
        }
        // If we just left plan mode, emit the right activity based on current status
        if (wasInPlanMode && !inPlanMode) {
            val hasCodeActivity = ActivityType.CODE_EDITING in emittedTypes ||
                ActivityType.CODE_WRITING in emittedTypes
            if (!hasCodeActivity) {
                val type = if (STATUS_THINKING.containsMatchIn(searchText)) {
                    ActivityType.THINKING
                } else {
                    ActivityType.CODE_EDITING
                }
                activities.add(DetectedActivity(type = type))
                emittedTypes.add(type)
            }
        }

        // 3. Status text detection (ALWAYS-ON, separate debounce from tool detection)
        val statusMatch = when {
            STATUS_PLAN_MODE.containsMatchIn(searchText) -> "status_plan_mode" to ActivityType.PLANNING
            STATUS_FILE_READ.containsMatchIn(searchText) -> "status_file_read" to ActivityType.FILE_READ
            STATUS_SEARCH.containsMatchIn(searchText) -> "status_search" to ActivityType.FILE_READ
            STATUS_EDIT.containsMatchIn(searchText) -> "status_edit" to ActivityType.CODE_EDITING
            STATUS_WROTE.containsMatchIn(searchText) -> "status_wrote" to ActivityType.CODE_WRITING
            STATUS_THINKING.containsMatchIn(searchText) -> "status_thinking" to ActivityType.THINKING
            else -> null
        }
        if (statusMatch != null) {
            val (sig, activityType) = statusMatch
            // Only emit if: not already emitted in this chunk AND not debounced
            if (activityType !in emittedTypes &&
                (sig != lastStatusSignature || (now - lastStatusTimestamp) >= DEBOUNCE_MS)) {
                lastStatusSignature = sig
                lastStatusTimestamp = now
                activities.add(DetectedActivity(type = activityType))
                emittedTypes.add(activityType)
            }
        }

        // Multi-agent launch detection: "3 Explore agents launched" → emit N AGENT_SPAWN
        if (ActivityType.AGENT_SPAWN !in emittedTypes) {
            val agentLaunchMatch = STATUS_AGENT_LAUNCH.find(searchText)
            if (agentLaunchMatch != null) {
                val countStr = agentLaunchMatch.groupValues.getOrNull(1)
                val count = countStr?.toIntOrNull() ?: 1
                val sig = "status_agent_$count"
                if (sig != lastStatusSignature || (now - lastStatusTimestamp) >= DEBOUNCE_MS) {
                    lastStatusSignature = sig
                    lastStatusTimestamp = now
                    repeat(count) {
                        activities.add(DetectedActivity(type = ActivityType.AGENT_SPAWN))
                    }
                    emittedTypes.add(ActivityType.AGENT_SPAWN)
                }
            }
        }

        // 4. Code block detection (fences + heuristics)
        val fenceMatches = CODE_FENCE_PATTERN.findAll(searchText).count()
        if (fenceMatches > 0) {
            // Each fence toggles the code block state (open/close)
            if (fenceMatches % 2 == 1) inCodeBlock = !inCodeBlock
            // Emit on opening fence (odd toggle count means we entered a block)
            if (inCodeBlock && !inPlanMode &&
                ActivityType.CODE_WRITING !in emittedTypes &&
                ActivityType.CODE_EDITING !in emittedTypes) {
                val sig = "code_block"
                if (sig != lastStatusSignature || (now - lastStatusTimestamp) >= DEBOUNCE_MS) {
                    lastStatusSignature = sig
                    lastStatusTimestamp = now
                    activities.add(DetectedActivity(type = ActivityType.CODE_WRITING))
                    emittedTypes.add(ActivityType.CODE_WRITING)
                }
            }
        } else if (!inCodeBlock) {
            // Heuristic check: code-like lines without fences
            val hasCodePattern = CODE_HEURISTIC_PATTERNS.any { it.containsMatchIn(searchText) }
            if (hasCodePattern && !inPlanMode &&
                ActivityType.CODE_WRITING !in emittedTypes &&
                ActivityType.CODE_EDITING !in emittedTypes) {
                val sig = "code_heuristic"
                if (sig != lastStatusSignature || (now - lastStatusTimestamp) >= DEBOUNCE_MS) {
                    lastStatusSignature = sig
                    lastStatusTimestamp = now
                    activities.add(DetectedActivity(type = ActivityType.CODE_WRITING))
                    emittedTypes.add(ActivityType.CODE_WRITING)
                }
            }
        }

        // 5. Build/test results (within result window of a Bash-type command)
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
        lastStatusSignature = null
        lastStatusTimestamp = 0L
        lastToolActivityType = null
        lastToolTimestampForResult = 0L
        previousChunkTail = ""
        inPlanMode = false
        inCodeBlock = false
    }

    /**
     * Get currently tracked active agents.
     */
    fun getActiveAgents(): Map<String, Map<String, Any>> = activeAgents.toMap()
}
