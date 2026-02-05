package com.pixeloffice.entities

import com.pixeloffice.rendering.EffectRenderInfo
import com.pixeloffice.rendering.GhostRenderInfo
import kotlin.math.max

/**
 * A ghost effect that rises from a developer when tests fail.
 *
 * The ghost rises up slowly, becomes transparent, and disappears.
 */
class Ghost(
    x: Float = 0f,
    y: Float = 0f,
    entityId: String = "",
    private val riseSpeed: Float = 20f,
    private val riseHeight: Float = 40f,
    private val frameDuration: Float = 0.15f
) : BaseEntity(x, y, entityId) {

    private var startY = y
    private var currentFrame = 0
    private var frameTimer = 0f
    private val totalFrames = 8
    var alpha = 1f  // Transparency (1.0 = opaque, 0.0 = invisible)
        private set

    init {
        setAnimation("rise")
    }

    /**
     * Reset ghost to start rising from a new position.
     */
    fun reset(newX: Float, newY: Float) {
        x = newX
        y = newY
        startY = newY
        currentFrame = 0
        frameTimer = 0f
        alpha = 1f
        active = true
        visible = true
    }

    override fun update(dt: Float) {
        if (!active) return

        // Rise upward (Y decreases to go up in screen coords)
        y -= riseSpeed * dt

        // Fade out as it rises
        val distanceRisen = startY - y
        alpha = max(0f, 1f - (distanceRisen / riseHeight))

        // Update animation frame
        frameTimer += dt
        if (frameTimer >= frameDuration) {
            frameTimer = 0f
            currentFrame = (currentFrame + 1) % totalFrames
        }

        // Check if animation is complete
        if (distanceRisen >= riseHeight) {
            active = false
            visible = false
        }
    }

    override fun getRenderInfo(): Map<String, Any> {
        return mapOf(
            "type" to "ghost",
            "x" to x,
            "y" to y,
            "animation" to currentAnimation,
            "frame" to currentFrame,
            "alpha" to alpha,
            "visible" to visible
        )
    }

    override fun toEffectRenderInfo(): EffectRenderInfo =
        EffectRenderInfo.Ghost(GhostRenderInfo(x, y, currentFrame, alpha))

    /**
     * Check if ghost animation is complete.
     */
    fun isFinished(): Boolean = !active
}
