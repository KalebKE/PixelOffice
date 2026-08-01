package com.pixeloffice.world

import com.pixeloffice.core.Config
import com.pixeloffice.core.DeveloperConfig
import com.pixeloffice.core.SpriteConfig
import com.pixeloffice.states.DeveloperStateNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedDeveloperOfficeTest {

    @Test
    fun `empty office has no permanent characters and every configured desk is available`() {
        val office = office()

        assertTrue(office.getAllDevelopers().isEmpty())
        assertEquals(9, office.getRenderData().desks.size)

        repeat(9) { index ->
            assertNotNull(office.spawnDeveloper("agent-$index"))
        }
        assertEquals(9, office.getAllDevelopers().size)
        assertNull(office.spawnDeveloper("overflow"))
    }

    @Test
    fun `appearance is stable and uses all five developer sprites`() {
        val firstOffice = office()
        val secondOffice = office()

        val first = assertNotNull(firstOffice.spawnDeveloper("codex:session-1:main"))
        val second = assertNotNull(secondOffice.spawnDeveloper("codex:session-1:main"))
        assertEquals(first.getSpriteVariantIndex(), second.getSpriteVariantIndex())

        val variants = (0 until 100)
            .map { id -> office().spawnDeveloper("agent-$id")!!.getSpriteVariantIndex() }
            .toSet()
        assertEquals(setOf(0, 1, 2, 3, 4), variants)
    }

    @Test
    fun `idle developer patrols three stops alone and returns home`() {
        val office = office(maxStops = 3)
        val developer = assertNotNull(office.spawnDeveloper("solo"))

        office.update(0.01f)
        assertEquals("WALKING", office.getPatrolPhase("solo"))

        var returned = false
        for (step in 0 until 2_000) {
            office.update(0.01f)
            assertTrue(office.getPatrolStopsVisited("solo") <= 3)
            if (office.getPatrolPhase("solo") == "IDLE_DELAY" &&
                office.getPatrolStopsVisited("solo") == 0
            ) {
                returned = true
                break
            }
        }

        assertTrue(returned, "solo patrol should return to its assigned desk")
        assertTrue(developer.isAtDesk())
    }

    @Test
    fun `idle visitor interrupts an active developer and activity resumes`() {
        val office = office(maxStops = 1, socialDuration = 0.2f)
        assertNotNull(office.spawnDeveloper("visitor"))
        val target = assertNotNull(office.spawnDeveloper("target"))
        target.handleEvent("code_writing_started")

        advanceUntil(office) { office.getActiveConversationCount() == 1 }
        assertEquals(DeveloperStateNames.BEING_INTERRUPTED, target.getState())

        office.update(0.25f)
        assertEquals(0, office.getActiveConversationCount())
        assertEquals(DeveloperStateNames.WRITING_CODE, target.getState())
    }

    @Test
    fun `new agent event cancels a conversation and takes priority`() {
        val office = office(maxStops = 1, socialDuration = 1f)
        assertNotNull(office.spawnDeveloper("visitor"))
        val target = assertNotNull(office.spawnDeveloper("target"))
        target.handleEvent("code_writing_started")

        advanceUntil(office) { office.getActiveConversationCount() == 1 }
        target.handleEvent("planning_started")

        assertEquals(0, office.getActiveConversationCount())
        assertEquals(DeveloperStateNames.WALKING_TO_WHITEBOARD, target.getState())
    }

    @Test
    fun `work event during patrol returns developer to desk before activity`() {
        val office = office(maxStops = 3)
        val developer = assertNotNull(office.spawnDeveloper("worker"))
        office.update(0.01f)
        assertEquals("WALKING", office.getPatrolPhase("worker"))
        for (step in 0 until 20) {
            if (!developer.isAtDesk()) break
            office.update(0.01f)
        }
        assertTrue(!developer.isAtDesk())

        developer.handleEvent("code_writing_started")
        assertEquals(DeveloperStateNames.WALKING_TO_DESK, developer.getState())

        advanceUntil(office) { developer.getState() == DeveloperStateNames.WRITING_CODE }
        assertTrue(developer.isAtDesk())
    }

    @Test
    fun `removing a conversation participant releases the other developer`() {
        val office = office(maxStops = 1, socialDuration = 1f)
        assertNotNull(office.spawnDeveloper("visitor"))
        val target = assertNotNull(office.spawnDeveloper("target"))
        target.handleEvent("code_writing_started")
        advanceUntil(office) { office.getActiveConversationCount() == 1 }

        office.removeDeveloper("target")

        assertEquals(0, office.getActiveConversationCount())
        assertNull(office.getDeveloper("target"))
        assertNotNull(office.getDeveloper("visitor"))
        office.update(0.01f)
    }

    private fun office(maxStops: Int = 3, socialDuration: Float = 0.02f): Office {
        val config = Config(
            sprites = SpriteConfig(
                colorVariants = listOf("blue", "green", "red", "red_hair", "dark_hair")
            ),
            developer = DeveloperConfig(
                walkSpeed = 1_000f,
                idlePatrolMinSeconds = 0f,
                idlePatrolMaxSeconds = 0f,
                idlePatrolMaxStops = maxStops,
                socialDurationSeconds = socialDuration
            )
        )
        return Office(config, randomFloat = { 0f }).also { it.setupDefaultDeskColumns() }
    }

    private fun advanceUntil(office: Office, condition: () -> Boolean) {
        repeat(2_000) {
            if (condition()) return
            office.update(0.01f)
        }
        error("Condition was not reached")
    }
}
