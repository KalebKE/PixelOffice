package com.pixeloffice.world

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A* pathfinding algorithm on a tile-based collision grid.
 *
 * Finds the shortest path between two points while avoiding obstacles.
 * Supports diagonal movement with proper corner checking.
 */
class AStarPathfinder(private val collisionMap: CollisionMap) {

    companion object {
        // Movement costs
        const val CARDINAL_COST = 1.0f
        const val DIAGONAL_COST = 1.414f // sqrt(2)
    }

    /**
     * Node for A* algorithm.
     */
    private data class Node(
        val x: Int,
        val y: Int,
        val g: Float,  // Cost from start
        val h: Float,  // Heuristic to goal
        val parent: Node?
    ) : Comparable<Node> {
        val f: Float get() = g + h

        override fun compareTo(other: Node): Int {
            return f.compareTo(other.f)
        }
    }

    /**
     * Calculate the heuristic distance (octile distance for diagonal movement).
     */
    private fun heuristic(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        return CARDINAL_COST * (dx + dy) + (DIAGONAL_COST - 2 * CARDINAL_COST) * minOf(dx, dy)
    }

    /**
     * Find path from start tile to goal tile.
     *
     * @return List of tile coordinates forming the path, or empty list if no path found
     */
    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int): List<Pair<Int, Int>> {
        // Handle case where start or goal is blocked
        val actualStart = if (collisionMap.isWalkable(startX, startY)) {
            Pair(startX, startY)
        } else {
            collisionMap.findNearestWalkable(startX, startY) ?: return emptyList()
        }

        val actualGoal = if (collisionMap.isWalkable(goalX, goalY)) {
            Pair(goalX, goalY)
        } else {
            collisionMap.findNearestWalkable(goalX, goalY) ?: return emptyList()
        }

        // Same start and goal
        if (actualStart == actualGoal) {
            return listOf(actualGoal)
        }

        val openSet = PriorityQueue<Node>()
        val closedSet = mutableSetOf<Pair<Int, Int>>()
        val gScores = mutableMapOf<Pair<Int, Int>, Float>()

        val startNode = Node(
            x = actualStart.first,
            y = actualStart.second,
            g = 0f,
            h = heuristic(actualStart.first, actualStart.second, actualGoal.first, actualGoal.second),
            parent = null
        )

        openSet.add(startNode)
        gScores[actualStart] = 0f

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()
            val currentPos = Pair(current.x, current.y)

            // Found the goal
            if (current.x == actualGoal.first && current.y == actualGoal.second) {
                return reconstructPath(current)
            }

            if (currentPos in closedSet) continue
            closedSet.add(currentPos)

            // Explore neighbors
            for ((nx, ny) in collisionMap.getNeighbors(current.x, current.y)) {
                val neighborPos = Pair(nx, ny)
                if (neighborPos in closedSet) continue

                // Calculate movement cost
                val isDiagonal = (nx != current.x && ny != current.y)
                val moveCost = if (isDiagonal) DIAGONAL_COST else CARDINAL_COST
                val tentativeG = current.g + moveCost

                val currentBestG = gScores[neighborPos] ?: Float.MAX_VALUE
                if (tentativeG < currentBestG) {
                    gScores[neighborPos] = tentativeG

                    val neighborNode = Node(
                        x = nx,
                        y = ny,
                        g = tentativeG,
                        h = heuristic(nx, ny, actualGoal.first, actualGoal.second),
                        parent = current
                    )
                    openSet.add(neighborNode)
                }
            }
        }

        // No path found
        return emptyList()
    }

    /**
     * Reconstruct the path from goal to start by following parent pointers.
     */
    private fun reconstructPath(goalNode: Node): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var current: Node? = goalNode

        while (current != null) {
            path.add(Pair(current.x, current.y))
            current = current.parent
        }

        path.reverse()
        return path
    }

    /**
     * Calculate path in world coordinates.
     *
     * @param startX Starting X in world coordinates
     * @param startY Starting Y in world coordinates
     * @param endX Goal X in world coordinates
     * @param endY Goal Y in world coordinates
     * @return List of waypoints in world coordinates
     */
    fun calculateWorldPath(startX: Float, startY: Float, endX: Float, endY: Float): List<Pair<Float, Float>> {
        val (startTileX, startTileY) = collisionMap.worldToTile(startX, startY)
        val (endTileX, endTileY) = collisionMap.worldToTile(endX, endY)

        val tilePath = findPath(startTileX, startTileY, endTileX, endTileY)

        if (tilePath.isEmpty()) {
            // Fallback: direct path if no route found
            return listOf(Pair(endX, endY))
        }

        // Convert tile path to world coordinates
        val worldPath = tilePath.map { (tx, ty) ->
            collisionMap.tileToWorld(tx, ty)
        }.toMutableList()

        // Replace the last point with the exact destination
        if (worldPath.isNotEmpty()) {
            worldPath[worldPath.lastIndex] = Pair(endX, endY)
        }

        return worldPath
    }
}
