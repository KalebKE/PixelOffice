package com.pixeloffice.world

import com.pixeloffice.core.Config

/**
 * Tile types for pathfinding grid.
 */
enum class TileType {
    WALKABLE,
    BLOCKED,
    OUT_OF_BOUNDS
}

/**
 * Tile-based collision grid built from furniture config.
 *
 * The grid is sized based on the display dimensions and tile size.
 * Default: 320x240 pixels with 16x16 tiles = 20x15 tile grid.
 *
 * Coordinate system:
 * - World coordinates are Y-down (Y=0 at top)
 * - Tile (0,0) is at top-left of world
 */
class CollisionMap(private val config: Config) {

    private val tileSize = config.office.tileSize
    private val worldWidth = config.display.width
    private val worldHeight = config.display.height

    val gridWidth = worldWidth / tileSize   // 20 tiles
    val gridHeight = worldHeight / tileSize // 15 tiles

    private val grid: Array<Array<TileType>> = Array(gridHeight) { Array(gridWidth) { TileType.OUT_OF_BOUNDS } }

    init {
        buildGrid()
    }

    /**
     * Build the collision grid from config data.
     */
    private fun buildGrid() {
        // First, mark walkable zones
        for (zone in config.office.walkableZones) {
            markRectWalkable(zone.x, zone.y, zone.w, zone.h)
        }

        // Then, mark furniture as blocked
        for (furniture in config.office.furniture) {
            val collision = furniture.collision ?: continue

            // Collision rect is relative to furniture position
            val worldX = furniture.x + collision.x
            val worldY = furniture.y + collision.y

            markRectBlocked(worldX, worldY, collision.w, collision.h)
        }
    }

    /**
     * Mark a rectangular area as walkable.
     */
    private fun markRectWalkable(worldX: Int, worldY: Int, width: Int, height: Int) {
        val startTileX = worldX / tileSize
        val startTileY = worldY / tileSize
        val endTileX = (worldX + width - 1) / tileSize
        val endTileY = (worldY + height - 1) / tileSize

        for (ty in startTileY..endTileY) {
            for (tx in startTileX..endTileX) {
                if (tx in 0 until gridWidth && ty in 0 until gridHeight) {
                    grid[ty][tx] = TileType.WALKABLE
                }
            }
        }
    }

    /**
     * Mark a rectangular area as blocked.
     */
    private fun markRectBlocked(worldX: Int, worldY: Int, width: Int, height: Int) {
        val startTileX = worldX / tileSize
        val startTileY = worldY / tileSize
        val endTileX = (worldX + width - 1) / tileSize
        val endTileY = (worldY + height - 1) / tileSize

        for (ty in startTileY..endTileY) {
            for (tx in startTileX..endTileX) {
                if (tx in 0 until gridWidth && ty in 0 until gridHeight) {
                    grid[ty][tx] = TileType.BLOCKED
                }
            }
        }
    }

    /**
     * Check if a tile is walkable.
     */
    fun isWalkable(tileX: Int, tileY: Int): Boolean {
        if (tileX < 0 || tileX >= gridWidth || tileY < 0 || tileY >= gridHeight) {
            return false
        }
        return grid[tileY][tileX] == TileType.WALKABLE
    }

    /**
     * Get the tile type at a position.
     */
    fun getTileType(tileX: Int, tileY: Int): TileType {
        if (tileX < 0 || tileX >= gridWidth || tileY < 0 || tileY >= gridHeight) {
            return TileType.OUT_OF_BOUNDS
        }
        return grid[tileY][tileX]
    }

    /**
     * Convert world coordinates to tile coordinates.
     */
    fun worldToTile(worldX: Float, worldY: Float): Pair<Int, Int> {
        val tileX = (worldX / tileSize).toInt()
        val tileY = (worldY / tileSize).toInt()
        return Pair(tileX, tileY)
    }

    /**
     * Convert tile coordinates to world coordinates (center of tile).
     */
    fun tileToWorld(tileX: Int, tileY: Int): Pair<Float, Float> {
        val worldX = tileX * tileSize + tileSize / 2f
        val worldY = tileY * tileSize + tileSize / 2f
        return Pair(worldX, worldY)
    }

    /**
     * Get walkable neighbors of a tile (for A* pathfinding).
     * Includes diagonal neighbors with corner checking.
     */
    fun getNeighbors(tileX: Int, tileY: Int): List<Pair<Int, Int>> {
        val neighbors = mutableListOf<Pair<Int, Int>>()

        // Cardinal directions
        val cardinalDirs = listOf(
            Pair(0, -1),  // up
            Pair(0, 1),   // down
            Pair(-1, 0),  // left
            Pair(1, 0)    // right
        )

        // Diagonal directions with their required cardinal neighbors
        val diagonalDirs = listOf(
            Triple(-1, -1, Pair(-1, 0) to Pair(0, -1)),  // up-left
            Triple(1, -1, Pair(1, 0) to Pair(0, -1)),    // up-right
            Triple(-1, 1, Pair(-1, 0) to Pair(0, 1)),    // down-left
            Triple(1, 1, Pair(1, 0) to Pair(0, 1))       // down-right
        )

        // Add cardinal neighbors
        for ((dx, dy) in cardinalDirs) {
            val nx = tileX + dx
            val ny = tileY + dy
            if (isWalkable(nx, ny)) {
                neighbors.add(Pair(nx, ny))
            }
        }

        // Add diagonal neighbors (only if both adjacent cardinal tiles are walkable)
        for ((dx, dy, cardinals) in diagonalDirs) {
            val nx = tileX + dx
            val ny = tileY + dy

            if (!isWalkable(nx, ny)) continue

            // Check both adjacent cardinal tiles to prevent corner cutting
            val (card1, card2) = cardinals
            val adjacent1Walkable = isWalkable(tileX + card1.first, tileY + card1.second)
            val adjacent2Walkable = isWalkable(tileX + card2.first, tileY + card2.second)

            if (adjacent1Walkable && adjacent2Walkable) {
                neighbors.add(Pair(nx, ny))
            }
        }

        return neighbors
    }

    /**
     * Find the nearest walkable tile to the given tile.
     * Uses BFS to find the closest one.
     */
    fun findNearestWalkable(tileX: Int, tileY: Int): Pair<Int, Int>? {
        if (isWalkable(tileX, tileY)) {
            return Pair(tileX, tileY)
        }

        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(tileX, tileY))
        visited.add(Pair(tileX, tileY))

        val directions = listOf(
            Pair(0, -1), Pair(0, 1), Pair(-1, 0), Pair(1, 0),
            Pair(-1, -1), Pair(1, -1), Pair(-1, 1), Pair(1, 1)
        )

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()

            for ((dx, dy) in directions) {
                val nx = cx + dx
                val ny = cy + dy
                val neighbor = Pair(nx, ny)

                if (neighbor in visited) continue
                if (nx < 0 || nx >= gridWidth || ny < 0 || ny >= gridHeight) continue

                visited.add(neighbor)

                if (isWalkable(nx, ny)) {
                    return neighbor
                }

                queue.add(neighbor)
            }
        }

        return null
    }

    /**
     * Debug: Print the grid to console.
     */
    fun debugPrint() {
        println("Collision Map (${gridWidth}x${gridHeight} tiles):")
        for (y in 0 until gridHeight) {
            val row = StringBuilder()
            for (x in 0 until gridWidth) {
                row.append(
                    when (grid[y][x]) {
                        TileType.WALKABLE -> "."
                        TileType.BLOCKED -> "#"
                        TileType.OUT_OF_BOUNDS -> "X"
                    }
                )
            }
            println(row)
        }
    }
}
