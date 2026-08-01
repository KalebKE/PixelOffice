package com.pixeloffice.rendering

import com.pixeloffice.world.DeskColumn

data class OfficeColumnZones(
    val deskBaseX: Float,
    val loungeBaseX: Float
)

/** Shared vertical measurements used by the office renderer and project grid. */
object OfficeLayout {
    private const val DESK_SURFACE_Y_OFFSET = 11f

    const val SKY_HEIGHT = 38f

    fun contentHeight(officeHeight: Float): Float =
        (officeHeight - SKY_HEIGHT).coerceAtLeast(0f)

    /** Locate the taller desk block and shorter lounge block after orientation. */
    fun columnZones(columns: List<DeskColumn>): OfficeColumnZones {
        val deskColumn = columns.maxByOrNull { it.rows.size }
        val loungeColumn = columns
            .filterNot { it.id == deskColumn?.id }
            .minByOrNull { it.rows.size }
        return OfficeColumnZones(
            deskBaseX = deskColumn?.baseX ?: DeskColumn.LEFT_COLUMN_X,
            loungeBaseX = loungeColumn?.baseX ?: DeskColumn.RIGHT_COLUMN_X
        )
    }

    /**
     * Returns the wall row for a dog resting at a desk. The renderer uses this
     * to place the dog after the cubicle wall but before the desk and chair,
     * leaving the pulled-back chair visibly in front of it.
     */
    fun underChairRow(pet: PetRenderInfo, desks: List<DeskRenderInfo>): Float? {
        if (pet.type != PetType.DOG || pet.moving) return null
        val deskId = pet.restingAnchorId?.removePrefix("desk:") ?: return null
        if (deskId == pet.restingAnchorId) return null
        return desks.find { it.id == deskId }?.y?.minus(DESK_SURFACE_Y_OFFSET)
    }
}
