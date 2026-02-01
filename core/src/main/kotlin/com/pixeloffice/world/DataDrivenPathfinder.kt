package com.pixeloffice.world

/**
 * Data-driven pathfinder that uses A* algorithm with path smoothing.
 *
 * This pathfinder reads collision data from config to build a navigation grid,
 * then uses A* to find paths and line-of-sight smoothing to create natural movement.
 *
 * Architecture:
 * ```
 * config.json (furniture with collision bounds)
 *        ↓
 * CollisionMap (build tile grid)
 *        ↓
 * AStarPathfinder (A* algorithm on grid)
 *        ↓
 * PathSmoother (line-of-sight optimization)
 *        ↓
 * Smoothed path in world coordinates
 * ```
 */
class DataDrivenPathfinder(private val collisionMap: CollisionMap) : Pathfinder {

    private val astar = AStarPathfinder(collisionMap)
    private val smoother = PathSmoother(collisionMap)

    /**
     * Calculate a smoothed path from start to end.
     *
     * @param startX Starting X position in world coordinates
     * @param startY Starting Y position in world coordinates
     * @param endX Destination X position in world coordinates
     * @param endY Destination Y position in world coordinates
     * @return List of waypoints to follow
     */
    override fun calculatePath(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<Pair<Float, Float>> {
        // Get raw A* path in world coordinates
        val rawPath = astar.calculateWorldPath(startX, startY, endX, endY)

        if (rawPath.isEmpty()) {
            // Fallback to direct path
            return listOf(Pair(endX, endY))
        }

        // Smooth the path to remove unnecessary waypoints
        val smoothedPath = smoother.smoothPath(rawPath)

        // Skip the first point if it's very close to the start position
        // (since the character is already there)
        return if (smoothedPath.size > 1) {
            val (firstX, firstY) = smoothedPath.first()
            val distSq = (firstX - startX) * (firstX - startX) + (firstY - startY) * (firstY - startY)
            if (distSq < 64f) { // Within 8 pixels
                smoothedPath.drop(1)
            } else {
                smoothedPath
            }
        } else {
            smoothedPath
        }
    }

    /**
     * Check if a position is walkable.
     */
    fun isWalkable(worldX: Float, worldY: Float): Boolean {
        val (tileX, tileY) = collisionMap.worldToTile(worldX, worldY)
        return collisionMap.isWalkable(tileX, tileY)
    }

    /**
     * Get the collision map for debugging.
     */
    fun getCollisionMap(): CollisionMap = collisionMap

    /**
     * Debug: print the collision grid.
     */
    fun debugPrintGrid() {
        collisionMap.debugPrint()
    }
}
