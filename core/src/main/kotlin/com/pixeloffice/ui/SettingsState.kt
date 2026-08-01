package com.pixeloffice.ui

import com.pixeloffice.world.*
import kotlin.random.Random

data class DeskSettings(
    var enabled: Boolean = true,
    var equipment: Equipment = Equipment.NONE,
    var chairColor: ChairColor = ChairColor.BLACK,
    var wallDecor: WallDecor = WallDecor.NONE,
    var deskItems: MutableList<DeskItem> = mutableListOf()
)

data class RowSettings(
    val wallY: Float,
    var westDesk: DeskSettings? = null,
    var eastDesk: DeskSettings? = null
)

data class ColumnSettings(
    val id: String,
    val baseX: Float,
    val rows: MutableList<RowSettings> = mutableListOf()
)

data class DeveloperSettings(
    var agentId: String = "",
    var colorVariant: Int = 0,
    var assignedColumnId: String? = null,
    var assignedDeskNumber: Int? = null
)

data class SettingsConfig(
    val column1: ColumnSettings,
    val column2: ColumnSettings,
    val developers: MutableList<DeveloperSettings> = mutableListOf(),
    var debugMode: Boolean = false
) {
    companion object {
        private const val EQUIPMENT_SALT = 0x13579BDF
        private const val CHAIR_SALT = 0x2468ACE
        private const val WALL_DECOR_SALT = 0x55AA55AA
        private const val COLUMN_ORIENTATION_SALT = 0x41C011

        fun fromDefaults(): SettingsConfig {
            val col1 = ColumnSettings(
                id = "deskColumn1",
                baseX = 45f,
                rows = mutableListOf(
                    RowSettings(
                        wallY = 125f,
                        westDesk = DeskSettings(
                            equipment = Equipment.MONITOR,
                            chairColor = ChairColor.BLACK,
                            wallDecor = WallDecor.SMALL_ART_ORANGE
                        ),
                        eastDesk = DeskSettings(
                            equipment = Equipment.COMPUTER,
                            chairColor = ChairColor.WHITE
                        )
                    ),
                    RowSettings(
                        wallY = 155f,
                        westDesk = DeskSettings(
                            equipment = Equipment.COMPUTER,
                            chairColor = ChairColor.GREEN,
                            wallDecor = WallDecor.ART
                        ),
                        eastDesk = DeskSettings(
                            equipment = Equipment.MONITOR,
                            chairColor = ChairColor.BLUE
                        )
                    ),
                    RowSettings(
                        wallY = 185f,
                        westDesk = DeskSettings(
                            equipment = Equipment.MONITOR,
                            chairColor = ChairColor.ORANGE,
                            wallDecor = WallDecor.SMALL_ART_BLUE
                        ),
                        eastDesk = null
                    )
                )
            )

            val col2 = ColumnSettings(
                id = "deskColumn2",
                baseX = 175f,
                rows = mutableListOf(
                    RowSettings(
                        wallY = 125f,
                        westDesk = DeskSettings(
                            equipment = Equipment.MONITOR,
                            chairColor = ChairColor.BLACK,
                            wallDecor = WallDecor.SMALL_ART_ORANGE
                        ),
                        eastDesk = DeskSettings(
                            equipment = Equipment.COMPUTER,
                            chairColor = ChairColor.WHITE
                        )
                    ),
                    RowSettings(
                        wallY = 155f,
                        westDesk = DeskSettings(
                            equipment = Equipment.COMPUTER,
                            chairColor = ChairColor.GREEN,
                            wallDecor = WallDecor.ART
                        ),
                        eastDesk = DeskSettings(
                            equipment = Equipment.MONITOR,
                            chairColor = ChairColor.BLUE
                        )
                    )
                )
            )

            val devs = mutableListOf(
                DeveloperSettings("demo_agent_1", 0),
                DeveloperSettings("demo_agent_2", 1),
                DeveloperSettings("demo_agent_3", 2)
            )

            return SettingsConfig(
                column1 = col1,
                column2 = col2,
                developers = devs,
                debugMode = false
            )
        }

        /**
         * Build a balanced desk appearance that is stable for a project ID.
         * Desk geometry and availability remain identical to [fromDefaults].
         */
        fun randomizedForProject(
            projectId: String,
            loungeOnLeft: Boolean = stableLoungeOnLeftForProject(projectId)
        ): SettingsConfig {
            val defaults = fromDefaults()
            val result = if (loungeOnLeft) {
                defaults.copy(
                    column1 = defaults.column1.copy(baseX = DeskColumn.RIGHT_COLUMN_X),
                    column2 = defaults.column2.copy(baseX = DeskColumn.LEFT_COLUMN_X)
                )
            } else {
                defaults
            }
            val desks = result.allDeskSettings()
            val deskCount = desks.size

            val equipment = (
                List((deskCount + 1) / 2) { Equipment.COMPUTER } +
                    List(deskCount / 2) { Equipment.MONITOR }
                ).shuffled(projectRandom(projectId, EQUIPMENT_SALT))
            val chairs = balancedValues(
                ChairColor.entries,
                deskCount,
                projectRandom(projectId, CHAIR_SALT)
            )
            val wallDecor = valuesWithEmptySlots(
                WallDecor.entries.filterNot { it == WallDecor.NONE },
                WallDecor.NONE,
                deskCount,
                projectRandom(projectId, WALL_DECOR_SALT)
            )
            desks.forEachIndexed { index, desk ->
                desk.equipment = equipment[index]
                desk.chairColor = chairs[index]
                desk.wallDecor = wallDecor[index]
                desk.deskItems = mutableListOf()
            }
            return result
        }

        internal fun stableLoungeOnLeftForProject(projectId: String): Boolean =
            projectRandom(projectId, COLUMN_ORIENTATION_SALT).nextBoolean()

        private fun projectRandom(projectId: String, salt: Int): Random =
            Random(31 * projectId.hashCode() + salt)

        private fun <T> balancedValues(values: List<T>, count: Int, random: Random): List<T> {
            if (values.isEmpty() || count <= 0) return emptyList()
            val result = mutableListOf<T>()
            while (result.size < count) {
                result += values.shuffled(random).take(count - result.size)
            }
            return result
        }

        private fun <T> valuesWithEmptySlots(
            values: List<T>,
            empty: T,
            count: Int,
            random: Random
        ): List<T> {
            if (count <= 0) return emptyList()
            val filledCount = (count * 2) / 3
            val filled = balancedValues(values, filledCount, random)
            return (filled + List(count - filledCount) { empty }).shuffled(random)
        }
    }

    fun allDeskSettings(): List<DeskSettings> = buildList {
        fun collect(column: ColumnSettings) {
            for (row in column.rows) {
                row.westDesk?.takeIf { it.enabled }?.let(::add)
                row.eastDesk?.takeIf { it.enabled }?.let(::add)
            }
        }
        collect(column1)
        collect(column2)
    }

    fun deepCopy(): SettingsConfig = copy(
        column1 = column1.copy(rows = column1.rows.map { row ->
            row.copy(
                westDesk = row.westDesk?.copy(deskItems = row.westDesk!!.deskItems.toMutableList()),
                eastDesk = row.eastDesk?.copy(deskItems = row.eastDesk!!.deskItems.toMutableList())
            )
        }.toMutableList()),
        column2 = column2.copy(rows = column2.rows.map { row ->
            row.copy(
                westDesk = row.westDesk?.copy(deskItems = row.westDesk!!.deskItems.toMutableList()),
                eastDesk = row.eastDesk?.copy(deskItems = row.eastDesk!!.deskItems.toMutableList())
            )
        }.toMutableList()),
        developers = developers.map { it.copy() }.toMutableList()
    )

    fun getAllDeskIds(): List<String> {
        val ids = mutableListOf<String>()
        fun collectFromColumn(col: ColumnSettings) {
            for ((rowIndex, row) in col.rows.withIndex()) {
                if (row.westDesk != null && row.westDesk!!.enabled) {
                    ids.add("${col.id}_desk${(rowIndex + 1) * 2}")
                }
                if (row.eastDesk != null && row.eastDesk!!.enabled) {
                    ids.add("${col.id}_desk${(rowIndex + 1) * 2 - 1}")
                }
            }
        }
        collectFromColumn(column1)
        collectFromColumn(column2)
        return ids
    }
}
