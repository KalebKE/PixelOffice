package com.pixeloffice.states

import com.pixeloffice.entities.Developer
import com.pixeloffice.entities.ThoughtBubble
import kotlin.test.Test
import kotlin.test.assertEquals

class DeveloperEventStateTest {
    @Test
    fun `waiting persists and accepts the next work event`() {
        val developer = developerAtDesk()

        developer.handleEvent("waiting_started")
        assertEquals(DeveloperStateNames.WAITING, developer.getState())
        developer.update(10f)
        assertEquals(DeveloperStateNames.WAITING, developer.getState())

        developer.handleEvent("code_writing_started")
        assertEquals(DeveloperStateNames.WRITING_CODE, developer.getState())
    }

    @Test
    fun `idle is a global transition while at the desk`() {
        val developer = developerAtDesk()
        developer.handleEvent("code_writing_started")
        assertEquals(DeveloperStateNames.WRITING_CODE, developer.getState())

        developer.handleEvent("idle")
        assertEquals(DeveloperStateNames.IDLE, developer.getState())
    }

    @Test
    fun `thinking persists until the next agent event`() {
        val developer = developerAtDesk()

        developer.handleEvent("thinking_started")
        developer.update(60f)

        assertEquals(DeveloperStateNames.THINKING, developer.getState())
        val bubble = developer.getTypedRenderInfo().children.single()
        assertEquals(
            "thinking",
            (bubble as com.pixeloffice.rendering.EffectRenderInfo.Bubble).info.bubbleType
        )

        developer.handleEvent("code_writing_started")
        assertEquals(DeveloperStateNames.WRITING_CODE, developer.getState())
    }

    @Test
    fun `success celebration returns to thinking instead of idle`() {
        val developer = developerAtDesk()

        developer.handleEvent("command_succeeded")
        assertEquals(DeveloperStateNames.CELEBRATING, developer.getState())

        developer.update(2f)
        assertEquals(DeveloperStateNames.THINKING, developer.getState())
        val bubble = developer.getTypedRenderInfo().children.single()
        assertEquals(
            "thinking",
            (bubble as com.pixeloffice.rendering.EffectRenderInfo.Bubble).info.bubbleType
        )
    }

    private fun developerAtDesk(): Developer {
        return Developer(x = 10f, y = 20f, agentId = "test").also {
            it.setDeskPosition(10f, 20f)
            it.setChairPosition(10f, 20f)
            it.setBubbleSpawner { entity, bubbleType ->
                ThoughtBubble(entity.x, entity.y, bubbleType = bubbleType)
            }
            it.start()
        }
    }
}
