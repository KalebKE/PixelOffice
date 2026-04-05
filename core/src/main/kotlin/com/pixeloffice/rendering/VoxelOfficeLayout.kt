package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3

/**
 * Builds the 3D voxel office layout matching the 2D pixel office structure.
 *
 * The pixel office has two desk COLUMNS (left and right), each containing
 * desk rows. Each row has west and east desks facing inward toward a shared
 * partition wall -- like cubicle bays. Three aisles separate the columns
 * (left aisle, center aisle, right aisle).
 *
 * 2D pixel coordinates → 3D world:
 *   2D: 320 wide × 240 tall, Y-down
 *   3D: X = left-to-right, Z = front(camera) to back(wall), Y = up
 *
 * We use proportional mapping: 3D_x = (pixel_x / 320) * OFFICE_WIDTH
 *                               3D_z = -(pixel_y / 240) * OFFICE_DEPTH
 */
class VoxelOfficeLayout(private val catalog: VoxelAssetCatalog) {

    companion object {
        const val OFFICE_WIDTH = 26f
        const val OFFICE_DEPTH = 22f

        private const val DESK_HEIGHT = 1.1f  // desk surface Y for placing items

        // Proportional X mapping from 2D pixel coords to 3D
        // 2D left column baseX=45 → 3D x = (45/320)*26 = 3.66
        // 2D right column baseX=175 → 3D x = (175/320)*26 = 14.22
        // Left column desk positions: west=baseX+19=64, east=baseX+40=85
        // Right column desk positions: west=baseX+19=194, east=baseX+40=215

        // Column 1 (left)
        private const val COL1_WEST_X = 5.2f   // (64/320)*26
        private const val COL1_EAST_X = 6.9f   // (85/320)*26
        private const val COL1_PARTITION_X = 6.05f  // midpoint

        // Column 2 (right)
        private const val COL2_WEST_X = 15.8f  // (194/320)*26
        private const val COL2_EAST_X = 17.5f  // (215/320)*26
        private const val COL2_PARTITION_X = 16.65f

        // Z layout: back wall at Z=-22, front (camera) at Z=0
        // Upper corridor (2D Y=65-120) → near back wall: Z=-18 to -20
        // Desk rows (2D Y=125-215) → middle of office: Z=-8 to -16
        // Lower area (2D Y=215-240) → near front: Z=-2 to -5
        //
        // Desk rows spread evenly across the middle zone:
        private const val ROW1_Z = -9.0f    // first desk row (closest to front)
        private const val ROW2_Z = -12.0f   // second desk row
        private const val ROW3_Z = -15.0f   // third desk row (closest to corridor)

        // Upper corridor: between desk rows and back wall
        private const val CORRIDOR_Z = -18.5f   // corridor furniture
        private const val BACK_WALL_Z = -20.5f   // items mounted on/near back wall (whiteboards, door)

        // Chair offset: chairs sit in front of desk (toward camera = +Z)
        private const val CHAIR_FORWARD = 1.5f
    }

    data class Placement(
        val assetPath: String,
        val x: Float, val y: Float, val z: Float,
        val rotY: Float = 0f
    )

    fun map2DTo3D(pixelX: Float, pixelY: Float): Vector3 {
        val x = (pixelX / 320f) * OFFICE_WIDTH
        val z = -(pixelY / 240f) * OFFICE_DEPTH
        return Vector3(x, 0f, z)
    }

    fun getDeskPositions3D(): Map<String, Vector3> = mapOf(
        // Left Column (odd=east, even=west per DeskColumn convention)
        "deskColumn1_desk2" to Vector3(COL1_WEST_X, 0f, ROW1_Z + CHAIR_FORWARD),   // R1 west
        "deskColumn1_desk1" to Vector3(COL1_EAST_X, 0f, ROW1_Z + CHAIR_FORWARD),   // R1 east
        "deskColumn1_desk4" to Vector3(COL1_WEST_X, 0f, ROW2_Z + CHAIR_FORWARD),   // R2 west
        "deskColumn1_desk3" to Vector3(COL1_EAST_X, 0f, ROW2_Z + CHAIR_FORWARD),   // R2 east
        "deskColumn1_desk6" to Vector3(COL1_WEST_X, 0f, ROW3_Z + CHAIR_FORWARD),   // R3 west
        "deskColumn1_desk5" to Vector3(COL1_EAST_X, 0f, ROW3_Z + CHAIR_FORWARD),   // R3 east (disabled in defaults but position exists)
        // Right Column
        "deskColumn2_desk2" to Vector3(COL2_WEST_X, 0f, ROW1_Z + CHAIR_FORWARD),   // R1 west
        "deskColumn2_desk1" to Vector3(COL2_EAST_X, 0f, ROW1_Z + CHAIR_FORWARD),   // R1 east (PM)
        "deskColumn2_desk4" to Vector3(COL2_WEST_X, 0f, ROW2_Z + CHAIR_FORWARD),   // R2 west
        "deskColumn2_desk3" to Vector3(COL2_EAST_X, 0f, ROW2_Z + CHAIR_FORWARD),   // R2 east (PO)
    )

    private var lastPlacements: List<Placement> = emptyList()

    /** Returns all placements from the last buildStaticLayout call */
    fun allPlacements(): List<Placement> = lastPlacements

    /** Returns (cubicle placements, non-cubicle placements) from the last buildStaticLayout call */
    fun splitByCubicle(): Pair<List<Placement>, List<Placement>> {
        val cubicles = lastPlacements.filter { it.assetPath.startsWith("Cubicles/") }
        val others = lastPlacements.filter { !it.assetPath.startsWith("Cubicles/") }
        return Pair(cubicles, others)
    }

    fun buildStaticLayout(): List<ModelInstance> {
        val placements = mutableListOf<Placement>()

        addDeskColumn1(placements)
        addDeskColumn2(placements)
        addUpperCorridor(placements)
        addLowerArea(placements)
        addRightEdge(placements)

        lastPlacements = placements.toList()

        // Log all placements for debugging
        Gdx.app?.log("VoxelLayout", "=== ${placements.size} placements ===")
        for (p in placements) {
            val shortName = p.assetPath.substringAfterLast("/")
            Gdx.app?.log("VoxelLayout", "$shortName @ (%.1f, %.1f, %.1f) rotY=%.0f".format(p.x, p.y, p.z, p.rotY))
        }

        return placements.mapNotNull { p ->
            catalog.createInstance(p.assetPath)?.also { instance ->
                instance.transform.setToTranslation(p.x, p.y, p.z)
                if (p.rotY != 0f) {
                    instance.transform.rotate(Vector3.Y, p.rotY)
                }
            }
        }
    }

    // ---- Desk bay helper ----
    // A desk bay = partition wall + desk + chair + PC
    // West desks face RIGHT (toward partition), east desks face LEFT

    private fun addWestDesk(
        out: MutableList<Placement>, x: Float, z: Float,
        chairModel: String, pcModel: String
    ) {
        // Cubicle partition behind desk (to the left/west)
        out.add(Placement("Cubicles/Office_Cubicle_White_04", x - 1.0f, 0f, z))
        // Desk
        out.add(Placement("Tables/Office_Table_White_1x1_01", x, 0f, z))
        // Chair (in front of desk, facing desk = rotated 180)
        out.add(Placement("Chairs/$chairModel", x, 0f, z + CHAIR_FORWARD, 180f))
        // PC on desk
        out.add(Placement("Misc/Electronics/$pcModel", x + 0.3f, DESK_HEIGHT, z - 0.1f))
    }

    private fun addEastDesk(
        out: MutableList<Placement>, x: Float, z: Float,
        chairModel: String, pcModel: String
    ) {
        // Cubicle partition behind desk (to the right/east)
        out.add(Placement("Cubicles/Office_Cubicle_White_03", x + 1.0f, 0f, z, 180f))
        // Desk
        out.add(Placement("Tables/Office_Table_White_1x1_02", x, 0f, z))
        // Chair (in front of desk, facing desk = no rotation)
        out.add(Placement("Chairs/$chairModel", x, 0f, z + CHAIR_FORWARD))
        // PC on desk
        out.add(Placement("Misc/Electronics/$pcModel", x - 0.3f, DESK_HEIGHT, z - 0.1f))
    }

    // ---- Zone builders ----

    private fun addDeskColumn1(out: MutableList<Placement>) {
        // LEFT DESK COLUMN (2D baseX=45)
        // Row 1 (wallY=125)
        addWestDesk(out, COL1_WEST_X, ROW1_Z, "Office_Chair_Black_01", "Office_Misc_PC_01")
        addEastDesk(out, COL1_EAST_X, ROW1_Z, "Office_Chair_White_01", "Office_Misc_PC_02")
        // Wall art
        out.add(Placement("Misc/Office_Misc_PictureFrame_01", COL1_WEST_X - 1.5f, 1.6f, ROW1_Z))

        // Row 2 (wallY=155)
        addWestDesk(out, COL1_WEST_X, ROW2_Z, "Office_Chair_Black_01", "Office_Misc_PC_02")
        addEastDesk(out, COL1_EAST_X, ROW2_Z, "Office_Chair_Black_02", "Office_Misc_PC_01")
        // Wall art
        out.add(Placement("Misc/Office_Misc_Wall_Corkboard_01", COL1_WEST_X - 1.5f, 1.4f, ROW2_Z))

        // Row 3 (wallY=185) - west desk + east desk (east is disabled in defaults but has cubicle)
        addWestDesk(out, COL1_WEST_X, ROW3_Z, "Office_Chair_Black_01", "Office_Misc_PC_01")
        // East side row 3: just partition wall, no desk (decorative, matches pixel office)
        out.add(Placement("Cubicles/Office_Cubicle_White_01", COL1_EAST_X + 1.0f, 0f, ROW3_Z, 180f))
        // Wall art
        out.add(Placement("Misc/Office_Misc_PictureFrame_02", COL1_WEST_X - 1.5f, 1.6f, ROW3_Z))

        // Desk items
        out.add(Placement("Misc/Office_Misc_Notebook", COL1_EAST_X - 0.3f, DESK_HEIGHT, ROW1_Z + 0.2f))
        out.add(Placement("Misc/Coffee/Office_Misc_Coffee_Mug", COL1_WEST_X - 0.3f, DESK_HEIGHT, ROW2_Z + 0.2f))
    }

    private fun addDeskColumn2(out: MutableList<Placement>) {
        // RIGHT DESK COLUMN (2D baseX=175)
        // Row 1 (wallY=125) — east = PM reserved
        addWestDesk(out, COL2_WEST_X, ROW1_Z, "Office_Chair_Black_01", "Office_Misc_PC_01")
        addEastDesk(out, COL2_EAST_X, ROW1_Z, "Office_Chair_White_01", "Office_Misc_PC_02")
        out.add(Placement("Misc/Office_Misc_PictureFrame_01", COL2_WEST_X - 1.5f, 1.6f, ROW1_Z))

        // Row 2 (wallY=155) — east = PO reserved
        addWestDesk(out, COL2_WEST_X, ROW2_Z, "Office_Chair_Black_01", "Office_Misc_PC_02")
        addEastDesk(out, COL2_EAST_X, ROW2_Z, "Office_Chair_Black_02", "Office_Misc_PC_01")
        out.add(Placement("Misc/Office_Misc_Wall_Corkboard_02", COL2_EAST_X + 2.0f, 1.4f, ROW2_Z))

        // Row 3 (wallY=185) — LOUNGE AREA (matches pixel office: green couch, red trash, tree)
        out.add(Placement("Chairs/Office_Couch_Black_01", COL2_WEST_X, 0f, ROW3_Z, 0f))
        out.add(Placement("Misc/Trashcans/Office_Misc_Traschcan_Recycle_Red", COL2_EAST_X + 0.5f, 0f, ROW3_Z + 0.5f))
        out.add(Placement("Misc/Office_Misc_Plant_03", COL2_EAST_X + 2.0f, 0f, ROW3_Z))
        // Wall decor for lounge
        out.add(Placement("Misc/Office_Misc_PictureFrame_02", COL2_WEST_X - 1.5f, 1.6f, ROW3_Z))

        // Desk items
        out.add(Placement("Misc/Electronics/Office_Misc_Tablet", COL2_EAST_X + 0.3f, DESK_HEIGHT, ROW1_Z + 0.2f))
        out.add(Placement("Misc/Office_Misc_Organizer", COL2_WEST_X - 0.3f, DESK_HEIGHT, ROW2_Z + 0.2f))
    }

    private fun addUpperCorridor(out: MutableList<Placement>) {
        // UPPER CORRIDOR (2D Y=78-120, the area above the desk rows)
        // This area has: vending machine, water cooler, coffee machine, whiteboards,
        // trees, orange couch, blue trash can, bookshelf, doors, clock, TVs

        // --- Left side ---
        // Vending machine (2D x=5, y=78) → far left against back wall
        out.add(Placement("Misc/Office_Misc_Cabinet_01", 0.8f, 0f, CORRIDOR_Z - 1.5f, 90f))

        // Water cooler area + coffee machine (2D x=31-53, y=93-95)
        out.add(Placement("Misc/Coffee/Office_Misc_Coffee_Machine_01", 2.5f, 0f, CORRIDOR_Z - 0.5f, 180f))
        out.add(Placement("Tables/Office_Table_Brown_1x1_01", 3.8f, 0f, CORRIDOR_Z - 0.5f))
        out.add(Placement("Misc/Coffee/Office_Misc_Coffee_Mug", 3.8f, DESK_HEIGHT, CORRIDOR_Z - 0.3f))

        // --- Whiteboards (2D x=73,180,205 y=83) ---
        out.add(Placement("Misc/Office_Misc_Whiteboard_01", 6.0f, 0f, BACK_WALL_Z))
        out.add(Placement("Misc/Office_Misc_Whiteboard_02", 14.6f, 0f, BACK_WALL_Z))
        out.add(Placement("Misc/Office_Misc_Whiteboard_01", 16.7f, 0f, BACK_WALL_Z))

        // --- Trees in corridor (2D x=95,162 y=90) ---
        out.add(Placement("Misc/Office_Misc_Plant_03", 7.7f, 0f, CORRIDOR_Z))
        out.add(Placement("Misc/Office_Misc_Plant_03", 13.2f, 0f, CORRIDOR_Z))

        // --- Doors + clock (2D x=120-136, y=75 + clock at 126,65) ---
        out.add(Placement("Misc/Office_Misc_Door_02", 10.5f, 0f, BACK_WALL_Z - 0.3f))
        out.add(Placement("Misc/Office_Misc_Wall_Clock_01", 10.5f, 2.5f, BACK_WALL_Z - 0.5f))

        // --- Right side ---
        // Orange couch (2D x=225, y=95)
        out.add(Placement("Chairs/Office_Couch_Brown_01", 18.3f, 0f, CORRIDOR_Z, 0f))

        // Blue trash can (2D x=260, y=95)
        out.add(Placement("Misc/Trashcans/Office_Misc_Traschcan_Recycle_Blue", 21.1f, 0f, CORRIDOR_Z))

        // Bookshelf (2D x=290, y=80)
        out.add(Placement("Misc/Office_Misc_Cabinet_02", 23.6f, 0f, BACK_WALL_Z, -90f))

        // Floor lamp / plant near bookshelf (2D x=283, y=82)
        out.add(Placement("Misc/Office_Misc_Plant_01", 23.0f, 0f, CORRIDOR_Z + 0.5f))
    }

    private fun addLowerArea(out: MutableList<Placement>) {
        // LOWER AREA (near front of office, Z ≈ -2 to -5)

        // Tree (2D x=76, y=218)
        out.add(Placement("Misc/Office_Misc_Plant_03", 6.2f, 0f, -3.0f))

        // Large table + printer (2D x=193-215, y=220-222)
        out.add(Placement("Tables/Office_Table_Brown_2x2_01", COL2_WEST_X, 0f, -3.5f))
        out.add(Placement("Misc/Electronics/Office_Misc_Printer", COL2_EAST_X, 0f, -3.5f))
        out.add(Placement("Misc/Office_Misc_Papers", COL2_EAST_X, DESK_HEIGHT, -3.7f))
    }

    private fun addRightEdge(out: MutableList<Placement>) {
        // RIGHT EDGE — trees along right wall (2D x=305, y=123,153,188)
        // These are the decorative plants along the right margin of the pixel office
        out.add(Placement("Misc/Office_Misc_Plant_02", 24.8f, 0f, ROW1_Z))
        out.add(Placement("Misc/Office_Misc_Plant_01", 24.8f, 0f, ROW2_Z))
        out.add(Placement("Misc/Office_Misc_Plant_02", 24.8f, 0f, ROW3_Z))

        // Vending machine on far right (2D x=305, y=78) — bookshelf-like
        out.add(Placement("Misc/Office_Misc_Cabinet_01", 25.2f, 0f, CORRIDOR_Z - 1.0f, -90f))
    }
}
