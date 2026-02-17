package com.pixeloffice.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated lava lamp decoration for the office.
 * Renders rising/falling blobs in a lamp shape.
 */
class LavaLamp(
    private val x: Float,
    private val y: Float
) {
    // Lamp dimensions (pixel art scale)
    private val lampWidth = 6f
    private val lampHeight = 12f

    // Blob animation
    private data class Blob(var yOffset: Float, val size: Float, val speed: Float, val phase: Float)
    private val blobs: List<Blob>
    private var time = 0f

    // Colors - warm orange/red lava lamp palette
    private val glassColor = Color(0.2f, 0.15f, 0.25f, 0.6f)
    private val lavaColor = Color(1f, 0.4f, 0.1f, 1f)
    private val glowColor = Color(1f, 0.5f, 0.2f, 1f)

    init {
        val rng = Random(42)
        blobs = List(4) {
            Blob(
                yOffset = rng.nextFloat() * lampHeight,
                size = rng.nextFloat() * 1.5f + 1f,
                speed = rng.nextFloat() * 0.3f + 0.2f,
                phase = rng.nextFloat() * 6.28f
            )
        }
    }

    fun update(dt: Float) {
        time += dt
        for (blob in blobs) {
            // Oscillate blobs up and down within lamp
            blob.yOffset = (sin(time * blob.speed + blob.phase) * 0.5f + 0.5f) * (lampHeight - blob.size * 2)
        }
    }

    fun draw(batch: SpriteBatch, pixelRegion: TextureRegion, flipY: (Float) -> Float) {
        val screenY = flipY(y)

        // Draw lamp base (dark)
        batch.color = Color(0.3f, 0.25f, 0.2f, 1f)
        batch.draw(pixelRegion, x - 1f, screenY - 2f, lampWidth + 2f, 2f)

        // Draw lamp glass outline
        batch.color = glassColor
        batch.draw(pixelRegion, x, screenY, lampWidth, lampHeight)

        // Draw animated blobs
        batch.color = lavaColor
        for (blob in blobs) {
            val blobY = screenY + blob.yOffset + 1f
            val blobX = x + lampWidth / 2f - blob.size / 2f + sin(time * 0.5f + blob.phase) * 0.5f
            batch.draw(pixelRegion, blobX, blobY, blob.size, blob.size)
        }

        // Draw lamp cap (dark)
        batch.color = Color(0.3f, 0.25f, 0.2f, 1f)
        batch.draw(pixelRegion, x - 1f, screenY + lampHeight, lampWidth + 2f, 2f)

        batch.color = Color.WHITE
    }

    fun drawNightGlow(batch: SpriteBatch, glowRegion: TextureRegion, flipY: (Float) -> Float) {
        val screenY = flipY(y)
        val glowSize = 24f

        batch.color = Color(glowColor.r, glowColor.g, glowColor.b, 0.15f)
        batch.draw(
            glowRegion,
            x + lampWidth / 2f - glowSize / 2f,
            screenY + lampHeight / 2f - glowSize / 2f,
            glowSize,
            glowSize
        )
        batch.color = Color.WHITE
    }
}
