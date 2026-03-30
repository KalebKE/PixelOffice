package com.pixeloffice.entities

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test: PM (dog) and PO (cat) thought bubbles must use
 * chairPosition for X when sitting at east-side desks, matching the
 * Developer fix.
 */
class PMPOBubblePositionTest {

    // --- ProjectManager ---

    @Test
    fun `PM bubble uses chair x when sitting at east-side desk`() {
        val deskX = 215f
        val chairX = 231f
        val pm = ProjectManager(x = deskX, y = 136f, entityId = "pm_test")
        pm.setBubbleSpawner { entity, bubbleType ->
            ThoughtBubble(x = entity.x, y = entity.y, entityId = "bub", bubbleType = bubbleType)
        }
        pm.setDeskFacing("left")
        pm.setAssignedDesk("desk1", deskX, 136f, chairX, 127f)

        pm.showThoughtBubble("thinking")
        pm.update(0.016f)

        val bubble = pm.getTypedRenderInfo().children.firstOrNull()
        assertTrue(bubble != null, "PM bubble should be present")
        val b = bubble as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        val distFromChair = kotlin.math.abs(b.info.x - (chairX + 8))
        val distFromRawX = kotlin.math.abs(b.info.x - (deskX + 8))
        assertTrue(distFromChair < distFromRawX,
            "PM bubble x (${b.info.x}) should be near chair center (${chairX + 8}), not desk center (${deskX + 8})")
    }

    @Test
    fun `PM east-side bubble has facingLeft false (tail toward desk center)`() {
        val pm = ProjectManager(x = 215f, y = 136f, entityId = "pm_test")
        pm.setBubbleSpawner { entity, bubbleType ->
            ThoughtBubble(x = entity.x, y = entity.y, entityId = "bub", bubbleType = bubbleType)
        }
        pm.setDeskFacing("left")
        pm.setAssignedDesk("desk1", 215f, 136f, 231f, 127f)

        pm.showThoughtBubble("thinking")
        pm.update(0.016f)

        val b = pm.getTypedRenderInfo().children.first() as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        assertTrue(!b.info.facingLeft, "East-side PM bubble tail should point left (toward desk center)")
    }

    // --- ProductOwner ---

    @Test
    fun `PO bubble uses chair x when sitting at east-side desk`() {
        val deskX = 215f
        val chairX = 231f
        val po = ProductOwner(x = deskX, y = 166f, entityId = "po_test")
        po.setBubbleSpawner { entity, bubbleType ->
            ThoughtBubble(x = entity.x, y = entity.y, entityId = "bub", bubbleType = bubbleType)
        }
        po.setDeskFacing("left")
        po.setAssignedDesk("desk3", deskX, 166f, chairX, 157f)

        po.showThoughtBubble("thinking")
        po.update(0.016f)

        val bubble = po.getTypedRenderInfo().children.firstOrNull()
        assertTrue(bubble != null, "PO bubble should be present")
        val b = bubble as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        val distFromChair = kotlin.math.abs(b.info.x - (chairX + 8))
        val distFromRawX = kotlin.math.abs(b.info.x - (deskX + 8))
        assertTrue(distFromChair < distFromRawX,
            "PO bubble x (${b.info.x}) should be near chair center (${chairX + 8}), not desk center (${deskX + 8})")
    }

    @Test
    fun `PO east-side bubble has facingLeft false (tail toward desk center)`() {
        val po = ProductOwner(x = 215f, y = 166f, entityId = "po_test")
        po.setBubbleSpawner { entity, bubbleType ->
            ThoughtBubble(x = entity.x, y = entity.y, entityId = "bub", bubbleType = bubbleType)
        }
        po.setDeskFacing("left")
        po.setAssignedDesk("desk3", 215f, 166f, 231f, 157f)

        po.showThoughtBubble("thinking")
        po.update(0.016f)

        val b = po.getTypedRenderInfo().children.first() as com.pixeloffice.rendering.EffectRenderInfo.Bubble
        assertTrue(!b.info.facingLeft, "East-side PO bubble tail should point left (toward desk center)")
    }
}
