package com.pixeloffice.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.pixeloffice.core.UfoConfig
import java.util.Calendar
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural sky system that reflects wall-clock time.
 *
 * Renders a 38px-tall sky strip with:
 * - Time-of-day gradient (ShapeRenderer filled rects)
 * - Floating clouds (ShapeRenderer circles)
 * - Twinkling stars at night (SpriteBatch 1×1 pixel)
 */
class SkyRenderer(
    private val screenWidth: Int,
    private val screenHeight: Int,
    skyTrafficInterval: Float = 30f,
    skyTrafficEnabled: Boolean = true,
    skyTrafficSprite: String = "sprites/32bit-PaperAirplane",
    skyTrafficFrameCount: Int = 4,
    ufoConfig: UfoConfig = UfoConfig()
) {
    // Sky strip height in pixels
    private val skyHeight = 38

    // Animation time accumulator
    private var time = 0f

    // Cached hour fraction, updated each frame
    private var hourFraction = 12f

    // Current interpolated top/bottom colors
    private val currentTopColor = Color()
    private val currentBottomColor = Color()

    // ── Color keyframes ──
    // Each entry: hourFraction -> Pair(topColor, bottomColor)
    private data class SkyKeyframe(val hour: Float, val top: Color, val bottom: Color)

    private val keyframes = listOf(
        SkyKeyframe(5.0f,  Color(0.06f, 0.06f, 0.18f, 1f), Color(0.10f, 0.10f, 0.28f, 1f)),  // Pre-dawn
        SkyKeyframe(6.0f,  Color(0.30f, 0.10f, 0.30f, 1f), Color(0.85f, 0.45f, 0.55f, 1f)),  // Early sunrise
        SkyKeyframe(7.0f,  Color(0.90f, 0.55f, 0.25f, 1f), Color(1.00f, 0.80f, 0.60f, 1f)),  // Sunrise peak
        SkyKeyframe(8.0f,  Color(0.16f, 0.68f, 1.00f, 1f), Color(0.53f, 0.81f, 0.98f, 1f)),  // Morning → Day
        SkyKeyframe(17.0f, Color(0.16f, 0.68f, 1.00f, 1f), Color(0.53f, 0.81f, 0.98f, 1f)),  // Late afternoon
        SkyKeyframe(19.0f, Color(0.85f, 0.45f, 0.55f, 1f), Color(0.90f, 0.55f, 0.25f, 1f)),  // Sunset
        SkyKeyframe(20.0f, Color(0.30f, 0.10f, 0.30f, 1f), Color(0.10f, 0.10f, 0.28f, 1f)),  // Dusk
        SkyKeyframe(21.0f, Color(0.04f, 0.04f, 0.12f, 1f), Color(0.06f, 0.06f, 0.18f, 1f))   // Night
    )

    // ── Cloud data ──
    private data class CloudPuff(val offsetX: Float, val offsetY: Float, val radius: Float)
    private data class Cloud(var x: Float, val y: Float, val speed: Float, val puffs: List<CloudPuff>)

    private val clouds: MutableList<Cloud>
    private val cloudColor = Color()

    // ── Star data ──
    private data class Star(val x: Float, val y: Float, val brightness: Float, val twinkleSpeed: Float, val phase: Float)

    private val stars: List<Star>

    // When set, overrides wall-clock time for sky colors/stars/nightness
    var overrideHourFraction: Float? = null

    // Flying objects
    private val skyTraffic = SkyTraffic(screenWidth, skyHeight, skyTrafficSprite, skyTrafficFrameCount, ufoConfig).apply {
        spawnInterval = skyTrafficInterval
        enabled = skyTrafficEnabled
    }

    // Temp color for lerp operations
    private val tmpColor = Color()

    init {
        val rng = Random(42) // seeded for reproducible layout

        // Generate clouds
        clouds = mutableListOf()
        val cloudCount = rng.nextInt(4, 9)
        for (i in 0 until cloudCount) {
            val puffCount = rng.nextInt(3, 8)
            val puffs = mutableListOf<CloudPuff>()
            var px = 0f
            for (j in 0 until puffCount) {
                val radius = rng.nextFloat() * 4f + 2f // 2-6px
                puffs.add(CloudPuff(px, rng.nextFloat() * 4f - 2f, radius))
                px += radius * 0.8f + rng.nextFloat() * 2f
            }
            clouds.add(Cloud(
                x = rng.nextFloat() * screenWidth,
                y = rng.nextFloat() * 22f + 6f, // 6-28 within the 38px strip
                speed = rng.nextFloat() * 2.5f + 0.5f, // 0.5-3.0 px/sec
                puffs = puffs
            ))
        }

        // Generate stars
        val starCount = rng.nextInt(20, 36)
        stars = List(starCount) {
            Star(
                x = rng.nextFloat() * screenWidth,
                y = rng.nextFloat() * 34f + 2f, // 2-36 within the 38px strip
                brightness = rng.nextFloat() * 0.5f + 0.5f, // 0.5-1.0
                twinkleSpeed = rng.nextFloat() * 3f + 1f, // 1-4 Hz
                phase = rng.nextFloat() * 6.28f
            )
        }
    }

    fun update(dt: Float) {
        time += dt

        // Update hour fraction from wall clock
        val cal = Calendar.getInstance()
        hourFraction = overrideHourFraction ?: (cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f)

        // Interpolate sky colors
        interpolateSkyColors()

        // Update flying objects
        skyTraffic.update(dt)

        // Update cloud positions
        for (cloud in clouds) {
            cloud.x += cloud.speed * dt
            // Compute cloud's total width from puffs
            val totalWidth = cloud.puffs.lastOrNull()?.let { it.offsetX + it.radius * 2 } ?: 20f
            if (cloud.x > screenWidth + totalWidth) {
                cloud.x = -totalWidth
            }
        }
    }

    /**
     * Interpolate top/bottom sky colors based on current hourFraction.
     * Handles the midnight wrap (21:00 → 5:00).
     */
    private fun interpolateSkyColors() {
        val h = hourFraction

        // Find surrounding keyframes
        // Night wraps: after 21:00, interpolate toward 5:00 (next day)
        if (h >= keyframes.last().hour || h < keyframes.first().hour) {
            // Night period: 21:00 → 5:00
            val nightStart = keyframes.last()   // 21:00
            val nightEnd = keyframes.first()     // 5:00

            // Normalize position within the night span (8 hours)
            val nightDuration = 24f - nightStart.hour + nightEnd.hour // 8 hours
            val elapsed = if (h >= nightStart.hour) h - nightStart.hour else h + 24f - nightStart.hour
            val t = (elapsed / nightDuration).coerceIn(0f, 1f)

            currentTopColor.set(nightStart.top).lerp(nightEnd.top, t)
            currentBottomColor.set(nightStart.bottom).lerp(nightEnd.bottom, t)
        } else {
            // Daytime: find two surrounding keyframes and lerp
            var lower = keyframes.first()
            var upper = keyframes.last()
            for (i in 0 until keyframes.size - 1) {
                if (h >= keyframes[i].hour && h < keyframes[i + 1].hour) {
                    lower = keyframes[i]
                    upper = keyframes[i + 1]
                    break
                }
            }
            val span = upper.hour - lower.hour
            val t = if (span > 0f) ((h - lower.hour) / span).coerceIn(0f, 1f) else 0f

            currentTopColor.set(lower.top).lerp(upper.top, t)
            currentBottomColor.set(lower.bottom).lerp(upper.bottom, t)
        }
    }

    /**
     * Draw the sky gradient — 38 horizontal 1px-tall filled rects.
     * Must be called between beginShapes() / endShapes() or manages its own.
     */
    fun drawGradient(shapeRenderer: ShapeRenderer) {
        shapeRenderer.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        val baseY = screenHeight - skyHeight // bottom of sky strip in screen coords (Y-up)

        for (row in 0 until skyHeight) {
            val t = row.toFloat() / (skyHeight - 1).toFloat()
            // row 0 = bottom of strip (closest to building), row 37 = top of screen
            // bottom color at row 0, top color at row 37
            tmpColor.set(currentBottomColor).lerp(currentTopColor, t)
            shapeRenderer.color = tmpColor
            shapeRenderer.rect(0f, baseY + row.toFloat(), screenWidth.toFloat(), 1f)
        }

        shapeRenderer.end()
    }

    /**
     * Draw clouds as clusters of filled circles.
     */
    fun drawClouds(shapeRenderer: ShapeRenderer) {
        // Compute cloud color: white during day, dim gray at night
        val dayColor = Color(1f, 1f, 1f, 0.7f)
        val nightCloudColor = Color(0.25f, 0.25f, 0.35f, 0.4f)

        val nightness = getNightness()
        cloudColor.set(dayColor).lerp(nightCloudColor, nightness)

        val baseY = screenHeight - skyHeight

        shapeRenderer.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = cloudColor

        for (cloud in clouds) {
            for (puff in cloud.puffs) {
                val cx = cloud.x + puff.offsetX
                val cy = baseY + (skyHeight - cloud.y - puff.offsetY) // flip within strip
                drawFilledCircle(shapeRenderer, cx, cy, puff.radius)
            }
        }

        shapeRenderer.end()
    }

    /**
     * Draw twinkling stars using SpriteBatch and a 1×1 pixel texture.
     * Call within an active SpriteBatch.
     */
    fun drawStars(batch: SpriteBatch, pixelRegion: TextureRegion) {
        val visibility = getStarVisibility()
        if (visibility <= 0f) return

        val baseY = screenHeight - skyHeight

        for (star in stars) {
            val alpha = star.brightness * (0.5f + 0.5f * sin(time * star.twinkleSpeed + star.phase)) * visibility
            if (alpha < 0.02f) continue
            batch.setColor(1f, 1f, 1f, alpha)
            batch.draw(pixelRegion, star.x, baseY + (skyHeight - star.y), 1f, 1f)
        }

        // Reset batch color
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /**
     * Get the current top color of the sky gradient (for clear color).
     */
    fun getCurrentTopColor(): Color = currentTopColor

    /**
     * Whether it's currently "night" (for office dimming overlay).
     * Roughly 20:00 – 6:00.
     */
    fun isNightTime(): Boolean {
        return hourFraction >= 20f || hourFraction < 6f
    }

    /**
     * Draw flying objects (planes, etc.) in the sky strip.
     * Call within an active SpriteBatch.
     */
    fun drawFlyingObjects(batch: SpriteBatch) {
        skyTraffic.draw(batch, getNightness(), screenHeight)
    }

    fun forceSpawnUfo() {
        skyTraffic.forceSpawnUfo()
    }

    /**
     * Smooth nightness factor (0 = full day, 1 = full night).
     * Used for cloud/star/flying-object color interpolation.
     */
    internal fun getNightness(): Float {
        val h = hourFraction
        return when {
            h in 8f..17f -> 0f           // Full day
            h in 21f..24f || h < 5f -> 1f // Full night
            h in 17f..21f -> (h - 17f) / 4f  // Dusk transition
            h in 5f..8f -> 1f - (h - 5f) / 3f // Dawn transition
            else -> 0f
        }
    }

    /**
     * Star visibility: 0 during day, fades in during dusk (19-21), full at night, fades out at dawn (5-7).
     */
    private fun getStarVisibility(): Float {
        val h = hourFraction
        return when {
            h in 7f..19f -> 0f            // Day: invisible
            h in 21f..24f || h < 5f -> 1f  // Full night
            h in 19f..21f -> (h - 19f) / 2f // Fade in at dusk
            h in 5f..7f -> 1f - (h - 5f) / 2f // Fade out at dawn
            else -> 0f
        }
    }

    /**
     * Draw a filled circle using triangles (ShapeRenderer doesn't have a built-in filled circle
     * at pixel scale, so we approximate with a simple approach).
     */
    private fun drawFilledCircle(sr: ShapeRenderer, cx: Float, cy: Float, radius: Float) {
        // Use ShapeRenderer's built-in circle with enough segments for pixel art
        val segments = (radius * 4).toInt().coerceIn(6, 16)
        sr.circle(cx, cy, radius, segments)
    }

    fun dispose() {
        skyTraffic.dispose()
    }
}
