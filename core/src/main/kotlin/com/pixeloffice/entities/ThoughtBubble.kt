package com.pixeloffice.entities

import kotlin.math.sin

/**
 * A thought bubble that appears above a developer when thinking.
 *
 * Shows animated dots or symbols to indicate the developer is processing.
 */
class ThoughtBubble(
    x: Float = 0f,
    y: Float = 0f,
    entityId: String = "",
    private val frameDuration: Float = 0.3f,
    private val bobAmount: Float = 2f,
    private val bobSpeed: Float = 3f
) : BaseEntity(x, y, entityId) {

    private var baseY = y
    private var currentFrame = 0
    private var frameTimer = 0f
    private val totalFrames = 4
    private var bobTimer = 0f

    init {
        setAnimation("thinking")
    }

    /**
     * Attach bubble above a position.
     */
    fun attachTo(newX: Float, newY: Float) {
        x = newX
        baseY = newY
        y = newY
    }

    override fun update(dt: Float) {
        if (!active) return

        // Update bob animation
        bobTimer += dt * bobSpeed
        y = baseY + sin(bobTimer) * bobAmount

        // Update frame animation
        frameTimer += dt
        if (frameTimer >= frameDuration) {
            frameTimer = 0f
            currentFrame = (currentFrame + 1) % totalFrames
        }
    }

    override fun getRenderInfo(): Map<String, Any> {
        return mapOf(
            "type" to "thought_bubble",
            "x" to x,
            "y" to y,
            "animation" to currentAnimation,
            "frame" to currentFrame,
            "visible" to visible
        )
    }

    /**
     * Show the thought bubble.
     */
    fun show() {
        visible = true
        active = true
        frameTimer = 0f
        bobTimer = 0f
    }

    /**
     * Hide the thought bubble.
     */
    fun hide() {
        visible = false
        active = false
    }
}
