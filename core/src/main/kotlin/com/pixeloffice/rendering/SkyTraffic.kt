package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.random.Random

/**
 * Manages flying objects (paper airplanes, birds, etc.) in the sky strip.
 */
class SkyTraffic(
    private val screenWidth: Int,
    private val skyHeight: Int,
    spritePath: String,
    private val frameCount: Int
) {
    var spawnInterval: Float = 30f
    var enabled: Boolean = true

    private var texture: Texture? = null
    private var frames: Array<TextureRegion> = emptyArray()
    private var timeSinceSpawn = 0f
    private var animTime = 0f
    private val frameDuration = 0.15f

    private data class FlyingObject(
        var x: Float,
        val y: Float,
        val speed: Float,
        val direction: Int, // 1 = right, -1 = left
        val scale: Float
    )

    private val objects = mutableListOf<FlyingObject>()
    private val rng = Random(System.currentTimeMillis())

    init {
        try {
            val file = Gdx.files.internal("$spritePath.png")
            if (file.exists()) {
                texture = Texture(file)
                val frameWidth = texture!!.width / frameCount
                val frameHeight = texture!!.height
                frames = Array(frameCount) { i ->
                    TextureRegion(texture, i * frameWidth, 0, frameWidth, frameHeight)
                }
            }
        } catch (e: Exception) {
            Gdx.app?.log("SkyTraffic", "Failed to load sprite: $spritePath - ${e.message}")
        }
    }

    fun update(dt: Float) {
        if (!enabled) return

        animTime += dt
        timeSinceSpawn += dt

        // Spawn new objects
        if (timeSinceSpawn >= spawnInterval && frames.isNotEmpty()) {
            spawnObject()
            timeSinceSpawn = 0f
        }

        // Update positions
        val iter = objects.iterator()
        while (iter.hasNext()) {
            val obj = iter.next()
            obj.x += obj.speed * obj.direction * dt

            // Remove if off screen
            val frameWidth = frames[0].regionWidth * obj.scale
            if (obj.direction > 0 && obj.x > screenWidth + frameWidth) {
                iter.remove()
            } else if (obj.direction < 0 && obj.x < -frameWidth) {
                iter.remove()
            }
        }
    }

    private fun spawnObject() {
        val direction = if (rng.nextBoolean()) 1 else -1
        val frameWidth = if (frames.isNotEmpty()) frames[0].regionWidth.toFloat() else 16f
        val scale = rng.nextFloat() * 0.5f + 0.75f // 0.75 - 1.25

        objects.add(FlyingObject(
            x = if (direction > 0) -frameWidth * scale else screenWidth + frameWidth * scale,
            y = rng.nextFloat() * (skyHeight - 10) + 5f,
            speed = rng.nextFloat() * 20f + 15f, // 15-35 px/sec
            direction = direction,
            scale = scale
        ))
    }

    fun draw(batch: SpriteBatch, nightness: Float, screenHeight: Int) {
        if (!enabled || frames.isEmpty()) return

        val baseY = screenHeight - skyHeight
        val currentFrame = ((animTime / frameDuration).toInt() % frameCount)
        val frame = frames[currentFrame]

        // Dim at night
        val brightness = 1f - nightness * 0.6f
        batch.color = Color(brightness, brightness, brightness, 1f)

        for (obj in objects) {
            val drawX = obj.x
            val drawY = baseY + obj.y
            val width = frame.regionWidth * obj.scale
            val height = frame.regionHeight * obj.scale

            // Flip horizontally if going left
            if (obj.direction < 0) {
                batch.draw(frame, drawX + width, drawY, -width, height)
            } else {
                batch.draw(frame, drawX, drawY, width, height)
            }
        }

        batch.color = Color.WHITE
    }

    fun dispose() {
        texture?.dispose()
        texture = null
        frames = emptyArray()
        objects.clear()
    }
}
