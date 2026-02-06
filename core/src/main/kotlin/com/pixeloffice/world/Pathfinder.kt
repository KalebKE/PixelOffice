package com.pixeloffice.world

/**
 * Interface for pathfinding implementations.
 * Enables swapping between different pathfinding strategies.
 */
interface Pathfinder {
    /**
     * Calculate a path from start to end position.
     *
     * @param startX Starting X position in world coordinates
     * @param startY Starting Y position in world coordinates
     * @param endX Destination X position in world coordinates
     * @param endY Destination Y position in world coordinates
     * @return List of waypoints (x, y pairs) to follow, including final destination
     */
    fun calculatePath(startX: Float, startY: Float, endX: Float, endY: Float): List<Pair<Float, Float>>

    /**
     * Get the midpoint position for a desk (between aisle and desk).
     * Used by PM/PO to stop at the midpoint when visiting desks they don't own.
     */
    fun getDeskMidpoint(deskId: String): Pair<Float, Float>? = null
}
