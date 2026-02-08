package com.pixeloffice.ui

import com.pixeloffice.world.*

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
    var spawnPM: Boolean = true,
    var pmDeskId: String? = null,
    var spawnPO: Boolean = true,
    var poDeskId: String? = null,
    var debugMode: Boolean = false
) {
    companion object {
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
                spawnPM = true,
                pmDeskId = null,
                spawnPO = true,
                poDeskId = null,
                debugMode = false
            )
        }
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
