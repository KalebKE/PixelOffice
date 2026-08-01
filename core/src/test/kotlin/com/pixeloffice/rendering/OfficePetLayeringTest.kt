package com.pixeloffice.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficePetLayeringTest {

    private val desk = DeskRenderInfo(
        id = "deskColumn1_desk1",
        x = 85f,
        y = 136f,
        occupied = true
    )

    @Test
    fun `resting dog at desk is assigned to that furniture row`() {
        val dog = pet(
            type = PetType.DOG,
            moving = false,
            restingAnchorId = "desk:deskColumn1_desk1"
        )

        assertEquals(125f, OfficeLayout.underChairRow(dog, listOf(desk)))
    }

    @Test
    fun `moving dog and cat retain normal floor depth`() {
        assertNull(
            OfficeLayout.underChairRow(
                pet(PetType.DOG, moving = true, restingAnchorId = null),
                listOf(desk)
            )
        )
        assertNull(
            OfficeLayout.underChairRow(
                pet(PetType.CAT, moving = false, restingAnchorId = "desk:deskColumn1_desk1"),
                listOf(desk)
            )
        )
    }

    private fun pet(
        type: PetType,
        moving: Boolean,
        restingAnchorId: String?
    ) = PetRenderInfo(
        entityId = "pet_${type.name.lowercase()}",
        type = type,
        x = 114f,
        y = 136f,
        facing = "left",
        moving = moving,
        restingAnchorId = restingAnchorId,
        visible = true,
        showBubble = false
    )
}
