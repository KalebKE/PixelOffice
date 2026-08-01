package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.pixeloffice.core.UfoConfig
import kotlin.math.sin
import kotlin.random.Random

/**
 * Processed sprite: day and night texture + region pairs with dimensions.
 */
private class ProcessedSprite(
    val dayTexture: Texture,
    val nightTexture: Texture,
    val dayRegion: TextureRegion,
    val nightRegion: TextureRegion,
    val width: Int,
    val height: Int
)

/**
 * An active flying object (paper airplane) currently crossing the sky.
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
 * UFO flight phase state machine.
 */
enum class UfoPhase { FLYING, HOVERING, BEAMING }

/**
 * An active UFO currently crossing the sky.
 */
class ActiveUfo(
    var x: Float,
    val baseY: Float,
    val speed: Float,
    val flipX: Boolean,
    var time: Float = 0f,
    var phase: UfoPhase = UfoPhase.FLYING,
    var phaseTimer: Float = 0f,
    var hasHovered: Boolean = false,
    var hasBeamed: Boolean = false,
    val hoverAtX: Float,
    val beamAtX: Float,
    var beamProgress: Float = 0f
)

/**
 * Self-contained manager for flying objects in the sky strip.
 *
 * Loads animated sprite frames from a directory, auto-crops to content bounds,
 * downscales to sky-strip size, and generates silhouette variants for night.
 * Handles spawn timing, update loop, and rendering.
 * Flying objects cross the sky horizontally with sine-wave vertical bob.
 *
 * Also manages UFOs which fly, hover, and fire tractor beams to abduct cows.
 */
class SkyTraffic(
    private var screenWidth: Int,
    private val skyHeight: Int,
    spriteDir: String = "sprites/32bit-PaperAirplane",
    frameCount: Int = 4,
    private val ufoConfig: UfoConfig = UfoConfig()
) {
    var spawnInterval: Float = 30f
    var enabled: Boolean = true

    private val active = mutableListOf<ActiveFlyingObject>()
    private val activeUfos = mutableListOf<ActiveUfo>()
    private var spawnTimer: Float = 0f
    private var ufoSpawnTimer: Float = 0f
    private val ufoSpawnInterval: Float = 60f
    private val ufoSpawnChance: Float = 0.1f
    private val rng = Random(System.currentTimeMillis())

    // Paper airplane textures
    private val dayTextures: List<Texture>
    private val nightTextures: List<Texture>
    private val dayRegions: List<TextureRegion>
    private val nightRegions: List<TextureRegion>
    private val spriteWidth: Int
    private val spriteHeight: Int

    // UFO textures
    private val ufoSprites = mutableListOf<ProcessedSprite>() // idle, beam1, beam2
    private var beamSprite: ProcessedSprite? = null
    private var cowSprite: ProcessedSprite? = null
    private val ufoTextures = mutableListOf<Texture>() // all UFO textures for disposal

    // Animation
    private val frameDuration = 0.2f // 5 FPS

    // Paper airplane movement constants
    private val speedRange = 18f..30f
    private val bobAmplitude = 1.5f
    private val bobSpeed = 1.2f
    private val yRange = 4f..22f

    // UFO movement constants
    private val ufoSpeedRange = 12f..20f
    private val ufoYRange = 6f..18f
    private val ufoBobAmplitude = 1.0f
    private val ufoBobSpeed = 0.8f
    private val ufoBeamFrameDuration = 0.3f

    private val silhouetteColor = Color(0.05f, 0.05f, 0.1f, 0.9f)

    fun setRenderWidth(width: Int) {
        screenWidth = width.coerceAtLeast(1)
    }

    init {
        // Load paper airplane sprites using multi-frame numbered format
        val fullPixmaps = (0 until frameCount).map { i ->
            val path = "%s/sprite_%02d.png".format(spriteDir, i)
            Pixmap(Gdx.files.internal(path))
        }

        // Compute union content bounding box across all frames
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
        val targetHeight = 9
        val targetWidth = Math.round(cropW.toFloat() * targetHeight / cropH)

        val dayTexturesMut = mutableListOf<Texture>()
        val nightTexturesMut = mutableListOf<Texture>()
        val dayRegionsMut = mutableListOf<TextureRegion>()
        val nightRegionsMut = mutableListOf<TextureRegion>()

        val silhouetteRgba8888 = Color.rgba8888(silhouetteColor)

        for (pm in fullPixmaps) {
            val cropped = Pixmap(cropW, cropH, Pixmap.Format.RGBA8888)
            cropped.drawPixmap(pm, unionMinX, unionMinY, cropW, cropH, 0, 0, cropW, cropH)

            val scaled = Pixmap(targetWidth, targetHeight, Pixmap.Format.RGBA8888)
            scaled.filter = Pixmap.Filter.NearestNeighbour
            scaled.drawPixmap(cropped, 0, 0, cropW, cropH, 0, 0, targetWidth, targetHeight)
            cropped.dispose()

            val dayTex = Texture(scaled).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
            dayTexturesMut.add(dayTex)
            dayRegionsMut.add(TextureRegion(dayTex))

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

            scaled.dispose()
            nightPixmap.dispose()
        }

        for (pm in fullPixmaps) pm.dispose()

        dayTextures = dayTexturesMut
        nightTextures = nightTexturesMut
        dayRegions = dayRegionsMut
        nightRegions = nightRegionsMut
        spriteWidth = targetWidth
        spriteHeight = targetHeight

        // Load UFO sprites
        if (ufoConfig.enabled) {
            loadUfoSprites(silhouetteRgba8888)
        }

        // Start with a random partial timer so the first spawn isn't always at exactly spawnInterval
        spawnTimer = rng.nextFloat() * spawnInterval * 0.5f
        ufoSpawnTimer = rng.nextFloat() * ufoSpawnInterval * 0.5f
    }

    /**
     * Load and process a single sprite file: auto-crop, downscale, generate night silhouette.
     */
    private fun loadAndProcessSprite(path: String, targetHeight: Int, silhouetteRgba: Int): ProcessedSprite {
        val pixmap = Pixmap(Gdx.files.internal(path))

        // Find content bounds
        var minX = pixmap.width
        var minY = pixmap.height
        var maxX = 0
        var maxY = 0
        for (y in 0 until pixmap.height) {
            for (x in 0 until pixmap.width) {
                val alpha = pixmap.getPixel(x, y) and 0xFF
                if (alpha > 0) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        val cropW = (maxX - minX + 1).coerceAtLeast(1)
        val cropH = (maxY - minY + 1).coerceAtLeast(1)
        val targetW = Math.round(cropW.toFloat() * targetHeight / cropH).coerceAtLeast(1)

        // Crop
        val cropped = Pixmap(cropW, cropH, Pixmap.Format.RGBA8888)
        cropped.drawPixmap(pixmap, minX, minY, cropW, cropH, 0, 0, cropW, cropH)
        pixmap.dispose()

        // Downscale
        val scaled = Pixmap(targetW, targetHeight, Pixmap.Format.RGBA8888)
        scaled.filter = Pixmap.Filter.NearestNeighbour
        scaled.drawPixmap(cropped, 0, 0, cropW, cropH, 0, 0, targetW, targetHeight)
        cropped.dispose()

        // Day texture
        val dayTex = Texture(scaled).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }

        // Night silhouette
        val nightPm = Pixmap(targetW, targetHeight, Pixmap.Format.RGBA8888)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetW) {
                val alpha = scaled.getPixel(x, y) and 0xFF
                if (alpha > 0) {
                    nightPm.drawPixel(x, y, silhouetteRgba)
                }
            }
        }
        val nightTex = Texture(nightPm).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }

        scaled.dispose()
        nightPm.dispose()

        return ProcessedSprite(dayTex, nightTex, TextureRegion(dayTex), TextureRegion(nightTex), targetW, targetHeight)
    }

    private fun loadUfoSprites(silhouetteRgba: Int) {
        val ufoTargetHeight = 14
        val beamTargetHeight = 24
        val cowTargetHeight = 6

        // UFO body: idle, beam1, beam2
        val ufoPaths = listOf(
            "sprites/ufo/ufo/animation/ufo_idle.png",
            "sprites/ufo/ufo/animation/ufo_beam1.png",
            "sprites/ufo/ufo/animation/ufo_beam2.png"
        )
        for (path in ufoPaths) {
            val sprite = loadAndProcessSprite(path, ufoTargetHeight, silhouetteRgba)
            ufoSprites.add(sprite)
            ufoTextures.add(sprite.dayTexture)
            ufoTextures.add(sprite.nightTexture)
        }

        // Tractor beam
        val beam = loadAndProcessSprite("sprites/ufo/tractorbeam/tractorbeam.png", beamTargetHeight, silhouetteRgba)
        beamSprite = beam
        ufoTextures.add(beam.dayTexture)
        ufoTextures.add(beam.nightTexture)

        // Cow
        val cow = loadAndProcessSprite("sprites/ufo/cow/cow.png", cowTargetHeight, silhouetteRgba)
        cowSprite = cow
        ufoTextures.add(cow.dayTexture)
        ufoTextures.add(cow.nightTexture)
    }

    fun update(dt: Float) {
        if (!enabled) return

        // Update paper airplanes
        val iter = active.iterator()
        while (iter.hasNext()) {
            val obj = iter.next()
            obj.time += dt
            obj.x += obj.speed * dt

            if (obj.speed > 0 && obj.x > screenWidth + obj.width) {
                iter.remove()
            } else if (obj.speed < 0 && obj.x < -obj.width.toFloat()) {
                iter.remove()
            }
        }

        // Update UFOs
        updateUfos(dt)

        // Paper airplane spawn timer
        spawnTimer += dt
        if (spawnTimer >= spawnInterval) {
            spawnPlane()
            spawnTimer = rng.nextFloat() * spawnInterval * 0.15f
        }

        // UFO spawn timer (independent, 10% chance every 60s)
        if (ufoConfig.enabled && ufoSprites.isNotEmpty()) {
            ufoSpawnTimer += dt
            if (ufoSpawnTimer >= ufoSpawnInterval) {
                if (rng.nextFloat() < ufoSpawnChance) {
                    spawnUfo()
                }
                ufoSpawnTimer = rng.nextFloat() * ufoSpawnInterval * 0.15f
            }
        }
    }

    private fun updateUfos(dt: Float) {
        val ufoWidth = ufoSprites.firstOrNull()?.width ?: return

        val iter = activeUfos.iterator()
        while (iter.hasNext()) {
            val ufo = iter.next()
            ufo.time += dt
            ufo.phaseTimer += dt

            when (ufo.phase) {
                UfoPhase.FLYING -> {
                    ufo.x += ufo.speed * dt

                    // Check if we should start hovering
                    if (!ufo.hasHovered) {
                        val reachedHover = if (ufo.speed > 0) ufo.x >= ufo.hoverAtX else ufo.x <= ufo.hoverAtX
                        if (reachedHover) {
                            ufo.phase = UfoPhase.HOVERING
                            ufo.phaseTimer = 0f
                        }
                    }

                    // Check if we should start beaming (only after hover is done)
                    if (!ufo.hasBeamed && ufo.hasHovered) {
                        val reachedBeam = if (ufo.speed > 0) ufo.x >= ufo.beamAtX else ufo.x <= ufo.beamAtX
                        if (reachedBeam) {
                            ufo.phase = UfoPhase.BEAMING
                            ufo.phaseTimer = 0f
                            ufo.beamProgress = 0f
                        }
                    }
                }
                UfoPhase.HOVERING -> {
                    // Stay still
                    if (ufo.phaseTimer >= ufoConfig.hoverDuration) {
                        ufo.phase = UfoPhase.FLYING
                        ufo.hasHovered = true
                        ufo.phaseTimer = 0f
                    }
                }
                UfoPhase.BEAMING -> {
                    // Stay still, animate beam
                    ufo.beamProgress = (ufo.phaseTimer / ufoConfig.beamDuration).coerceIn(0f, 1f)
                    if (ufo.phaseTimer >= ufoConfig.beamDuration) {
                        ufo.phase = UfoPhase.FLYING
                        ufo.hasBeamed = true
                        ufo.phaseTimer = 0f
                    }
                }
            }

            // Remove when off-screen
            if (ufo.speed > 0 && ufo.x > screenWidth + ufoWidth) {
                iter.remove()
            } else if (ufo.speed < 0 && ufo.x < -ufoWidth.toFloat()) {
                iter.remove()
            }
        }
    }

    private fun spawnPlane() {
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

    fun forceSpawnUfo() {
        if (ufoSprites.isNotEmpty()) spawnUfo()
    }

    private fun spawnUfo() {
        val goingRight = rng.nextBoolean()
        val speed = rng.nextFloat() * (ufoSpeedRange.endInclusive - ufoSpeedRange.start) + ufoSpeedRange.start
        val finalSpeed = if (goingRight) speed else -speed
        val baseY = rng.nextFloat() * (ufoYRange.endInclusive - ufoYRange.start) + ufoYRange.start
        val startX = if (goingRight) -(ufoSprites[0].width.toFloat()) else screenWidth.toFloat() + ufoSprites[0].width

        // Pick hover and beam X positions within the middle portion of the screen
        val margin = screenWidth * 0.2f
        val midStart = margin
        val midEnd = screenWidth - margin

        val hoverAtX = rng.nextFloat() * (midEnd - midStart) * 0.5f + midStart
        // Beam point is further along than hover point
        val beamAtX = if (goingRight) {
            hoverAtX + rng.nextFloat() * (midEnd - hoverAtX).coerceAtLeast(10f) * 0.6f + 20f
        } else {
            hoverAtX - rng.nextFloat() * (hoverAtX - midStart).coerceAtLeast(10f) * 0.6f - 20f
        }

        activeUfos.add(ActiveUfo(
            x = startX,
            baseY = baseY,
            speed = finalSpeed,
            flipX = !goingRight,
            hoverAtX = hoverAtX,
            beamAtX = beamAtX
        ))
    }

    fun draw(batch: SpriteBatch, nightness: Float, screenHeight: Int) {
        if (!enabled) return

        val baseScreenY = screenHeight - skyHeight

        // Draw paper airplanes
        for (obj in active) {
            val bobOffset = sin(obj.time * bobSpeed) * bobAmplitude
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

        // Draw UFOs
        drawUfos(batch, nightness, screenHeight)
    }

    private fun drawUfos(batch: SpriteBatch, nightness: Float, screenHeight: Int) {
        if (ufoSprites.isEmpty()) return
        val beam = beamSprite ?: return
        val cow = cowSprite ?: return
        val isNight = nightness > 0.5f

        val baseScreenY = screenHeight - skyHeight

        for (ufo in activeUfos) {
            val isNightBeaming = isNight && ufo.phase == UfoPhase.BEAMING
            val ufoIdle = ufoSprites[0]
            val ufoW = ufoIdle.width.toFloat()
            val ufoH = ufoIdle.height.toFloat()

            // Vertical bob only during FLYING
            val bobOffset = if (ufo.phase == UfoPhase.FLYING) {
                sin(ufo.time * ufoBobSpeed) * ufoBobAmplitude
            } else {
                0f
            }
            val screenY = baseScreenY + (skyHeight - ufo.baseY - bobOffset - ufoH).toFloat()

            // Pick UFO frame based on phase
            val ufoRegion = when (ufo.phase) {
                UfoPhase.FLYING, UfoPhase.HOVERING -> {
                    if (isNight) ufoIdle.nightRegion else ufoIdle.dayRegion
                }
                UfoPhase.BEAMING -> {
                    // Alternate beam1/beam2
                    val beamFrameIdx = ((ufo.phaseTimer / ufoBeamFrameDuration).toInt() % 2) + 1
                    val sprite = ufoSprites[beamFrameIdx]
                    if (isNight && !isNightBeaming) sprite.nightRegion else sprite.dayRegion
                }
            }

            // Draw UFO body
            if (ufo.flipX) {
                batch.draw(ufoRegion, ufo.x + ufoW, screenY, -ufoW, ufoH)
            } else {
                batch.draw(ufoRegion, ufo.x, screenY, ufoW, ufoH)
            }

            // Draw beam and cow during BEAMING phase
            if (ufo.phase == UfoPhase.BEAMING) {
                val p = ufo.beamProgress // 0..1

                // Beam alpha and length: grows 0→0.3, full 0.3→0.7, retracts 0.7→1.0
                val beamAlpha: Float
                val beamLengthFraction: Float
                when {
                    p < 0.3f -> {
                        val t = p / 0.3f
                        beamAlpha = t * 0.7f
                        beamLengthFraction = t
                    }
                    p < 0.7f -> {
                        beamAlpha = 0.7f
                        beamLengthFraction = 1f
                    }
                    else -> {
                        val t = (p - 0.7f) / 0.3f
                        beamAlpha = (1f - t) * 0.7f
                        beamLengthFraction = 1f - t
                    }
                }

                val beamW = beam.width.toFloat()
                val fullBeamH = beam.height.toFloat()
                val beamH = fullBeamH * beamLengthFraction

                // Center beam under UFO
                val beamX = ufo.x + (ufoW - beamW) / 2f
                val beamTopY = screenY // bottom of UFO sprite
                val beamDrawY = beamTopY - beamH

                val prevColor = batch.color.cpy()
                if (isNightBeaming) {
                    batch.setColor(0.3f, 1f, 0.4f, beamAlpha)
                } else {
                    batch.setColor(1f, 1f, 1f, beamAlpha)
                }
                val beamRegion = beam.dayRegion
                batch.draw(beamRegion, beamX, beamDrawY, beamW, beamH)
                batch.color = prevColor

                // Cow: appears at p=0.2, rises to UFO bottom at p=0.8, disappears
                if (p in 0.2f..0.8f) {
                    val cowW = cow.width.toFloat()
                    val cowH = cow.height.toFloat()
                    val cowT = ((p - 0.2f) / 0.6f).coerceIn(0f, 1f)
                    // Cow rises from bottom of beam to just under UFO
                    val cowBottomY = beamDrawY
                    val cowTopY = beamTopY - cowH
                    val cowY = cowBottomY + (cowTopY - cowBottomY) * cowT
                    val cowX = ufo.x + (ufoW - cowW) / 2f

                    val cowRegion = if (isNight && !isNightBeaming) cow.nightRegion else cow.dayRegion
                    batch.draw(cowRegion, cowX, cowY, cowW, cowH)
                }
            }
        }
    }

    fun dispose() {
        active.clear()
        activeUfos.clear()
        for (tex in dayTextures) tex.dispose()
        for (tex in nightTextures) tex.dispose()
        for (tex in ufoTextures) tex.dispose()
    }
}
