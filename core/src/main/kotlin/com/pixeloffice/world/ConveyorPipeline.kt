package com.pixeloffice.world

/**
 * CI/CD pipeline state for a single project.
 * Tracks stages and box (commit/PR) positions on the conveyor belt.
 */
class ConveyorPipeline {

    enum class StageStatus { IDLE, RUNNING, PASSED, FAILED }

    data class StageState(
        val name: String,
        var status: StageStatus = StageStatus.IDLE
    )

    val stages = listOf(
        StageState("lint"),
        StageState("test"),
        StageState("build"),
        StageState("deploy")
    )

    /** Position of the active box along the belt (0 = entry, 1 = exit). */
    var boxProgress: Float = 0f
        private set

    /** Whether a box is currently on the belt. */
    var boxActive: Boolean = false
        private set

    private var boxSpeed = 0.15f // belt units per second

    fun update(dt: Float) {
        if (boxActive) {
            boxProgress += boxSpeed * dt
            if (boxProgress > 1f) {
                boxProgress = 0f
                // Box completed the belt — reset for next cycle
            }
        }
    }

    /**
     * Update pipeline state from AO session status.
     */
    fun syncFromStatus(status: String, activity: String?) {
        // Reset all stages
        for (stage in stages) stage.status = StageStatus.IDLE

        boxActive = true

        when (status) {
            "implementing", "spawning", "working" -> {
                stages[0].status = StageStatus.RUNNING  // lint
                stages[1].status = StageStatus.RUNNING  // test
                boxProgress = 0.2f
            }
            "committed" -> {
                stages[0].status = StageStatus.PASSED
                stages[1].status = StageStatus.PASSED
                stages[2].status = StageStatus.RUNNING  // build
                boxProgress = 0.5f
            }
            "reviewing", "review_local", "pr_open", "review_pending" -> {
                stages[0].status = StageStatus.PASSED
                stages[1].status = StageStatus.PASSED
                stages[2].status = StageStatus.PASSED
                stages[3].status = StageStatus.RUNNING  // deploy
                boxProgress = 0.75f
            }
            "ci_failed", "merge_conflicts", "errored" -> {
                stages[0].status = StageStatus.PASSED
                stages[1].status = StageStatus.FAILED
                boxProgress = 0.35f
                boxSpeed = 0f  // box stopped
            }
            "approved", "approved_local", "mergeable" -> {
                for (stage in stages) stage.status = StageStatus.PASSED
                boxProgress = 0.9f
            }
            "merged", "done" -> {
                for (stage in stages) stage.status = StageStatus.PASSED
                boxProgress = 1f
                boxActive = false
            }
            else -> {
                boxActive = false
                boxProgress = 0f
            }
        }

        // Restore speed if not failed
        if (stages.none { it.status == StageStatus.FAILED }) {
            boxSpeed = 0.15f
        }
    }
}
