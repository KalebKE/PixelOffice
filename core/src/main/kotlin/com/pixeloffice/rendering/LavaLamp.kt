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
class LavaLamp(private val y: Float) {
    private data class PaletteColors(
        val base: Color,
        val glass: Color,
        val lava: Color,
        val glow: Color
    )

    // Lamp dimensions (pixel art scale)
    private val lampWidth = 6f
    private val lampHeight = 12f

    // Blob animation
    private data class Blob(var yOffset: Float, val size: Float, val speed: Float, val phase: Float)
    private val blobs: List<Blob>
    private var time = 0f

    private val sunsetColors = PaletteColors(
        base = Color(0.3f, 0.2f, 0.12f, 1f),
        glass = Color(0.2f, 0.15f, 0.25f, 0.6f),
        lava = Color(1f, 0.4f, 0.1f, 1f),
        glow = Color(1f, 0.5f, 0.2f, 1f)
    )
    private val forestColors = PaletteColors(
        base = Color(0.08f, 0.24f, 0.16f, 1f),
        glass = Color(0.06f, 0.16f, 0.18f, 0.65f),
        lava = Color(0.1f, 0.9f, 0.35f, 1f),
        glow = Color(0.2f, 1f, 0.45f, 1f)
    )
    private val oceanColors = PaletteColors(
        base = Color(0.08f, 0.18f, 0.35f, 1f),
        glass = Color(0.05f, 0.12f, 0.3f, 0.65f),
        lava = Color(0.1f, 0.7f, 1f, 1f),
        glow = Color(0.2f, 0.75f, 1f, 1f)
    )
    private val slateColors = PaletteColors(
        base = Color(0.25f, 0.18f, 0.32f, 1f),
        glass = Color(0.16f, 0.1f, 0.25f, 0.65f),
        lava = Color(0.9f, 0.25f, 0.75f, 1f),
        glow = Color(1f, 0.35f, 0.85f, 1f)
    )

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

    fun draw(
        batch: SpriteBatch,
        pixelRegion: TextureRegion,
        flipY: (Float) -> Float,
        x: Float,
        palette: OfficeAccentPalette
    ) {
        val screenY = flipY(y)
        val colors = colorsFor(palette)

        // Draw lamp base (dark)
        batch.color = colors.base
        batch.draw(pixelRegion, x - 1f, screenY - 2f, lampWidth + 2f, 2f)

        // Draw lamp glass outline
        batch.color = colors.glass
        batch.draw(pixelRegion, x, screenY, lampWidth, lampHeight)

        // Draw animated blobs
        batch.color = colors.lava
        for (blob in blobs) {
            val blobY = screenY + blob.yOffset + 1f
            val blobX = x + lampWidth / 2f - blob.size / 2f + sin(time * 0.5f + blob.phase) * 0.5f
            batch.draw(pixelRegion, blobX, blobY, blob.size, blob.size)
        }

        // Draw lamp cap (dark)
        batch.color = colors.base
        batch.draw(pixelRegion, x - 1f, screenY + lampHeight, lampWidth + 2f, 2f)

        batch.color = Color.WHITE
    }

    fun drawNightGlow(
        batch: SpriteBatch,
        glowRegion: TextureRegion,
        flipY: (Float) -> Float,
        x: Float,
        palette: OfficeAccentPalette
    ) {
        val screenY = flipY(y)
        val glowSize = 24f
        val glowColor = colorsFor(palette).glow

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

    private fun colorsFor(palette: OfficeAccentPalette): PaletteColors = when (palette) {
        OfficeAccentPalette.SUNSET -> sunsetColors
        OfficeAccentPalette.FOREST -> forestColors
        OfficeAccentPalette.OCEAN -> oceanColors
        OfficeAccentPalette.SLATE -> slateColors
    }
}
