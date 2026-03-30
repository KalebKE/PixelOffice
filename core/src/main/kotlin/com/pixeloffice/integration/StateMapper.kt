package com.pixeloffice.integration

object StateMapper {
    /**
     * Returns an event string the Developer state machine can handle, or null for no change.
     * Returns "__despawn__" for terminal states that should remove the developer.
     */
    fun mapToEvent(status: String, activity: String?): String? {
        return when (status) {
            "planning" -> "thinking_started" // agent at whiteboard, planning before coding
            "implementing" -> when (activity) {
                "active" -> "code_writing_started"
                "ready" -> "thinking_started"
                "waiting_input" -> "interrupted"
                "blocked" -> "tests_failed"
                else -> null // idle, no change
            }
            "committed" -> "building_started" // gear icon, waiting for build
            "reviewing", "review_local" -> "researching_started"
            "ci_failed" -> "ci_failed"
            "merge_conflicts", "errored" -> "tests_failed"
            "approved", "approved_local" -> "command_succeeded"
            "mergeable" -> "merging" // thumbs up bubble, PR about to ship
            "changes_requested" -> when (activity) {
                "active" -> "code_writing_started"
                else -> "thinking_started"
            }
            "needs_input", "stuck" -> "interrupted"
            "pr_open", "review_pending", "local_review" -> "researching_started" // code review
            "spawning", "working", "idle" -> when (activity) {
                "active" -> "code_writing_started"
                else -> null // stay in current state
            }
            // Terminal states - return special marker
            "merged", "killed", "terminated", "done", "cleanup" -> "__despawn__"
            else -> null
        }
    }

    fun isTerminal(status: String): Boolean {
        return status in setOf("merged", "killed", "terminated", "done", "cleanup")
    }
}
