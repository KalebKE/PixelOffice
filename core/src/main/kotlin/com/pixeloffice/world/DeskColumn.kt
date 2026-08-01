package com.pixeloffice.world

/**
 * Equipment type for a desk.
 */
enum class Equipment {
    COMPUTER,
    MONITOR,
    NONE
}

/**
 * Chair color variants matching available sprites.
 */
enum class ChairColor {
    BLACK,
    WHITE,
    BLUE,
    GREEN,
    YELLOW,
    ORANGE
}

/**
 * Wall decoration types.
 */
enum class WallDecor {
    ART,
    SMALL_ART_ORANGE,
    SMALL_ART_BLUE,
    SMALL_CALENDAR,
    NOTICE,
    POST_IT_NOTES,
    NONE
}

/**
 * Items that can sit on a desk surface.
 */
enum class DeskItem {
    RED_BOOK,
    BLUE_BOOK,
    GREEN_BOOK,
    NOTES,
    DOCUMENT,
    COFFEE_MUG,
    NONE
}

/**
 * Which side of the row a desk is on.
 */
enum class DeskSide {
    WEST,
    EAST
}

/**
 * Configuration for a single desk within a row.
 */
data class DeskConfig(
    val side: DeskSide,
    val equipment: Equipment = Equipment.NONE,
    val chairColor: ChairColor = ChairColor.BLACK,
    val wallDecor: WallDecor = WallDecor.NONE,
    val deskItems: List<DeskItem> = emptyList()
)

/**
 * A row of desks within a column. Contains a wall Y position
 * and up to two desks (west and east).
 */
data class DeskRow(
    val wallY: Float,
    val westDesk: DeskConfig? = null,
    val eastDesk: DeskConfig? = null
)

/**
 * A column of desk rows sharing the same base X position.
 *
 * Desk numbering within a column:
 * - Odd numbers = east side, even numbers = west side
 * - Row 1 = desks 1,2; Row 2 = desks 3,4; Row 3 = desks 5,6
 */
data class DeskColumn(
    val id: String,
    val baseX: Float,
    val rows: List<DeskRow>
) {
    companion object {
        // Desk column base X positions
        const val LEFT_COLUMN_X = 45f
        const val RIGHT_COLUMN_X = 175f

        // Desk row Y positions (wall positions)
        val DESK_ROW_Y_POSITIONS = listOf(125f, 155f, 185f, 215f)

        /**
         * Calculate actual desk positions for character assignment.
         * @param columnX Base X position of the column (LEFT_COLUMN_X or RIGHT_COLUMN_X)
         * @param rowIndex Row index (0-3)
         * @param isLeftDesk true for left/west desk, false for right/east desk
         * @return Pair of (x, y) coordinates for the desk
         */
        fun getDeskPosition(columnX: Float, rowIndex: Int, isLeftDesk: Boolean): Pair<Float, Float> {
            val wallY = DESK_ROW_Y_POSITIONS[rowIndex]
            val deskX = if (isLeftDesk) columnX + 19f else columnX + 40f
            val deskY = wallY + 11f
            return Pair(deskX, deskY)
        }

        /**
         * Calculate chair position for character sitting.
         * @param columnX Base X position of the column (LEFT_COLUMN_X or RIGHT_COLUMN_X)
         * @param rowIndex Row index (0-3)
         * @param isLeftDesk true for left/west desk, false for right/east desk
         * @return Pair of (x, y) coordinates for the chair
         */
        fun getChairPosition(columnX: Float, rowIndex: Int, isLeftDesk: Boolean): Pair<Float, Float> {
            val wallY = DESK_ROW_Y_POSITIONS[rowIndex]
            val chairX = if (isLeftDesk) columnX + 7f else columnX + 56f
            val chairY = wallY + 2f
            return Pair(chairX, chairY)
        }
    }

    /**
     * Get the DeskConfig for a desk number within this column.
     * Odd = east, even = west. Row index = (number - 1) / 2.
     */
    fun getDesk(number: Int): DeskConfig? {
        val rowIndex = (number - 1) / 2
        if (rowIndex < 0 || rowIndex >= rows.size) return null
        val row = rows[rowIndex]
        return if (number % 2 == 1) row.eastDesk else row.westDesk
    }

    /**
     * Get the string ID for a desk number within this column.
     * e.g., "deskColumn1_desk3"
     */
    fun getDeskId(number: Int): String = "${id}_desk$number"
}

// ==================== DSL Builders ====================

/**
 * Builder for DeskConfig via DSL.
 */
class DeskConfigBuilder(private val side: DeskSide) {
    var equipment: Equipment = Equipment.NONE
    var chairColor: ChairColor = ChairColor.BLACK
    var wallDecor: WallDecor = WallDecor.NONE
    var deskItems: List<DeskItem> = emptyList()

    fun build(): DeskConfig = DeskConfig(
        side = side,
        equipment = equipment,
        chairColor = chairColor,
        wallDecor = wallDecor,
        deskItems = deskItems
    )
}

/**
 * Builder for DeskRow via DSL.
 */
class DeskRowBuilder(private val wallY: Float) {
    private var westDesk: DeskConfig? = null
    private var eastDesk: DeskConfig? = null

    fun westDesk(init: DeskConfigBuilder.() -> Unit) {
        westDesk = DeskConfigBuilder(DeskSide.WEST).apply(init).build()
    }

    fun eastDesk(init: DeskConfigBuilder.() -> Unit) {
        eastDesk = DeskConfigBuilder(DeskSide.EAST).apply(init).build()
    }

    fun build(): DeskRow = DeskRow(
        wallY = wallY,
        westDesk = westDesk,
        eastDesk = eastDesk
    )
}

/**
 * Builder for DeskColumn via DSL.
 */
class DeskColumnBuilder(private val id: String, private val baseX: Float) {
    private val rows = mutableListOf<DeskRow>()

    fun row(wallY: Float, init: DeskRowBuilder.() -> Unit) {
        rows.add(DeskRowBuilder(wallY).apply(init).build())
    }

    fun build(): DeskColumn = DeskColumn(
        id = id,
        baseX = baseX,
        rows = rows
    )
}

/**
 * Top-level DSL function to create a DeskColumn.
 */
fun deskColumn(id: String, baseX: Float, init: DeskColumnBuilder.() -> Unit): DeskColumn {
    return DeskColumnBuilder(id, baseX).apply(init).build()
}
