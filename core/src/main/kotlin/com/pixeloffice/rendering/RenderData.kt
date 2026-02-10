package com.pixeloffice.rendering

import com.pixeloffice.world.DeskColumn

/**
 * Typed render data for a desk.
 */
data class DeskRenderInfo(
    val id: String,
    val x: Float,
    val y: Float,
    val occupied: Boolean
)

/**
 * Typed render data for a whiteboard.
 */
data class WhiteboardRenderInfo(
    val id: String,
    val x: Float,
    val y: Float
)

/**
 * Typed render data for a thought bubble effect.
 */
data class BubbleRenderInfo(
    val x: Float,
    val y: Float,
    val frame: Int,
    val bubbleType: String,
    val facingLeft: Boolean = false
)

/**
 * Typed render data for a ghost effect.
 */
data class GhostRenderInfo(
    val x: Float,
    val y: Float,
    val frame: Int,
    val alpha: Float
)

/**
 * Union of effect types for rendering.
 */
sealed class EffectRenderInfo {
    data class Bubble(val info: BubbleRenderInfo) : EffectRenderInfo()
    data class Ghost(val info: GhostRenderInfo) : EffectRenderInfo()
}

/**
 * Typed render data for a character (developer, PM, or PO).
 */
data class CharacterRenderInfo(
    val type: String,         // "developer", "project_manager", "product_owner"
    val entityId: String,
    val x: Float,
    val y: Float,
    val animation: String,
    val facing: String,
    val variant: Int,
    val posture: String,      // "sitting" or "standing"
    val visible: Boolean,
    val state: String,
    val children: List<EffectRenderInfo> = emptyList()
)

/**
 * Complete typed render data passed from Office to Renderer each frame.
 * Replaces the previous Map<String, Any> with compile-time type safety.
 */
data class RenderData(
    val desks: List<DeskRenderInfo>,
    val whiteboards: List<WhiteboardRenderInfo>,
    val developers: List<CharacterRenderInfo>,
    val effects: List<EffectRenderInfo>,
    val deskColumns: List<DeskColumn>,
    val projectManager: CharacterRenderInfo?,
    val productOwner: CharacterRenderInfo?
)
