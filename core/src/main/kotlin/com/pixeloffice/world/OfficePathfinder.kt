package com.pixeloffice.world

/**
 * Calculates navigation paths around office obstacles.
 *
 * Characters need to navigate around desk walls and partitions
 * instead of walking through them. This pathfinder creates
 * waypoint-based paths using hardcoded office layout knowledge.
 *
 * Office layout:
 * - Left desk column: X=45-125 (desks at X=64, 85)
 * - Right desk column: X=175-255 (desks at X=194, 215)
 * - Center aisle: X=125-175 (safe to walk through)
 * - Desk rows at Y=136, 166, 196 (first row at Y=125 is the wall)
 */
class OfficePathfinder {

    companion object {
        // Exit corridors on left and right sides of desk clusters
        const val LEFT_COLUMN_EXIT_X = 30f      // Exit left of left column
        const val LEFT_COLUMN_RIGHT_X = 130f    // Exit right of left column (center aisle)
        const val RIGHT_COLUMN_LEFT_X = 165f    // Exit left of right column (center aisle)
        const val RIGHT_COLUMN_EXIT_X = 260f    // Exit right of right column

        // Safe corridor above desk walls (walls start at Y=125)
        const val UPPER_CORRIDOR_Y = 110f

        // X boundaries for columns
        const val LEFT_COLUMN_MIN_X = 45f
        const val LEFT_COLUMN_MAX_X = 125f
        const val RIGHT_COLUMN_MIN_X = 175f
        const val RIGHT_COLUMN_MAX_X = 255f

        // Center aisle
        const val CENTER_AISLE_X = 150f
    }

    /**
     * Determine which column a position is in.
     * @return "left", "right", or "center"
     */
    private fun getColumn(x: Float): String {
        return when {
            x < LEFT_COLUMN_MAX_X -> "left"
            x > RIGHT_COLUMN_MIN_X -> "right"
            else -> "center"
        }
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

        // Determine which column we're in
        val startColumn = getColumn(startX)
        val endColumn = getColumn(endX)

        // If both in center aisle or upper corridor, go direct
        if (!startInDeskArea && !endInDeskArea) {
            path.add(Pair(endX, endY))
            return path
        }

        // If in center aisle, can move freely vertically
        if (startColumn == "center" && endColumn == "center") {
            path.add(Pair(endX, endY))
            return path
        }

        // Moving from desk area to upper area
        if (startInDeskArea && !endInDeskArea) {
            val exitX = getExitX(startX, startColumn)
            path.add(Pair(exitX, startY))
            path.add(Pair(exitX, UPPER_CORRIDOR_Y))
            path.add(Pair(endX, endY))
            return path
        }

        // Moving from upper area to desk area
        if (!startInDeskArea && endInDeskArea) {
            val entryX = getExitX(endX, endColumn)
            path.add(Pair(entryX, UPPER_CORRIDOR_Y))
            path.add(Pair(entryX, endY))
            path.add(Pair(endX, endY))
            return path
        }

        // Both in desk area
        if (startInDeskArea && endInDeskArea) {
            // Same column - can move directly within the column if using aisle
            if (startColumn == endColumn) {
                val exitX = getExitX(startX, startColumn)
                // Move to aisle, then down/up, then to desk
                path.add(Pair(exitX, startY))
                path.add(Pair(exitX, endY))
                path.add(Pair(endX, endY))
            } else {
                // Different columns - need to go through corridor
                val startExitX = getExitX(startX, startColumn)
                val endEntryX = getExitX(endX, endColumn)

                path.add(Pair(startExitX, startY))
                path.add(Pair(startExitX, UPPER_CORRIDOR_Y))
                path.add(Pair(endEntryX, UPPER_CORRIDOR_Y))
                path.add(Pair(endEntryX, endY))
                path.add(Pair(endX, endY))
            }
            return path
        }

        // Fallback: direct path
        path.add(Pair(endX, endY))
        return path
    }

    /**
     * Get the exit X coordinate for a column.
     */
    private fun getExitX(x: Float, column: String): Float {
        return when (column) {
            "left" -> {
                // Use center aisle exit for left column (closer to center)
                LEFT_COLUMN_RIGHT_X
            }
            "right" -> {
                // Use center aisle exit for right column
                RIGHT_COLUMN_LEFT_X
            }
            else -> x  // Center aisle - stay at current X
        }
    }
}
