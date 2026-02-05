package com.pixeloffice.entities

import com.pixeloffice.rendering.EffectRenderInfo
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 2D position.
 */
data class Position(var x: Float = 0f, var y: Float = 0f)

/**
 * 2D velocity.
 */
data class Velocity(var x: Float = 0f, var y: Float = 0f)

/**
 * Abstract base class for all entities in the office.
 *
 * Entities have position, velocity, and can be updated and rendered.
 */
abstract class BaseEntity(
    x: Float = 0f,
    y: Float = 0f,
    val entityId: String = ""
) {
    val position = Position(x, y)
    val velocity = Velocity()
    var visible = true
    var active = true
    protected var currentAnimation = "idle"
    protected var facingDirection = "down" // up, down, left, right
    protected var spriteVariant = 0 // Color variant index

    // Sprite width for center-based positioning (characters are 16px wide)
    protected open val spriteHalfWidth: Float = 8f

    var x: Float
        get() = position.x
        set(value) { position.x = value }

    var y: Float
        get() = position.y
        set(value) { position.y = value }

    /**
     * Update entity state.
     * @param dt Delta time in seconds since last update.
     */
    abstract fun update(dt: Float)

    /**
     * Get information needed to render this entity.
     * @return Map with rendering data (sprite, position, etc).
     */
    abstract fun getRenderInfo(): Map<String, Any>

    /**
     * Convert this entity to a typed EffectRenderInfo.
     * Only valid for effect entities (ThoughtBubble, Ghost).
     */
    open fun toEffectRenderInfo(): EffectRenderInfo {
        throw UnsupportedOperationException("${this::class.simpleName} is not an effect entity")
    }

    fun setAnimation(animation: String) {
        currentAnimation = animation
    }

    fun getAnimation(): String = currentAnimation

    fun setFacing(direction: String) {
        if (direction in listOf("up", "down", "left", "right")) {
            facingDirection = direction
        }
    }

    fun getFacing(): String = facingDirection

    fun setSpriteVariantIndex(variant: Int) {
        spriteVariant = variant
    }

    fun getSpriteVariantIndex(): Int = spriteVariant

    /**
     * Move towards a target position.
     * Uses the sprite's horizontal center for positioning calculations.
     *
     * @param targetX Target x position.
     * @param targetY Target y position.
     * @param speed Movement speed in pixels per second.
     * @param dt Delta time in seconds.
     * @return True if reached the target, False otherwise.
     */
    fun moveTowards(targetX: Float, targetY: Float, speed: Float, dt: Float): Boolean {
        // Calculate distance from sprite CENTER to target
        val centerX = x + spriteHalfWidth
        val dx = targetX - centerX
        val dy = targetY - y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < speed * dt) {
            // Snap so sprite CENTER is at target
            x = targetX - spriteHalfWidth
            y = targetY
            velocity.x = 0f
            velocity.y = 0f
            return true
        }

        // Movement uses center position for direction calculation
        if (distance > 0) {
            velocity.x = (dx / distance) * speed
            velocity.y = (dy / distance) * speed

            x += velocity.x * dt
            y += velocity.y * dt

            // Update facing direction
            if (abs(dx) > abs(dy)) {
                setFacing(if (dx > 0) "right" else "left")
            } else {
                setFacing(if (dy > 0) "down" else "up")
            }
        }

        return false
    }

    /**
     * Calculate distance to another entity.
     */
    fun distanceTo(other: BaseEntity): Float {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate distance to a point from the sprite's horizontal center.
     */
    fun distanceToPoint(px: Float, py: Float): Float {
        val centerX = x + spriteHalfWidth
        val dx = px - centerX
        val dy = py - y
        return sqrt(dx * dx + dy * dy)
    }
}
