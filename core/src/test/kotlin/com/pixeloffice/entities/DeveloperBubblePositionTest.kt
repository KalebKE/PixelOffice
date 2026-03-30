package com.pixeloffice.entities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test: thought bubbles must be positioned relative to the
 * chair (render) position, not the raw entity position, so that east-side
 * developers have bubbles above their head just like west-side developers.
 */
class DeveloperBubblePositionTest {

    private fun makeDeveloper(): Developer {
        val dev = Developer(x = 50f, y = 130f, entityId = "dev_test", agentId = "agent_test")
        dev.setBubbleSpawner { entity, bubbleType ->
            ThoughtBubble(x = entity.x, y = entity.y, entityId = "bubble_test", bubbleType = bubbleType)
        }
        return dev
    }

    @Test
    fun `east-side bubble is offset right from chair position`() {
        val dev = makeDeveloper()
        // East-side desk: chair at 106, raw entity at 50
        dev.setChairPosition(106f, 132f)
        dev.setDeskFacing("left") // east desks face left

        dev.showBubbleOfType("thinking")
        dev.update(0.016f)

        val bubble = dev.getTypedRenderInfo().children.first() as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        // East side: bubbleX = chairX + 16 = 122
        assertEquals(122f, bubble.info.x, "East-side bubble should be at chairX + 16")
    }

    @Test
    fun `west-side bubble is offset left from chair position`() {
        val dev = makeDeveloper()
        // West-side desk: chair at 57, raw entity at 50
        dev.setChairPosition(57f, 132f)
        dev.setDeskFacing("right") // west desks face right

        dev.showBubbleOfType("thinking")
        dev.update(0.016f)

        val bubble = dev.getTypedRenderInfo().children.first() as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        // West side: bubbleX = chairX - 16 = 41
        assertEquals(41f, bubble.info.x, "West-side bubble should be at chairX - 16")
    }

    @Test
    fun `west-side bubble has facingLeft true (tail toward desk center)`() {
        val dev = makeDeveloper()
        dev.setChairPosition(52f, 132f)
        dev.setDeskFacing("right") // west side faces right

        dev.showBubbleOfType("thinking")
        dev.update(0.016f)

        val bubble = dev.getTypedRenderInfo().children.first() as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        assertTrue(bubble.info.facingLeft, "West-side bubble tail should point right (toward desk center)")
    }

    @Test
    fun `east-side bubble has facingLeft false (tail toward desk center)`() {
        val dev = makeDeveloper()
        dev.setChairPosition(101f, 132f)
        dev.setDeskFacing("left") // east side faces left

        dev.showBubbleOfType("thinking")
        dev.update(0.016f)

        val bubble = dev.getTypedRenderInfo().children.first() as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        assertTrue(!bubble.info.facingLeft, "East-side bubble tail should point left (toward desk center)")
    }
}
