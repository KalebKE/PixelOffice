package com.pixeloffice.rendering

import com.pixeloffice.world.DeskColumn
import kotlin.random.Random

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
 * Typed render data for a developer character.
 */
data class CharacterRenderInfo(
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

enum class PetType {
    CAT,
    DOG
}

data class PetRenderInfo(
    val entityId: String,
    val type: PetType,
    val x: Float,
    val y: Float,
    val facing: String,
    val moving: Boolean,
    val restingAnchorId: String?,
    val visible: Boolean,
    val showBubble: Boolean
)

/** Coordinated lounge and lava-lamp colors, selected stably per project. */
enum class OfficeAccentPalette(
    val couchSprite: String,
    val trashCanSprite: String
) {
    SUNSET("couch_orange", "red_trash_can"),
    FOREST("couch_green", "green_trash_can"),
    OCEAN("couch_blue", "blue_trash_can"),
    SLATE("couch_gray", "blue_trash_can");

    companion object {
        private const val PROJECT_SEED_SALT = 0x0FF1CE

        fun forProject(projectId: String): OfficeAccentPalette {
            val random = Random(31 * projectId.hashCode() + PROJECT_SEED_SALT)
            return entries[random.nextInt(entries.size)]
        }
    }
}

/**
 * Complete typed render data passed from Office to Renderer each frame.
 * Replaces the previous Map<String, Any> with compile-time type safety.
 */
data class RenderData(
    val desks: List<DeskRenderInfo>,
    val whiteboards: List<WhiteboardRenderInfo>,
    val developers: List<CharacterRenderInfo>,
    val pets: List<PetRenderInfo>,
    val effects: List<EffectRenderInfo>,
    val deskColumns: List<DeskColumn>,
    val accentPalette: OfficeAccentPalette
)
