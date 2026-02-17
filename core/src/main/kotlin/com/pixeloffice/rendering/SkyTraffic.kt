package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.sin
import kotlin.random.Random

/**
 * An active flying object currently crossing the sky.
 */
class ActiveFlyingObject(
    var x: Float,
    val baseY: Float,
    val speed: Float,
    val dayRegions: List<TextureRegion>,
    val nightRegions: List<TextureRegion>,
    val width: Int,
    val height: Int,
    val flipX: Boolean,
    var time: Float = 0f
)

/**
 * Self-contained manager for flying objects in the sky strip.
 *
 * Loads animated sprite frames from a directory, auto-crops to content bounds,
 * downscales to sky-strip size, and generates silhouette variants for night.
 * Handles spawn timing, update loop, and rendering.
 * Flying objects cross the sky horizontally with sine-wave vertical bob.
 */
class SkyTraffic(
    private val screenWidth: Int,
    private val skyHeight: Int,
    spriteDir: String = "sprites/32bit-PaperAirplane",
    frameCount: Int = 4
) {
    var spawnInterval: Float = 30f
    var enabled: Boolean = true

    private val active = mutableListOf<ActiveFlyingObject>()
    private var spawnTimer: Float = 0f
    private val rng = Random(System.currentTimeMillis())

    // Multi-frame textures loaded from sprite directory
    private val dayTextures: List<Texture>
    private val nightTextures: List<Texture>
    private val dayRegions: List<TextureRegion>
    private val nightRegions: List<TextureRegion>
    private val spriteWidth: Int
    private val spriteHeight: Int

    // Animation
    private val frameDuration = 0.2f // 5 FPS

    // Movement/animation constants
    private val speedRange = 18f..30f
    private val bobAmplitude = 1.5f
    private val bobSpeed = 1.2f
    private val yRange = 4f..22f

    private val silhouetteColor = Color(0.05f, 0.05f, 0.1f, 0.9f)

    init {
        // 1. Load all full pixmaps
        val fullPixmaps = (0 until frameCount).map { i ->
            val path = "%s/sprite_%02d.png".format(spriteDir, i)
            Pixmap(Gdx.files.internal(path))
        }

        // 2. Compute union content bounding box across all frames
        var unionMinX = Int.MAX_VALUE
        var unionMinY = Int.MAX_VALUE
        var unionMaxX = Int.MIN_VALUE
        var unionMaxY = Int.MIN_VALUE

        for (pm in fullPixmaps) {
            for (y in 0 until pm.height) {
                for (x in 0 until pm.width) {
                    val alpha = pm.getPixel(x, y) and 0xFF
                    if (alpha > 0) {
                        if (x < unionMinX) unionMinX = x
                        if (y < unionMinY) unionMinY = y
                        if (x > unionMaxX) unionMaxX = x
                        if (y > unionMaxY) unionMaxY = y
                    }
                }
            }
        }

        val cropW = unionMaxX - unionMinX + 1
        val cropH = unionMaxY - unionMinY + 1

        // 3. Crop and downscale each frame
        val targetHeight = 9
        val targetWidth = Math.round(cropW.toFloat() * targetHeight / cropH)

        val dayTexturesMut = mutableListOf<Texture>()
        val nightTexturesMut = mutableListOf<Texture>()
        val dayRegionsMut = mutableListOf<TextureRegion>()
        val nightRegionsMut = mutableListOf<TextureRegion>()

        val silhouetteRgba8888 = Color.rgba8888(silhouetteColor)

        for (pm in fullPixmaps) {
            // Crop to union bounds
            val cropped = Pixmap(cropW, cropH, Pixmap.Format.RGBA8888)
            cropped.drawPixmap(pm, unionMinX, unionMinY, cropW, cropH, 0, 0, cropW, cropH)

            // Downscale cropped result
            val scaled = Pixmap(targetWidth, targetHeight, Pixmap.Format.RGBA8888)
            scaled.filter = Pixmap.Filter.NearestNeighbour
            scaled.drawPixmap(cropped, 0, 0, cropW, cropH, 0, 0, targetWidth, targetHeight)
            cropped.dispose()

            // 4. Create day texture
            val dayTex = Texture(scaled).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
            dayTexturesMut.add(dayTex)
            dayRegionsMut.add(TextureRegion(dayTex))

            // 5. Create night silhouette texture
            val nightPixmap = Pixmap(targetWidth, targetHeight, Pixmap.Format.RGBA8888)
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val alpha = scaled.getPixel(x, y) and 0xFF
                    if (alpha > 0) {
                        nightPixmap.drawPixel(x, y, silhouetteRgba8888)
                    }
                }
            }
            val nightTex = Texture(nightPixmap).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
            nightTexturesMut.add(nightTex)
            nightRegionsMut.add(TextureRegion(nightTex))

            // 6. Dispose intermediate pixmaps
            scaled.dispose()
            nightPixmap.dispose()
        }

        // Dispose full pixmaps
        for (pm in fullPixmaps) pm.dispose()

        dayTextures = dayTexturesMut
        nightTextures = nightTexturesMut
        dayRegions = dayRegionsMut
        nightRegions = nightRegionsMut

        // 7. Set dimensions from first frame
        spriteWidth = targetWidth
        spriteHeight = targetHeight

        // Start with a random partial timer so the first spawn isn't always at exactly spawnInterval
        spawnTimer = rng.nextFloat() * spawnInterval * 0.5f
    }

    fun update(dt: Float) {
        if (!enabled) return

        // Update active objects
        val iter = active.iterator()
        while (iter.hasNext()) {
            val obj = iter.next()
            obj.time += dt
            obj.x += obj.speed * dt

            // Remove when fully off-screen
            if (obj.speed > 0 && obj.x > screenWidth + obj.width) {
                iter.remove()
            } else if (obj.speed < 0 && obj.x < -obj.width.toFloat()) {
                iter.remove()
            }
        }

        // Spawn timer
        spawnTimer += dt
        if (spawnTimer >= spawnInterval) {
            spawn()
            // Reset with small random jitter
            spawnTimer = rng.nextFloat() * spawnInterval * 0.15f
        }
    }

    private fun spawn() {
        val goingRight = rng.nextBoolean()
        val speed = rng.nextFloat() * (speedRange.endInclusive - speedRange.start) + speedRange.start

        val startX = if (goingRight) -spriteWidth.toFloat() else screenWidth.toFloat() + spriteWidth
        val finalSpeed = if (goingRight) speed else -speed
        val baseY = rng.nextFloat() * (yRange.endInclusive - yRange.start) + yRange.start

        active.add(ActiveFlyingObject(
            x = startX,
            baseY = baseY,
            speed = finalSpeed,
            dayRegions = dayRegions,
            nightRegions = nightRegions,
            width = spriteWidth,
            height = spriteHeight,
            flipX = !goingRight
        ))
    }

    fun draw(batch: SpriteBatch, nightness: Float, screenHeight: Int) {
        if (!enabled) return

        val baseScreenY = screenHeight - skyHeight

        for (obj in active) {
            val bobOffset = sin(obj.time * bobSpeed) * bobAmplitude
            // Y within sky strip, converted to screen coords (Y-up)
            val screenY = baseScreenY + (skyHeight - obj.baseY - bobOffset - obj.height).toFloat()

            val frameIndex = ((obj.time / frameDuration).toInt()) % dayRegions.size
            val region = if (nightness > 0.5f) obj.nightRegions[frameIndex] else obj.dayRegions[frameIndex]

            if (obj.flipX) {
                batch.draw(
                    region,
                    obj.x + obj.width, screenY,
                    -obj.width.toFloat(), obj.height.toFloat()
                )
            } else {
                batch.draw(region, obj.x, screenY, obj.width.toFloat(), obj.height.toFloat())
            }
        }
    }

    fun dispose() {
        active.clear()
        for (tex in dayTextures) tex.dispose()
        for (tex in nightTextures) tex.dispose()
    }
}
