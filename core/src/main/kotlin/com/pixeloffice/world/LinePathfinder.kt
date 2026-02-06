package com.pixeloffice.world

import com.badlogic.gdx.Gdx

/**
 * Line-based pathfinder that restricts movement to predefined line segments.
 *
 * Characters can only travel along lines in the LineNetwork and must
 * transfer between lines at intersection points.
 */
class LinePathfinder(private val lineNetwork: LineNetwork) : Pathfinder {

    /**
     * Calculate a path from start to end position.
     *
     * @param startX Starting X position in world coordinates
     * @param startY Starting Y position in world coordinates
     * @param endX Destination X position in world coordinates
     * @param endY Destination Y position in world coordinates
     * @return List of waypoints (x, y pairs) to follow
     */
    override fun calculatePath(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<Pair<Float, Float>> {
        // 1. Find nearest navigation point to start
        val startPoint = lineNetwork.findNearestPoint(startX, startY)
        if (startPoint == null) {
            Gdx.app.log("LinePathfinder", "No start point found near ($startX, $startY)")
            return listOf(Pair(endX, endY))
        }

        // 2. Find nearest navigation point to end
        val endPoint = lineNetwork.findNearestPoint(endX, endY)
        if (endPoint == null) {
            Gdx.app.log("LinePathfinder", "No end point found near ($endX, $endY)")
            return listOf(Pair(endX, endY))
        }

        Gdx.app.log("LinePathfinder", "Pathfinding from ${startPoint.id} to ${endPoint.id}")

        // 3. Get path through the network using BFS
        val navPath = lineNetwork.getPath(startPoint.id, endPoint.id)

        if (navPath.isEmpty()) {
            Gdx.app.log("LinePathfinder", "No path found, using direct path")
            return listOf(Pair(endX, endY))
        }

        // 4. Convert to coordinate pairs
        val path = navPath.map { Pair(it.x, it.y) }

        // 5. Skip the first point if very close to start position
        val result = if (path.size > 1) {
            val (firstX, firstY) = path.first()
            val distSq = (firstX - startX) * (firstX - startX) + (firstY - startY) * (firstY - startY)
            if (distSq < 64f) { // Within 8 pixels
                path.drop(1)
            } else {
                path
            }
        } else {
            path
        }

        Gdx.app.log("LinePathfinder", "Path: $result")
        return result
    }

    override fun getDeskMidpoint(deskId: String): Pair<Float, Float>? {
        val point = lineNetwork.getDeskMidpoint(deskId)
        return point?.let { Pair(it.x, it.y) }
    }

    /**
     * Get the line network (for debug rendering).
     */
    fun getLineNetwork(): LineNetwork = lineNetwork
}
