package com.pixeloffice.rendering

import com.pixeloffice.world.DeskColumn
import com.pixeloffice.world.DeskRow
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficeLayoutTest {

    @Test
    fun `furniture zones follow the tall and short columns`() {
        val tall = DeskColumn(
            "tall",
            DeskColumn.RIGHT_COLUMN_X,
            listOf(DeskRow(125f), DeskRow(155f), DeskRow(185f))
        )
        val short = DeskColumn(
            "short",
            DeskColumn.LEFT_COLUMN_X,
            listOf(DeskRow(125f), DeskRow(155f))
        )

        assertEquals(
            OfficeColumnZones(
                deskBaseX = DeskColumn.RIGHT_COLUMN_X,
                loungeBaseX = DeskColumn.LEFT_COLUMN_X
            ),
            OfficeLayout.columnZones(listOf(short, tall))
        )
    }
}
