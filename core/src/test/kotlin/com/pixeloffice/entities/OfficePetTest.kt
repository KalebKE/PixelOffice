package com.pixeloffice.entities

import com.pixeloffice.core.PetConfig
import com.pixeloffice.rendering.PetType
import com.pixeloffice.world.Pathfinder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficePetTest {

    @Test
    fun `pet faces the desk while resting at a desk anchor`() {
        val pathfinder = object : Pathfinder {
            override fun calculatePath(
                startX: Float,
                startY: Float,
                endX: Float,
                endY: Float
            ) = listOf(endX to endY)

            override fun getDeskMidpoint(deskId: String) = null
        }
        val pet = OfficePet(
            type = PetType.DOG,
            startAnchor = PetAnchor("desk:west", 10f, 10f, restingFacing = "right"),
            pathfinder = pathfinder,
            config = PetConfig(walkSpeed = 100f),
            randomFloat = { 0f }
        )

        assertEquals("right", pet.getTypedRenderInfo().facing)
        pet.startRoam(PetAnchor("desk:east", 20f, 10f, restingFacing = "left"))
        repeat(10) { pet.update(0.1f) }

        assertEquals("left", pet.getTypedRenderInfo().facing)
    }

    @Test
    fun `pet walks a path and settles at its new anchor`() {
        val pathfinder = object : Pathfinder {
            override fun calculatePath(
                startX: Float,
                startY: Float,
                endX: Float,
                endY: Float
            ): List<Pair<Float, Float>> = listOf(50f to 20f, endX to endY)
        }
        val pet = OfficePet(
            type = PetType.CAT,
            startAnchor = PetAnchor("desk:a", 10f, 10f),
            pathfinder = pathfinder,
            config = PetConfig(roamMinSeconds = 60f, roamMaxSeconds = 60f, walkSpeed = 100f),
            randomFloat = { 0f }
        )

        pet.startRoam(PetAnchor("lounge:center", 80f, 20f))
        assertTrue(pet.isMoving())
        repeat(20) { pet.update(0.1f) }

        assertFalse(pet.isMoving())
        assertEquals("lounge:center", pet.currentAnchorId)
        assertEquals(null, pet.targetAnchorId)
        assertEquals(72f, pet.x)
        assertEquals(20f, pet.y)
    }
}
