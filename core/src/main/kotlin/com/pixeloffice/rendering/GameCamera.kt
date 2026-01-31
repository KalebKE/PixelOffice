package com.pixeloffice.rendering

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Rectangular bounds for visibility checks.
 */
data class Bounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun contains(px: Float, py: Float): Boolean {
        return px >= x && px < x + width && py >= y && py < y + height
    }

    fun intersects(other: Bounds): Boolean {
        return x < other.x + other.width &&
               x + width > other.x &&
               y < other.y + other.height &&
               y + height > other.y
    }
}

/**
 * Camera for controlling the viewport.
 *
 * Handles scrolling, zoom, and coordinate transformations
 * between world and screen space.
 *
 * Note: libGDX uses Y-up coordinate system by default.
 * We use camera.setToOrtho(true, ...) to flip Y for Pyxel compatibility.
 */
class GameCamera(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
    private var worldWidth: Int = viewportWidth,
    private var worldHeight: Int = viewportHeight
) {
    // The underlying libGDX camera
    // Using standard Y-up coordinates (Y=0 at bottom)
    val camera = OrthographicCamera().apply {
        setToOrtho(false, viewportWidth.toFloat(), viewportHeight.toFloat())
    }

    // Camera position (top-left corner in world space for Y-down system)
    private var posX = 0f
    private var posY = 0f

    // Zoom
    private var zoomLevel = 1f
    private val minZoom = 0.5f
    private val maxZoom = 2.0f

    // Smooth following
    private var targetX: Float? = null
    private var targetY: Float? = null
    private val followSpeed = 5.0f

    // Shake effect
    private var shakeIntensity = 0f
    private val shakeDecay = 5.0f
    private var shakeOffsetX = 0f
    private var shakeOffsetY = 0f

    val x: Float get() = posX + shakeOffsetX
    val y: Float get() = posY + shakeOffsetY
    val zoom: Float get() = zoomLevel

    fun setPosition(x: Float, y: Float) {
        posX = clampX(x)
        posY = clampY(y)
        updateCamera()
    }

    fun move(dx: Float, dy: Float) {
        setPosition(posX + dx, posY + dy)
    }

    fun centerOn(x: Float, y: Float) {
        setPosition(
            x - viewportWidth / (2 * zoomLevel),
            y - viewportHeight / (2 * zoomLevel)
        )
    }

    fun follow(x: Float, y: Float) {
        targetX = x - viewportWidth / (2 * zoomLevel)
        targetY = y - viewportHeight / (2 * zoomLevel)
    }

    fun setZoom(zoom: Float) {
        zoomLevel = MathUtils.clamp(zoom, minZoom, maxZoom)
        camera.zoom = zoomLevel
        updateCamera()
    }

    fun zoomIn(amount: Float = 0.1f) {
        setZoom(zoomLevel - amount) // Decreasing zoom zooms in
    }

    fun zoomOut(amount: Float = 0.1f) {
        setZoom(zoomLevel + amount)
    }

    fun shake(intensity: Float) {
        shakeIntensity = intensity
    }

    fun update(dt: Float) {
        // Smooth follow
        val tx = targetX
        val ty = targetY
        if (tx != null && ty != null) {
            val dx = tx - posX
            val dy = ty - posY
            posX += dx * followSpeed * dt
            posY += dy * followSpeed * dt
            posX = clampX(posX)
            posY = clampY(posY)
        }

        // Update shake
        if (shakeIntensity > 0) {
            shakeOffsetX = Random.nextFloat() * 2 * shakeIntensity - shakeIntensity
            shakeOffsetY = Random.nextFloat() * 2 * shakeIntensity - shakeIntensity
            shakeIntensity -= shakeDecay * dt
            if (shakeIntensity < 0) {
                shakeIntensity = 0f
                shakeOffsetX = 0f
                shakeOffsetY = 0f
            }
        }

        updateCamera()
    }

    private fun updateCamera() {
        // Position camera so that (x, y) is at the top-left of the viewport
        camera.position.set(
            x + viewportWidth / 2f,
            y + viewportHeight / 2f,
            0f
        )
        camera.update()
    }

    /**
     * Convert world coordinates to screen coordinates.
     */
    fun worldToScreen(wx: Float, wy: Float): Vector2 {
        val sx = (wx - x) * (1f / zoomLevel)
        val sy = (wy - y) * (1f / zoomLevel)
        return Vector2(sx, sy)
    }

    /**
     * Convert screen coordinates to world coordinates.
     */
    fun screenToWorld(sx: Float, sy: Float): Vector2 {
        val wx = sx * zoomLevel + x
        val wy = sy * zoomLevel + y
        return Vector2(wx, wy)
    }

    fun getVisibleBounds(): Bounds {
        return Bounds(
            x = x,
            y = y,
            width = viewportWidth / zoomLevel,
            height = viewportHeight / zoomLevel
        )
    }

    fun isVisible(x: Float, y: Float, width: Float = 0f, height: Float = 0f): Boolean {
        val visible = getVisibleBounds()
        val entityBounds = Bounds(x, y, width, height)
        return visible.intersects(entityBounds)
    }

    private fun clampX(x: Float): Float {
        val maxX = worldWidth - viewportWidth / zoomLevel
        return max(0f, min(maxX, x))
    }

    private fun clampY(y: Float): Float {
        val maxY = worldHeight - viewportHeight / zoomLevel
        return max(0f, min(maxY, y))
    }

    fun reset() {
        posX = 0f
        posY = 0f
        zoomLevel = 1f
        targetX = null
        targetY = null
        shakeIntensity = 0f
        shakeOffsetX = 0f
        shakeOffsetY = 0f
        camera.zoom = 1f
        updateCamera()
    }
}
