package com.pixeloffice.world

import com.pixeloffice.core.Config
import kotlin.math.sqrt

/**
 * A navigation point in the line network.
 */
data class NavPoint(val x: Float, val y: Float, val id: String)

/**
 * A line segment between two navigation points.
 */
data class NavLine(val from: NavPoint, val to: NavPoint)

/**
 * A desk position for navigation graph construction.
 */
data class DeskNavPosition(val id: String, val x: Float, val y: Float)

/**
 * Line-based navigation network for character pathfinding.
 *
 * Characters can only travel along predefined orthogonal lines and
 * transfer between lines at intersection points.
 *
 * The network is built from:
 * - Aisle centers calculated from walkable_zones (config)
 * - Desk connection points (set programmatically or from config)
 * - Whiteboard connection points from whiteboard_positions (config)
 */
class LineNetwork(private val config: Config) {

    private val points = mutableMapOf<String, NavPoint>()
    private val lines = mutableListOf<NavLine>()
    private val adjacency = mutableMapOf<String, MutableList<String>>()

    // Desk positions for navigation (can be overridden programmatically)
    private var deskPositions: List<DeskNavPosition> =
        config.office.deskPositions.map { DeskNavPosition(it.id, it.x.toFloat(), it.y.toFloat()) }

    // Calculated aisle centers
    private var leftAisleX = 22f
    private var centerAisleX = 150f
    private var rightAisleX = 287f
    private var corridorY = 114f

    init {
        buildNetwork()
    }

    /**
     * Set desk positions programmatically and rebuild the network.
     * Call this after desk columns are set up to use DeskColumn positions
     * instead of config.json desk_positions.
     */
    fun setDeskPositions(positions: List<DeskNavPosition>) {
        deskPositions = positions
        rebuild()
    }

    /**
     * Rebuild the navigation network (e.g., after desk positions change).
     */
    fun rebuild() {
        points.clear()
        lines.clear()
        adjacency.clear()
        buildNetwork()
    }

    /**
     * Build the navigation network.
     */
    private fun buildNetwork() {
        // 1. Calculate aisle centers from walkable_zones
        calculateAisleCenters()

        // 2. Create all intersection points
        createAllPoints()

        // 3. Create all connecting lines
        createAllLines()

        // 4. Build adjacency graph
        buildAdjacencyGraph()
    }

    /**
     * Calculate center positions of aisles from walkable zones.
     */
    private fun calculateAisleCenters() {
        for (zone in config.office.walkableZones) {
            when (zone.id) {
                "left_aisle" -> {
                    leftAisleX = zone.x + zone.w / 2f
                }
                "center_aisle" -> {
                    centerAisleX = zone.x + zone.w / 2f
                }
                "right_aisle" -> {
                    rightAisleX = zone.x + zone.w / 2f
                }
                "top_corridor" -> {
                    corridorY = zone.y + zone.h / 2f
                }
            }
        }
    }

    /**
     * Create all navigation points in the network.
     */
    private fun createAllPoints() {
        // Get unique desk row Y values
        val deskRowYs = deskPositions.map { it.y.toInt() }.distinct().sorted()

        // Corridor intersection points (where aisles meet corridor)
        addPoint(NavPoint(leftAisleX, corridorY, "corridor_left"))
        addPoint(NavPoint(centerAisleX, corridorY, "corridor_center"))
        addPoint(NavPoint(rightAisleX, corridorY, "corridor_right"))

        // Whiteboard points and their corridor intersections
        for (wb in config.office.whiteboardPositions) {
            addPoint(NavPoint(wb.x.toFloat(), wb.y.toFloat(), wb.id))
            addPoint(NavPoint(wb.x.toFloat(), corridorY, "wb_corridor_${wb.id}"))
        }

        // Desk row intersection points on each aisle
        for (rowY in deskRowYs) {
            val rowYFloat = rowY.toFloat()

            // Left aisle intersection
            addPoint(NavPoint(leftAisleX, rowYFloat, "left_aisle_y$rowY"))

            // Center aisle intersection
            addPoint(NavPoint(centerAisleX, rowYFloat, "center_aisle_y$rowY"))

            // Right aisle intersection (only for first two rows based on config)
            if (rowY <= 166) {
                addPoint(NavPoint(rightAisleX, rowYFloat, "right_aisle_y$rowY"))
            }
        }

        // Desk points - position based on which aisle they connect to
        val deskWidth = 17  // Actual desk sprite width (desk_left/desk_right are 17px)
        for (rowY in deskRowYs) {
            val rowDesks = deskPositions.filter { it.y.toInt() == rowY }
            val leftDesks = rowDesks.filter { it.x < centerAisleX }.sortedBy { it.x }
            val rightDesks = rowDesks.filter { it.x >= centerAisleX }.sortedByDescending { it.x }

            // Left column: all desks offset by deskWidth to reach chair position
            for (desk in leftDesks) {
                val deskX = desk.x + deskWidth
                addPoint(NavPoint(deskX, desk.y, desk.id))
            }

            // Right column: rightmost connects right (top-right), others connect left (top-left)
            for ((index, desk) in rightDesks.withIndex()) {
                val connectsRight = index == 0 && rowY <= 166  // right aisle only exists for y <= 166
                val deskX = if (connectsRight) desk.x + deskWidth else desk.x
                addPoint(NavPoint(deskX, desk.y, desk.id))
            }
        }

        // Bottom points for extended aisles
        val bottomY = 240f  // Bottom of screen
        addPoint(NavPoint(leftAisleX, bottomY, "left_aisle_bottom"))
        addPoint(NavPoint(centerAisleX, bottomY, "center_aisle_bottom"))
        addPoint(NavPoint(rightAisleX, bottomY, "right_aisle_bottom"))

        // Bottom corridor intersection points (y=210, between green couch and large table)
        val bottomCorridorY = 210f
        addPoint(NavPoint(centerAisleX, bottomCorridorY, "bottom_corridor_center"))
        addPoint(NavPoint(rightAisleX, bottomCorridorY, "bottom_corridor_right"))
    }

    /**
     * Create all line segments connecting points.
     */
    private fun createAllLines() {
        // Get unique desk row Y values
        val deskRowYs = deskPositions.map { it.y.toInt() }.distinct().sorted()

        // --- Horizontal corridor line ---
        // First, collect all points on the corridor (y = corridorY) sorted by X
        val corridorPoints = points.values
            .filter { it.y == corridorY }
            .sortedBy { it.x }

        // Connect consecutive corridor points
        for (i in 0 until corridorPoints.size - 1) {
            addLine(corridorPoints[i].id, corridorPoints[i + 1].id)
        }

        // --- Vertical whiteboard lines (whiteboard to corridor) ---
        for (wb in config.office.whiteboardPositions) {
            addLine(wb.id, "wb_corridor_${wb.id}")
        }

        // --- Vertical aisle lines ---
        // Left aisle: connect corridor to each row
        connectVerticalPoints("corridor_left", deskRowYs.map { "left_aisle_y$it" })

        // Center aisle: connect corridor to each row
        connectVerticalPoints("corridor_center", deskRowYs.map { "center_aisle_y$it" })

        // Right aisle: connect corridor to first two rows only
        val rightAisleRows = deskRowYs.filter { it <= 166 }.map { "right_aisle_y$it" }
        connectVerticalPoints("corridor_right", rightAisleRows)

        // --- Horizontal desk row lines ---
        for (rowY in deskRowYs) {
            // Get desks in this row
            val rowDesks = deskPositions.filter { it.y.toInt() == rowY }
            val leftDesks = rowDesks.filter { it.x < centerAisleX }.sortedBy { it.x }
            val rightDesks = rowDesks.filter { it.x >= centerAisleX }.sortedBy { it.x }

            // Left side: leftmost desk → left aisle, others → center aisle
            for ((index, desk) in leftDesks.withIndex()) {
                if (index == 0) {
                    addLine("left_aisle_y$rowY", desk.id)
                } else {
                    addLine("center_aisle_y$rowY", desk.id)
                }
            }

            // Right side: rightmost desk → right aisle (if exists), others → center aisle
            for ((index, desk) in rightDesks.sortedByDescending { it.x }.withIndex()) {
                if (index == 0 && points.containsKey("right_aisle_y$rowY")) {
                    addLine("right_aisle_y$rowY", desk.id)
                } else {
                    addLine("center_aisle_y$rowY", desk.id)
                }
            }
        }

        // --- Extend aisles to bottom ---
        val lastLeftRow = deskRowYs.maxOrNull()
        val lastCenterRow = deskRowYs.maxOrNull()
        val lastRightRow = deskRowYs.filter { it <= 166 }.maxOrNull()

        // Left aisle: last desk row → bottom
        if (lastLeftRow != null) {
            addLine("left_aisle_y$lastLeftRow", "left_aisle_bottom")
        }

        // Center aisle: last desk row → bottom corridor → bottom
        if (lastCenterRow != null) {
            addLine("center_aisle_y$lastCenterRow", "bottom_corridor_center")
            addLine("bottom_corridor_center", "center_aisle_bottom")
        }

        // Right aisle: last right row → bottom corridor → bottom
        if (lastRightRow != null) {
            addLine("right_aisle_y$lastRightRow", "bottom_corridor_right")
            addLine("bottom_corridor_right", "right_aisle_bottom")
        }

        // Horizontal line at bottom corridor (y=210) connecting center and right aisles
        addLine("bottom_corridor_center", "bottom_corridor_right")
    }

    /**
     * Connect a list of vertical points (from start down through rows).
     */
    private fun connectVerticalPoints(startId: String, rowIds: List<String>) {
        if (rowIds.isEmpty()) return

        // Connect start to first row
        if (points.containsKey(startId) && points.containsKey(rowIds.first())) {
            addLine(startId, rowIds.first())
        }

        // Connect consecutive rows
        for (i in 0 until rowIds.size - 1) {
            if (points.containsKey(rowIds[i]) && points.containsKey(rowIds[i + 1])) {
                addLine(rowIds[i], rowIds[i + 1])
            }
        }
    }

    /**
     * Build adjacency graph from lines.
     */
    private fun buildAdjacencyGraph() {
        adjacency.clear()

        // Initialize adjacency lists for all points
        for (point in points.values) {
            adjacency[point.id] = mutableListOf()
        }

        // Add edges from lines (bidirectional)
        for (line in lines) {
            adjacency[line.from.id]?.add(line.to.id)
            adjacency[line.to.id]?.add(line.from.id)
        }
    }

    /**
     * Add a point to the network.
     */
    private fun addPoint(point: NavPoint) {
        if (!points.containsKey(point.id)) {
            points[point.id] = point
        }
    }

    /**
     * Add a line between two points.
     */
    private fun addLine(fromId: String, toId: String) {
        val from = points[fromId] ?: return
        val to = points[toId] ?: return

        // Check if line already exists
        val exists = lines.any {
            (it.from.id == fromId && it.to.id == toId) ||
            (it.from.id == toId && it.to.id == fromId)
        }

        if (!exists) {
            lines.add(NavLine(from, to))
        }
    }

    /**
     * Find the nearest navigation point to a world position.
     */
    fun findNearestPoint(x: Float, y: Float): NavPoint? {
        var nearest: NavPoint? = null
        var minDist = Float.MAX_VALUE

        for (point in points.values) {
            val dx = point.x - x
            val dy = point.y - y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < minDist) {
                minDist = dist
                nearest = point
            }
        }

        return nearest
    }

    /**
     * Find a path between two points using BFS.
     */
    fun getPath(fromId: String, toId: String): List<NavPoint> {
        if (fromId == toId) {
            return points[fromId]?.let { listOf(it) } ?: emptyList()
        }

        val visited = mutableSetOf<String>()
        val parent = mutableMapOf<String, String>()
        val queue = ArrayDeque<String>()

        queue.add(fromId)
        visited.add(fromId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current == toId) {
                // Reconstruct path
                val path = mutableListOf<NavPoint>()
                var node = current
                while (node != fromId) {
                    points[node]?.let { path.add(0, it) }
                    node = parent[node] ?: break
                }
                points[fromId]?.let { path.add(0, it) }
                return path
            }

            for (neighbor in adjacency[current] ?: emptyList()) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    parent[neighbor] = current
                    queue.add(neighbor)
                }
            }
        }

        return emptyList()
    }

    /**
     * Get all lines in the network (for debug rendering).
     */
    fun getAllLines(): List<NavLine> = lines.toList()

    /**
     * Get all points in the network.
     */
    fun getAllPoints(): Map<String, NavPoint> = points.toMap()

    /**
     * Get a point by ID.
     */
    fun getPoint(id: String): NavPoint? = points[id]

    /**
     * Get or create a named navigation point.
     * If a point with the given name already exists, returns it.
     * Otherwise, finds the nearest existing point and returns it
     * (the name is used as a logical alias, the physical point is the nearest one).
     */
    fun getOrCreateNamedPoint(name: String, x: Float, y: Float): NavPoint? {
        // Check if point already exists by name
        points[name]?.let { return it }

        // Find nearest existing point to snap to
        return findNearestPoint(x, y)
    }
}
