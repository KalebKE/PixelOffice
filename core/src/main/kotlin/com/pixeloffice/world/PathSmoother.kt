package com.pixeloffice.world

import kotlin.math.abs

/**
 * Smooths paths by removing unnecessary waypoints using line-of-sight checks.
 *
 * Uses Bresenham's line algorithm to determine if two points can see each other
 * without crossing blocked tiles. This creates more natural, direct paths
 * instead of grid-aligned zigzag movements.
 */
class PathSmoother(private val collisionMap: CollisionMap) {

    /**
     * Smooth a path in world coordinates.
     *
     * @param path Original path as list of world coordinate pairs
     * @return Smoothed path with unnecessary waypoints removed
     */
    fun smoothPath(path: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (path.size <= 2) return path

        val smoothed = mutableListOf<Pair<Float, Float>>()
        smoothed.add(path.first())

        var currentIndex = 0

        while (currentIndex < path.size - 1) {
            // Try to find the furthest visible point
            var furthestVisible = currentIndex + 1

            for (testIndex in path.size - 1 downTo currentIndex + 2) {
                val (fromX, fromY) = path[currentIndex]
                val (toX, toY) = path[testIndex]

                if (hasLineOfSight(fromX, fromY, toX, toY)) {
                    furthestVisible = testIndex
                    break
                }
            }

            smoothed.add(path[furthestVisible])
            currentIndex = furthestVisible
        }

        return smoothed
    }

    /**
     * Check if there's a clear line of sight between two world positions.
     * Uses Bresenham's line algorithm on the tile grid.
     */
    fun hasLineOfSight(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val (tileX1, tileY1) = collisionMap.worldToTile(x1, y1)
        val (tileX2, tileY2) = collisionMap.worldToTile(x2, y2)

        return hasLineOfSightTiles(tileX1, tileY1, tileX2, tileY2)
    }

    /**
     * Check line of sight between two tile positions using Bresenham's algorithm.
     */
    private fun hasLineOfSightTiles(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        var x = x1
        var y = y1

        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)

        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1

        var err = dx - dy

        while (true) {
            // Check if current tile is walkable
            if (!collisionMap.isWalkable(x, y)) {
                return false
            }

            // Reached the end
            if (x == x2 && y == y2) {
                return true
            }

            val e2 = 2 * err

            // Handle diagonal movement - check both adjacent tiles
            if (e2 > -dy && e2 < dx) {
                // Moving diagonally - check corner tiles to prevent cutting through walls
                if (!collisionMap.isWalkable(x + sx, y) || !collisionMap.isWalkable(x, y + sy)) {
                    return false
                }
            }

            if (e2 > -dy) {
                err -= dy
                x += sx
            }

            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    /**
     * Smooth a tile path and convert to world coordinates.
     *
     * @param tilePath Path as list of tile coordinate pairs
     * @param endX Exact end X in world coordinates (to preserve precision)
     * @param endY Exact end Y in world coordinates (to preserve precision)
     * @return Smoothed path in world coordinates
     */
    fun smoothTilePath(
        tilePath: List<Pair<Int, Int>>,
        endX: Float,
        endY: Float
    ): List<Pair<Float, Float>> {
        if (tilePath.isEmpty()) return emptyList()
        if (tilePath.size == 1) return listOf(Pair(endX, endY))

        // Convert to world coordinates first
        val worldPath = tilePath.map { (tx, ty) ->
            collisionMap.tileToWorld(tx, ty)
        }.toMutableList()

        // Replace last point with exact destination
        worldPath[worldPath.lastIndex] = Pair(endX, endY)

        // Now smooth the world path
        return smoothPath(worldPath)
    }
}
