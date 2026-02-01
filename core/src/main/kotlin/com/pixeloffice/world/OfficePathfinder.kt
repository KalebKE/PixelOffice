package com.pixeloffice.world

/**
 * Calculates navigation paths around office obstacles.
 *
 * Developers need to navigate around desk walls and partitions
 * instead of walking through them. This pathfinder creates
 * waypoint-based paths using hardcoded office layout knowledge.
 */
class OfficePathfinder {

    companion object {
        // Exit corridors on left and right sides of desk clusters
        const val LEFT_EXIT_X = 30f
        const val RIGHT_EXIT_X = 120f

        // Safe corridor above desk walls (walls are at Y=125)
        const val UPPER_CORRIDOR_Y = 110f

        // X coordinate dividing left and right desk clusters
        const val PARTITION_X = 81f

        // Desk row Y coordinate
        const val DESK_ROW_Y = 136f
    }

    /**
     * Calculate a path from start to end that avoids obstacles.
     *
     * @param startX Starting X position
     * @param startY Starting Y position
     * @param endX Destination X position
     * @param endY Destination Y position
     * @return List of waypoints to follow (including final destination)
     */
    fun calculatePath(startX: Float, startY: Float, endX: Float, endY: Float): List<Pair<Float, Float>> {
        val path = mutableListOf<Pair<Float, Float>>()

        // Determine if we're in the desk area (below corridor)
        val startInDeskArea = startY > UPPER_CORRIDOR_Y
        val endInDeskArea = endY > UPPER_CORRIDOR_Y

        // Determine which side of the partition we're on
        val startOnLeft = startX < PARTITION_X
        val endOnLeft = endX < PARTITION_X

        if (startInDeskArea && !endInDeskArea) {
            // Moving from desk area to upper area (e.g., to whiteboard)
            // Step 1: Exit to the corridor (left or right based on desk side)
            val exitX = if (startOnLeft) LEFT_EXIT_X else RIGHT_EXIT_X
            path.add(Pair(exitX, startY))

            // Step 2: Move up to the corridor
            path.add(Pair(exitX, UPPER_CORRIDOR_Y))

            // Step 3: Go to destination
            path.add(Pair(endX, endY))

        } else if (!startInDeskArea && endInDeskArea) {
            // Moving from upper area to desk area (e.g., returning to desk)
            // Step 1: Move to the appropriate corridor exit point
            val exitX = if (endOnLeft) LEFT_EXIT_X else RIGHT_EXIT_X
            path.add(Pair(exitX, UPPER_CORRIDOR_Y))

            // Step 2: Move down to desk row level
            path.add(Pair(exitX, endY))

            // Step 3: Go to destination desk
            path.add(Pair(endX, endY))

        } else if (startInDeskArea && endInDeskArea && startOnLeft != endOnLeft) {
            // Moving between desk clusters (crossing the partition)
            // Step 1: Exit current desk cluster
            val startExitX = if (startOnLeft) LEFT_EXIT_X else RIGHT_EXIT_X
            path.add(Pair(startExitX, startY))

            // Step 2: Move up to corridor
            path.add(Pair(startExitX, UPPER_CORRIDOR_Y))

            // Step 3: Move across to other side's exit
            val endExitX = if (endOnLeft) LEFT_EXIT_X else RIGHT_EXIT_X
            path.add(Pair(endExitX, UPPER_CORRIDOR_Y))

            // Step 4: Move down to desk row
            path.add(Pair(endExitX, endY))

            // Step 5: Go to destination
            path.add(Pair(endX, endY))

        } else {
            // Simple case: no obstacles in the way, go directly
            path.add(Pair(endX, endY))
        }

        return path
    }
}
