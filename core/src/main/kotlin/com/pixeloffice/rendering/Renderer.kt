package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.pixeloffice.PixelOfficeGame
import com.pixeloffice.animation.SpriteFrame
import com.pixeloffice.animation.SpriteSheet
import com.pixeloffice.core.WalkableZone
import com.pixeloffice.world.*
import java.util.Calendar
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Color palette matching Pyxel/PixelOfficeAssets.png
 */
object Colors {
    val BLACK = Color(0f, 0f, 0f, 1f)
    val DARK_BLUE = Color(0x1D / 255f, 0x2B / 255f, 0x53 / 255f, 1f)
    val DARK_PURPLE = Color(0x7E / 255f, 0x25 / 255f, 0x53 / 255f, 1f)
    val DARK_GREEN = Color(0x00 / 255f, 0x87 / 255f, 0x51 / 255f, 1f)
    val BROWN = Color(0xAB / 255f, 0x52 / 255f, 0x36 / 255f, 1f)
    val DARK_GRAY = Color(0x5F / 255f, 0x57 / 255f, 0x4F / 255f, 1f)
    val LIGHT_GRAY = Color(0xC2 / 255f, 0xC3 / 255f, 0xC7 / 255f, 1f)
    val WHITE = Color(0xFF / 255f, 0xF1 / 255f, 0xE8 / 255f, 1f)
    val RED = Color(0xFF / 255f, 0x00 / 255f, 0x4D / 255f, 1f)
    val ORANGE = Color(0xFF / 255f, 0xA3 / 255f, 0x00 / 255f, 1f)
    val YELLOW = Color(0xFF / 255f, 0xEC / 255f, 0x27 / 255f, 1f)
    val GREEN = Color(0x00 / 255f, 0xE4 / 255f, 0x36 / 255f, 1f)
    val SKY_BLUE = Color(0x29 / 255f, 0xAD / 255f, 0xFF / 255f, 1f)
    val INDIGO = Color(0x83 / 255f, 0x76 / 255f, 0x9C / 255f, 1f)
    val PINK = Color(0xFF / 255f, 0x77 / 255f, 0xA8 / 255f, 1f)
    val PEACH = Color(0xFF / 255f, 0xCC / 255f, 0xAA / 255f, 1f)
    val MATRIX_GREEN = Color(0x00 / 255f, 0xFF / 255f, 0x41 / 255f, 1f)
    val MATRIX_BG = Color(0x0A / 255f, 0x0A / 255f, 0x0A / 255f, 1f)
}

/**
 * libGDX-based renderer for the pixel office.
 *
 * Uses standard libGDX Y-up coordinates (Y=0 at bottom).
 * World coordinates from config are Y-down, so we convert them.
 */
class Renderer(
    private val width: Int,
    private val height: Int,
    private val spriteSheet: SpriteSheet,
    private val skyTrafficInterval: Float = 30f,
    private val skyTrafficEnabled: Boolean = true,
    private val skyTrafficSprite: String = "sprites/32bit-PaperAirplane",
    private val skyTrafficFrameCount: Int = 4
) {
    // libGDX rendering objects
    lateinit var batch: SpriteBatch
        private set
    lateinit var shapeRenderer: ShapeRenderer
        private set
    lateinit var font: BitmapFont
        private set

    // Sprite sheet texture
    var texture: Texture? = null
        private set

    // Camera
    private var camera = GameCamera(width, height)

    // Animation time tracking
    private var time = 0f

    // UI state
    var showDebug = false
    var nightMode = false

    private enum class LabelMode { OFF, CHARACTERS, DESKS, ROUTES, FURNITURE }
    private var labelMode = LabelMode.OFF
    private var connectedCount = 0
    private var isDemoMode = false
    private var fps = 0
    private var walkableZones: List<WalkableZone> = emptyList()
    private var lineNetwork: List<NavLine> = emptyList()
    private var debugDevelopers: List<CharacterRenderInfo> = emptyList()
    private var debugPM: CharacterRenderInfo? = null
    private var debugPO: CharacterRenderInfo? = null

    // Furniture label recording for debug overlay
    private val furnitureLabels = mutableListOf<Triple<String, Float, Float>>()
    private val deferredEffects = mutableListOf<EffectRenderInfo>()

    // LED clock rendering - 1×1 green pixel used to draw segment digits
    private var ledPixelTexture: Texture? = null
    private lateinit var ledPixelRegion: TextureRegion
    private val ledGreen = Color(0x00 / 255f, 0xE4 / 255f, 0x36 / 255f, 1f)

    // Procedural sky
    private lateinit var skyRenderer: SkyRenderer

    // Soft radial glow texture for night mode monitor glow
    private var glowTexture: Texture? = null
    private lateinit var glowRegion: TextureRegion

    // 3×5 pixel patterns for 7-segment style digits (row-major, top to bottom)
    private val digitPatterns: Array<BooleanArray> = arrayOf(
        // 0
        booleanArrayOf(
            true, true, true,
            true, false, true,
            true, false, true,
            true, false, true,
            true, true, true
        ),
        // 1
        booleanArrayOf(
            false, false, true,
            false, false, true,
            false, false, true,
            false, false, true,
            false, false, true
        ),
        // 2
        booleanArrayOf(
            true, true, true,
            false, false, true,
            true, true, true,
            true, false, false,
            true, true, true
        ),
        // 3
        booleanArrayOf(
            true, true, true,
            false, false, true,
            true, true, true,
            false, false, true,
            true, true, true
        ),
        // 4
        booleanArrayOf(
            true, false, true,
            true, false, true,
            true, true, true,
            false, false, true,
            false, false, true
        ),
        // 5
        booleanArrayOf(
            true, true, true,
            true, false, false,
            true, true, true,
            false, false, true,
            true, true, true
        ),
        // 6
        booleanArrayOf(
            true, true, true,
            true, false, false,
            true, true, true,
            true, false, true,
            true, true, true
        ),
        // 7
        booleanArrayOf(
            true, true, true,
            false, false, true,
            false, false, true,
            false, false, true,
            false, false, true
        ),
        // 8
        booleanArrayOf(
            true, true, true,
            true, false, true,
            true, true, true,
            true, false, true,
            true, true, true
        ),
        // 9
        booleanArrayOf(
            true, true, true,
            true, false, true,
            true, true, true,
            false, false, true,
            true, true, true
        )
    )

    // 3×5 pixel patterns for uppercase letters (row-major, top to bottom)
    private val letterPatterns: Map<Char, BooleanArray> = mapOf(
        'A' to booleanArrayOf(
            false, true, false,
            true, false, true,
            true, true, true,
            true, false, true,
            true, false, true
        ),
        'B' to booleanArrayOf(
            true, true, false,
            true, false, true,
            true, true, false,
            true, false, true,
            true, true, false
        ),
        'C' to booleanArrayOf(
            false, true, true,
            true, false, false,
            true, false, false,
            true, false, false,
            false, true, true
        ),
        'D' to booleanArrayOf(
            true, true, false,
            true, false, true,
            true, false, true,
            true, false, true,
            true, true, false
        ),
        'E' to booleanArrayOf(
            true, true, true,
            true, false, false,
            true, true, false,
            true, false, false,
            true, true, true
        ),
        'F' to booleanArrayOf(
            true, true, true,
            true, false, false,
            true, true, false,
            true, false, false,
            true, false, false
        ),
        'G' to booleanArrayOf(
            false, true, true,
            true, false, false,
            true, false, true,
            true, false, true,
            false, true, true
        ),
        'H' to booleanArrayOf(
            true, false, true,
            true, false, true,
            true, true, true,
            true, false, true,
            true, false, true
        ),
        'I' to booleanArrayOf(
            true, true, true,
            false, true, false,
            false, true, false,
            false, true, false,
            true, true, true
        ),
        'J' to booleanArrayOf(
            false, false, true,
            false, false, true,
            false, false, true,
            true, false, true,
            false, true, false
        ),
        'K' to booleanArrayOf(
            true, false, true,
            true, false, true,
            true, true, false,
            true, false, true,
            true, false, true
        ),
        'L' to booleanArrayOf(
            true, false, false,
            true, false, false,
            true, false, false,
            true, false, false,
            true, true, true
        ),
        'M' to booleanArrayOf(
            true, false, true,
            true, true, true,
            true, true, true,
            true, false, true,
            true, false, true
        ),
        'N' to booleanArrayOf(
            true, false, true,
            true, true, true,
            true, true, true,
            true, true, true,
            true, false, true
        ),
        'O' to booleanArrayOf(
            false, true, false,
            true, false, true,
            true, false, true,
            true, false, true,
            false, true, false
        ),
        'P' to booleanArrayOf(
            true, true, false,
            true, false, true,
            true, true, false,
            true, false, false,
            true, false, false
        ),
        'Q' to booleanArrayOf(
            false, true, false,
            true, false, true,
            true, false, true,
            true, true, false,
            false, true, true
        ),
        'R' to booleanArrayOf(
            true, true, false,
            true, false, true,
            true, true, false,
            true, false, true,
            true, false, true
        ),
        'S' to booleanArrayOf(
            false, true, true,
            true, false, false,
            false, true, false,
            false, false, true,
            true, true, false
        ),
        'T' to booleanArrayOf(
            true, true, true,
            false, true, false,
            false, true, false,
            false, true, false,
            false, true, false
        ),
        'U' to booleanArrayOf(
            true, false, true,
            true, false, true,
            true, false, true,
            true, false, true,
            false, true, false
        ),
        'V' to booleanArrayOf(
            true, false, true,
            true, false, true,
            true, false, true,
            false, true, false,
            false, true, false
        ),
        'W' to booleanArrayOf(
            true, false, true,
            true, false, true,
            true, true, true,
            true, true, true,
            true, false, true
        ),
        'X' to booleanArrayOf(
            true, false, true,
            true, false, true,
            false, true, false,
            true, false, true,
            true, false, true
        ),
        'Y' to booleanArrayOf(
            true, false, true,
            true, false, true,
            false, true, false,
            false, true, false,
            false, true, false
        ),
        'Z' to booleanArrayOf(
            true, true, true,
            false, false, true,
            false, true, false,
            true, false, false,
            true, true, true
        ),
        ' ' to booleanArrayOf(
            false, false, false,
            false, false, false,
            false, false, false,
            false, false, false,
            false, false, false
        )
    )

    // 9×9 pixel gear icon pattern (row-major, top to bottom)
    private val gearPattern: BooleanArray = booleanArrayOf(
        false, false, true,  false, true,  false, true,  false, false,
        false, false, true,  true,  true,  true,  true,  false, false,
        true,  true,  true,  false, false, false, true,  true,  true,
        false, true,  false, false, false, false, false, true,  false,
        true,  true,  false, false, true,  false, false, true,  true,
        false, true,  false, false, false, false, false, true,  false,
        true,  true,  true,  false, false, false, true,  true,  true,
        false, false, true,  true,  true,  true,  true,  false, false,
        false, false, true,  false, true,  false, true,  false, false
    )

    // Company name sign
    var companyName: String = "Pixel Office"

    // Desk occupancy tracking for dynamic chair positions
    private var occupiedDesks: Set<String> = emptySet()

    // Data-driven desk columns
    private var deskColumns: List<DeskColumn> = emptyList()

    // Animation constants
    companion object {
        const val BOB_SPEED = 8.0f
        const val BOB_AMPLITUDE = 1.0f

        // Delegate layout constants to DeskColumn (single source of truth)
        val LEFT_COLUMN_X get() = DeskColumn.LEFT_COLUMN_X
        val RIGHT_COLUMN_X get() = DeskColumn.RIGHT_COLUMN_X
        val DESK_ROW_Y_POSITIONS get() = DeskColumn.DESK_ROW_Y_POSITIONS

        // Chair X offsets relative to baseX
        private const val WEST_CHAIR_PUSHED_BACK = 5f   // Pulled away (occupied)
        private const val WEST_CHAIR_PUSHED_IN = 14f    // ~6px under left desk edge (unoccupied)
        private const val EAST_CHAIR_PUSHED_BACK = 60f  // Pulled away (occupied)
        private const val EAST_CHAIR_PUSHED_IN = 52f    // ~5px under right desk edge (unoccupied)

        // Night mode colors
        private val NIGHT_SKY_COLOR = Color(0.04f, 0.04f, 0.12f, 1f)
        private val NIGHT_OVERLAY_COLOR = Color(0f, 0f, 0.05f, 0.55f)
        private val NIGHT_GLOW_COLOR = Color(0.4f, 0.5f, 0.8f, 1f)

        // Monitor face glow (CRT green on developer's face while coding)
        private val MONITOR_FACE_GLOW_COLOR = Color(0.1f, 0.85f, 0.3f, 1f)
        private const val MONITOR_FACE_GLOW_SIZE = 22f
        private const val MONITOR_FACE_GLOW_ALPHA = 0.16f

        // Floor lamp glow (warm amber for nighttime illumination)
        private val LAMP_GLOW_COLOR = Color(1.0f, 0.85f, 0.4f, 1f)

        @Deprecated("Use DeskColumn.getDeskPosition instead", ReplaceWith("DeskColumn.getDeskPosition(columnX, rowIndex, isLeftDesk)"))
        fun getDeskPosition(columnX: Float, rowIndex: Int, isLeftDesk: Boolean): Pair<Float, Float> =
            DeskColumn.getDeskPosition(columnX, rowIndex, isLeftDesk)
    }

    /**
     * Initialize rendering resources.
     * Must be called after libGDX is initialized.
     */
    fun initialize() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        font = BitmapFont().apply {
            data.setScale(1f)
        }

        // Load the sprite sheet texture
        texture = Texture(Gdx.files.internal("sprites/PixelOfficeAssets.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }

        // Initialize sprite sheet with texture
        spriteSheet.setTexture(texture!!)
        spriteSheet.initialize()

        // Create 1×1 white pixel texture for LED clock digits
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f)
        pixmap.fill()
        ledPixelTexture = Texture(pixmap).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }
        ledPixelRegion = TextureRegion(ledPixelTexture)
        pixmap.dispose()

        // Create 64×64 radial gradient texture for soft monitor glow
        val glowSize = 64
        val glowPixmap = Pixmap(glowSize, glowSize, Pixmap.Format.RGBA8888)
        val center = glowSize / 2f
        for (y in 0 until glowSize) {
            for (x in 0 until glowSize) {
                val dx = (x - center) / center
                val dy = (y - center) / center
                val dist = sqrt(dx * dx + dy * dy).coerceAtMost(1f)
                val alpha = max(0f, 1f - dist * dist)
                glowPixmap.setColor(1f, 1f, 1f, alpha)
                glowPixmap.drawPixel(x, y)
            }
        }
        glowTexture = Texture(glowPixmap).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        glowRegion = TextureRegion(glowTexture)
        glowPixmap.dispose()

        // Initialize procedural sky
        skyRenderer = SkyRenderer(width, height, skyTrafficInterval, skyTrafficEnabled, skyTrafficSprite, skyTrafficFrameCount)
    }

    fun setCamera(camera: GameCamera) {
        this.camera = camera
    }

    fun getCamera(): GameCamera = camera

    fun update(dt: Float) {
        time += dt
        skyRenderer.update(dt)
    }

    fun clear() {
        val c = skyRenderer.getCurrentTopColor()
        Gdx.gl.glClearColor(c.r, c.g, c.b, c.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    /**
     * Convert Y-down world coordinate to Y-up screen coordinate.
     * In config, Y=0 is at top. In libGDX, Y=0 is at bottom.
     */
    private fun flipY(worldY: Float, spriteHeight: Int = 0): Float {
        return height - worldY - spriteHeight
    }

    fun beginBatch() {
        // Use simple ortho projection - camera scrolling handled by coordinate offsets
        batch.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        batch.begin()
    }

    fun endBatch() {
        batch.end()
    }

    fun beginShapes(type: ShapeRenderer.ShapeType = ShapeRenderer.ShapeType.Filled) {
        // Use simple ortho projection - camera scrolling handled by coordinate offsets
        shapeRenderer.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        shapeRenderer.begin(type)
    }

    fun endShapes() {
        shapeRenderer.end()
    }

    /**
     * Debug draw method to test rendering without camera complexity.
     */
    fun debugDraw() {
        val tex = texture ?: return

        // Use simple ortho projection
        batch.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        batch.begin()

        // Draw the entire texture at bottom-left to verify it loads
        batch.draw(tex, 0f, 0f)

        // Draw a floor tile in the center
        val floorFrame = spriteSheet.getTileFrame("floor_tile")
        if (floorFrame != null) {
            batch.draw(floorFrame.region, width/2f, height/2f)
        }

        // Draw a developer sprite
        val devSprite = spriteSheet.getSprite("developer_blue")
        val devAnim = devSprite?.animations?.get("idle")
        if (devAnim != null) {
            val frame = devAnim.frames[0]
            batch.draw(frame.region, 100f, 100f)
        }

        batch.end()

        // Draw UI overlay
        drawUIOverlay()
    }

    /**
     * Draw the background: procedural sky, gray bar, wall tiles.
     */
    fun drawBackground() {
        // End the batch opened by drawScene so we can use ShapeRenderer
        endBatch()

        // Procedural sky gradient (ShapeRenderer)
        skyRenderer.drawGradient(shapeRenderer)

        // Procedural clouds (ShapeRenderer)
        skyRenderer.drawClouds(shapeRenderer)

        // Flying objects (SpriteBatch) — behind stars, in front of clouds
        beginBatch()
        skyRenderer.drawFlyingObjects(batch)
        endBatch()

        // Stars at night (SpriteBatch)
        beginBatch()
        skyRenderer.drawStars(batch, ledPixelRegion)
        endBatch()

        // Draw gray wall strip below clouds (6px high)
        // In Y-down: starts at y=38. In Y-up: starts at height-38-6
        val grayBarY = flipY(38f, 6)
        beginShapes()
        shapeRenderer.color = Colors.DARK_GRAY
        shapeRenderer.rect(0f, grayBarY, width.toFloat(), 6f)
        endShapes()

        // Resume batch for wall tiles
        beginBatch()

        // Draw wall tiles below the gray bar
        // In Y-down: wall starts at y=44. In Y-up we need to convert each row
        drawWall(44)

        drawFurniture("window_with_note", 40f, 70f)
        drawFurniture("door", 120f, 75f, flipX = true)
        drawFurniture("door", 136f, 75f)

        drawFurniture("clock", 126f, 65f)
        drawClockTime(125f, 65f)
        drawCompanySign()

        drawFurniture("window", 250f, 70f)
    }

    /**
     * Draw wall tiles - 3 rows spanning the screen width.
     */
    private fun drawWall(worldStartY: Int) {
        val wallFrame = spriteSheet.getTileFrame("wall_tile") ?: return

        val tileW = wallFrame.width
        val tileH = wallFrame.height

        // Draw 3 rows of wall tiles
        for (row in 0 until 3) {
            val worldY = worldStartY + (row * tileH)
            val screenY = flipY(worldY.toFloat(), tileH)
            var x = 0
            while (x < width) {
                batch.draw(wallFrame.region, x.toFloat(), screenY)
                x += tileW
            }
        }
    }

    /**
     * Draw the floor tiles.
     */
    fun drawFloor() {
        val savedColor = batch.color.cpy()
        batch.color = Color(0.76f, 0.76f, 0.78f, 1f)
        val floorTopScreenY = flipY(104f, height - 104)
        batch.draw(ledPixelRegion, 0f, floorTopScreenY, width.toFloat(), (height - 104).toFloat())
        batch.color = savedColor
    }

    /**
     * Draw any furniture/decoration sprite at world coordinates.
     * @param name Sprite name in the sprite sheet (e.g., "desk_left", "tree", "whiteboard").
     * @param worldX X position in world coordinates.
     * @param worldY Y position in world coordinates (Y-down).
     * @param flipX If true, draws the sprite mirrored horizontally.
     */
    fun drawFurniture(name: String, worldX: Float, worldY: Float, flipX: Boolean = false) {
        if (labelMode == LabelMode.FURNITURE) {
            furnitureLabels.add(Triple(name, worldX, worldY))
        }
        val frame = spriteSheet.getFurnitureFrame(name) ?: return
        val screenY = flipY(worldY, frame.height)
        if (flipX) {
            batch.draw(
                frame.region,
                worldX + frame.width, screenY,
                -frame.width.toFloat(), frame.height.toFloat()
            )
        } else {
            batch.draw(frame.region, worldX, screenY)
        }
    }

    /**
     * Draw the wall clock time as LED segment-style digits.
     * Renders "HH:MM" in bright green over the clock sprite.
     * @param worldX X position of the clock sprite in world coordinates.
     * @param worldY Y position of the clock sprite in world coordinates (Y-down).
     */
    private fun drawClockTime(worldX: Float, worldY: Float) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val prevColor = Color(batch.color)
        batch.color = ledGreen

        // Clock sprite is 19×6. Digits are 3×5, colon is 1×5.
        // Layout: 1px pad + 3 + 1 + 3 + 1 + 1 + 1 + 3 + 1 + 3 + 1px pad = 19
        val startX = worldX + 1f
        val startWorldY = worldY + 1f  // 1px top padding within the 6px sprite
        val screenY = flipY(startWorldY, 5)

        var cx = startX
        drawLedDigit(hour / 10, cx, screenY); cx += 4f
        drawLedDigit(hour % 10, cx, screenY); cx += 4f
        // Colon: two dots at rows 1 and 3 (0-indexed)
        val colonX = cx
        batch.draw(ledPixelRegion, colonX, screenY + 3f, 1f, 1f)
        batch.draw(ledPixelRegion, colonX, screenY + 1f, 1f, 1f)
        cx += 2f
        drawLedDigit(minute / 10, cx, screenY); cx += 4f
        drawLedDigit(minute % 10, cx, screenY)

        batch.color = prevColor
    }

    /**
     * Draw a single 3×5 LED segment digit at screen coordinates.
     */
    private fun drawLedDigit(digit: Int, screenX: Float, screenY: Float) {
        val pattern = digitPatterns[digit]
        for (row in 0 until 5) {
            for (col in 0 until 3) {
                if (pattern[row * 3 + col]) {
                    // row 0 = top of digit = highest screen Y (screenY + 4)
                    batch.draw(ledPixelRegion, screenX + col, screenY + (4 - row), 1f, 1f)
                }
            }
        }
    }

    /**
     * Draw a single 3×5 LED character at screen coordinates.
     * Supports A-Z (via letterPatterns) and 0-9 (via digitPatterns).
     */
    private fun drawLedChar(ch: Char, screenX: Float, screenY: Float) {
        val pattern = when {
            ch in '0'..'9' -> digitPatterns[ch - '0']
            else -> letterPatterns[ch] ?: return
        }
        for (row in 0 until 5) {
            for (col in 0 until 3) {
                if (pattern[row * 3 + col]) {
                    batch.draw(ledPixelRegion, screenX + col, screenY + (4 - row), 1f, 1f)
                }
            }
        }
    }

    /**
     * Draw the 9×9 pixel gear icon at screen coordinates.
     * screenX/screenY is the bottom-left corner of the icon.
     */
    private fun drawGearIcon(screenX: Float, screenY: Float) {
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (gearPattern[row * 9 + col]) {
                    batch.draw(ledPixelRegion, screenX + col, screenY + (8 - row), 1f, 1f)
                }
            }
        }
    }

    /**
     * Draw a modern arc floor lamp at world coordinates using procedural pixels.
     * The lamp turns on at night with a warm glow.
     */
    private fun drawFloorLamp(worldX: Float, worldY: Float) {
        val savedColor = batch.color.cpy()
        val lampColor = Colors.BLACK
        val litColor = Color(1.0f, 0.85f, 0.4f, 1f)

        // Base: 5px wide, 1px tall
        batch.color = lampColor
        for (i in 0 until 5) {
            batch.draw(ledPixelRegion, worldX + i - 2f, flipY(worldY + 21f, 0), 1f, 1f)
        }

        // Pole: 1px wide, 18px tall (from y+3 to y+20)
        for (i in 3 until 21) {
            batch.draw(ledPixelRegion, worldX, flipY(worldY + i.toFloat(), 0), 1f, 1f)
        }

        // Arc arm: extends right from top of pole, 4px
        for (i in 1..4) {
            batch.draw(ledPixelRegion, worldX + i, flipY(worldY + 2f, 0), 1f, 1f)
        }

        // Shade/head: 5px wide, 2px tall, hanging down from arm end
        val headColor = if (nightMode) litColor else lampColor
        batch.color = headColor
        for (row in 0 until 2) {
            for (col in 0 until 5) {
                batch.draw(ledPixelRegion, worldX + 2f + col, flipY(worldY + 3f + row, 0), 1f, 1f)
            }
        }

        // Small bulb pixel directly below shade center when lit
        if (nightMode) {
            batch.color = Color(1.0f, 0.95f, 0.7f, 1f)
            batch.draw(ledPixelRegion, worldX + 4f, flipY(worldY + 5f, 0), 1f, 1f)
        }

        // Top cap: 1px connecting pole top to arm
        batch.color = lampColor
        batch.draw(ledPixelRegion, worldX, flipY(worldY + 2f, 0), 1f, 1f)

        batch.color = savedColor
    }

    /**
     * Draw the company name sign on the wall to the right of the clock.
     * Black background with bright green LED-style text, matching the clock aesthetic.
     */
    private fun drawCompanySign() {
        val text = companyName.uppercase()
        val charWidth = 3
        val gap = 1
        val textWidth = text.length * (charWidth + gap) - gap
        val padding = 2
        val bgWidth = textWidth + padding * 2
        val bgHeight = 5 + padding * 2  // 5px tall chars + padding

        // Center over the two whiteboards (x=180..222, center=201)
        val signWorldX = 201f - bgWidth / 2f
        val signWorldY = 64f

        // Draw black background (need shapeRenderer)
        endBatch()
        beginShapes()
        shapeRenderer.color = Colors.BLACK
        val bgScreenY = flipY(signWorldY, bgHeight)
        shapeRenderer.rect(signWorldX, bgScreenY, bgWidth.toFloat(), bgHeight.toFloat())
        endShapes()

        // Draw green LED text
        beginBatch()
        val prevColor = Color(batch.color)
        batch.color = ledGreen

        val textScreenY = flipY(signWorldY + padding.toFloat(), 5)
        var cx = signWorldX + padding
        for (ch in text) {
            drawLedChar(ch, cx, textScreenY)
            cx += (charWidth + gap)
        }

        batch.color = prevColor

        drawLedBar()
    }

    /**
     * Draw LED light bar above the company sign showing connection count.
     * 8 LED slots: lit green for connected panes, yellow in demo mode, dark gray for unlit.
     */
    private fun drawLedBar() {
        val ledCount = 8
        val ledW = 3f
        val ledH = 2f
        val gap = 2f
        val totalWidth = ledCount * ledW + (ledCount - 1) * gap
        val barX = 201f - totalWidth / 2f
        val barWorldY = 60f
        val screenY = flipY(barWorldY, ledH.toInt())

        val prevColor = Color(batch.color)
        val unlitColor = Color(0.15f, 0.15f, 0.15f, 1f)

        for (i in 0 until ledCount) {
            val x = barX + i * (ledW + gap)
            val lit = if (isDemoMode) true else i < connectedCount
            batch.color = when {
                isDemoMode && lit -> Colors.YELLOW
                lit -> ledGreen
                else -> unlitColor
            }
            batch.draw(ledPixelRegion, x, screenY, ledW, ledH)
        }

        batch.color = prevColor
    }

    fun drawDog(worldX: Float, worldY: Float) {
        val anim = spriteSheet.getAnimalAnimation("dog")
        val frame = anim?.getFrameAtTime(time) ?: spriteSheet.getAnimalFrame("dog") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawCat(worldX: Float, worldY: Float) {
        val anim = spriteSheet.getAnimalAnimation("cat")
        val frame = anim?.getFrameAtTime(time) ?: spriteSheet.getAnimalFrame("cat") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    /**
     * Draw a sprite frame at world coordinates (Y-down).
     */
    fun drawSprite(
        worldX: Float,
        worldY: Float,
        frame: SpriteFrame,
        flipX: Boolean = false,
        bobOffset: Float = 0f
    ) {
        val screenY = flipY(worldY + bobOffset, frame.height)

        if (flipX) {
            batch.draw(
                frame.region,
                worldX + frame.width, screenY,
                -frame.width.toFloat(), frame.height.toFloat()
            )
        } else {
            batch.draw(frame.region, worldX, screenY)
        }
    }

    private fun getBobOffset(entityId: String, isWalking: Boolean): Float {
        if (!isWalking) return 0f

        // Use entity_id hash to offset the bob phase
        val phaseOffset = (entityId.hashCode() % 100) / 100f * Math.PI.toFloat() * 2
        return (sin(time * BOB_SPEED + phaseOffset) * BOB_AMPLITUDE).toFloat()
    }

    /**
     * Draw a developer sprite with bobbing animation.
     */
    fun drawDeveloper(
        worldX: Float,
        worldY: Float,
        animation: String,
        facing: String,
        variant: Int,
        entityId: String,
        posture: String = "standing"
    ) {
        val baseName = spriteSheet.getDeveloperSpriteName(variant)

        // Try to get sitting sprite first if posture is sitting
        val spriteName = if (posture == "sitting") {
            "${baseName}_sitting"
        } else {
            baseName
        }

        // Get sprite, fallback to standing if sitting not available
        val sprite = spriteSheet.getSprite(spriteName) ?: spriteSheet.getSprite(baseName) ?: return

        val anim = sprite.animations[animation] ?: sprite.animations["idle"] ?: return
        val frame = anim.getFrameAtTime(time)

        val flipX = facing == "left"
        val isWalking = animation.startsWith("walking")
        val bobOffset = getBobOffset(entityId, isWalking)

        drawSprite(worldX, worldY, frame, flipX, bobOffset)
    }

    /**
     * Draw a subtle green CRT glow on a developer's face while coding.
     * Uses the existing glowRegion radial gradient, tinted green.
     */
    private fun drawMonitorFaceGlow(info: CharacterRenderInfo) {
        val savedColor = batch.color.cpy()

        // Face center: upper third of sprite, ~4px below top in world coords
        val faceCenterX = info.x + 8f  // roughly center of 16px-wide sprite
        val faceCenterY = info.y + 4f  // near top of sprite (Y-down world)

        // Offset toward the monitor side based on facing direction
        val monitorOffsetX = if (info.facing == "left") -3f else 3f

        val glowX = faceCenterX + monitorOffsetX
        val screenY = flipY(faceCenterY, 0)

        batch.color = Color(
            MONITOR_FACE_GLOW_COLOR.r,
            MONITOR_FACE_GLOW_COLOR.g,
            MONITOR_FACE_GLOW_COLOR.b,
            MONITOR_FACE_GLOW_ALPHA
        )
        batch.draw(
            glowRegion,
            glowX - MONITOR_FACE_GLOW_SIZE / 2f,
            screenY - MONITOR_FACE_GLOW_SIZE / 2f,
            MONITOR_FACE_GLOW_SIZE,
            MONITOR_FACE_GLOW_SIZE
        )

        batch.color = savedColor
    }

    /**
     * Draw a generic character sprite with bobbing.
     */
    fun drawCharacter(
        worldX: Float,
        worldY: Float,
        spriteName: String,
        animation: String,
        facing: String,
        entityId: String
    ) {
        val sprite = spriteSheet.getSprite(spriteName) ?: return

        val anim = sprite.animations[animation] ?: sprite.animations["idle"] ?: return
        val frame = anim.getFrameAtTime(time)

        val flipX = facing == "left"
        val isWalking = animation.startsWith("walking")
        val bobOffset = getBobOffset(entityId, isWalking)

        drawSprite(worldX, worldY, frame, flipX, bobOffset)
    }

    /**
     * Draw PM or PO with sitting/standing posture support.
     */
    fun drawPMOrPO(
        worldX: Float,
        worldY: Float,
        baseName: String,
        animation: String,
        facing: String,
        entityId: String,
        posture: String = "standing"
    ) {
        // Try to get sitting sprite first if posture is sitting
        val spriteName = if (posture == "sitting") {
            "${baseName}_sitting"
        } else {
            baseName
        }

        // Get sprite, fallback to standing if sitting not available
        val sprite = spriteSheet.getSprite(spriteName) ?: spriteSheet.getSprite(baseName) ?: return

        val anim = sprite.animations[animation] ?: sprite.animations["idle"] ?: return
        val frame = anim.getFrameAtTime(time)

        val flipX = facing == "left"
        val isWalking = animation.startsWith("walking")
        val bobOffset = getBobOffset(entityId, isWalking)

        drawSprite(worldX, worldY, frame, flipX, bobOffset)
    }

    private fun bubbleColor(base: Color) = Color(base.r, base.g, base.b, 0.25f)

    /**
     * Draw a thought bubble effect (procedural).
     * @param bubbleType Type of bubble: "thinking", "blah", "question", "annoyed"
     */
    fun drawThoughtBubble(worldX: Float, worldY: Float, frameIndex: Int, bubbleType: String = "thinking") {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        when (bubbleType) {
            "thinking" -> drawThinkingBubble(worldX, worldY, frameIndex)
            "blah" -> drawBlahBubble(worldX, worldY, frameIndex)
            "question" -> drawQuestionBubble(worldX, worldY, frameIndex)
            "annoyed" -> drawAnnoyedBubble(worldX, worldY, frameIndex)
            "coding" -> drawCodingBubble(worldX, worldY, frameIndex)
            else -> drawThinkingBubble(worldX, worldY, frameIndex)
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * Draw thinking bubble with animated dots.
     */
    private fun drawThinkingBubble(worldX: Float, worldY: Float, frameIndex: Int) {
        endBatch()

        // Convert to screen coordinates (bubble is ~20px tall)
        val screenY = flipY(worldY, 20)

        // Animated thought bubble
        val pulse = (sin(time * 4) * 1).toInt()

        beginShapes()

        // Main bubble
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)

        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = bubbleColor(Colors.DARK_GRAY)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        // Animated dots inside bubble
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.DARK_GRAY)
        val dotOffset = ((time * 4).toInt()) % 4
        for (i in 0 until 3) {
            var dotY = screenY + 12 - pulse
            if ((i + dotOffset) % 4 < 3) {
                dotY += if ((i + dotOffset) % 4 == 1) 1f else 0f
            }
            shapeRenderer.circle(worldX + 4 + i * 4, dotY, 1f)
        }
        endShapes()

        beginShapes()
        // Small connecting bubbles (below main bubble in screen coords)
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX - 2, screenY + 6, 2f)
        shapeRenderer.circle(worldX - 4, screenY + 2, 1f)
        endShapes()

        beginBatch()
    }

    /**
     * Draw speech bubble with "blah" lines (PM talking).
     */
    private fun drawBlahBubble(worldX: Float, worldY: Float, frameIndex: Int) {
        endBatch()

        val screenY = flipY(worldY, 20)
        val pulse = (sin(time * 4) * 1).toInt()

        // Speech bubble (slightly larger, more oval)
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 10f)
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = bubbleColor(Colors.DARK_GRAY)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 10f)
        endShapes()

        // Speech lines inside (horizontal lines to represent talking)
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.DARK_GRAY)
        val lineY = screenY + 12 - pulse
        shapeRenderer.rectLine(worldX + 2, lineY + 2, worldX + 14, lineY + 2, 1f)
        shapeRenderer.rectLine(worldX + 4, lineY - 1, worldX + 12, lineY - 1, 1f)
        shapeRenderer.rectLine(worldX + 3, lineY - 4, worldX + 13, lineY - 4, 1f)
        endShapes()

        // Speech bubble tail (pointing down-left)
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.triangle(
            worldX, screenY + 6,
            worldX + 4, screenY + 6,
            worldX - 2, screenY + 2
        )
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = bubbleColor(Colors.DARK_GRAY)
        shapeRenderer.line(worldX, screenY + 6, worldX - 2, screenY + 2)
        shapeRenderer.line(worldX - 2, screenY + 2, worldX + 4, screenY + 6)
        endShapes()

        beginBatch()
    }

    /**
     * Draw question bubble with "?" symbol.
     */
    private fun drawQuestionBubble(worldX: Float, worldY: Float, frameIndex: Int) {
        endBatch()

        val screenY = flipY(worldY, 20)
        val pulse = (sin(time * 4) * 1).toInt()

        // Main bubble
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = bubbleColor(Colors.DARK_GRAY)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        // Question mark drawn with shapes
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.DARK_PURPLE)
        // Top curve of ?
        shapeRenderer.circle(worldX + 8, screenY + 15 - pulse, 3f)
        // Clear center to make it hollow
        endShapes()

        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX + 8, screenY + 15 - pulse, 1.5f)
        endShapes()

        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.DARK_PURPLE)
        // Stem of ?
        shapeRenderer.rect(worldX + 7, screenY + 10 - pulse, 2f, 3f)
        // Dot of ?
        shapeRenderer.circle(worldX + 8, screenY + 8 - pulse, 1f)
        endShapes()

        // Small connecting bubbles
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX - 2, screenY + 6, 2f)
        shapeRenderer.circle(worldX - 4, screenY + 2, 1f)
        endShapes()

        beginBatch()
    }

    /**
     * Draw annoyed bubble with "!" symbol.
     */
    private fun drawAnnoyedBubble(worldX: Float, worldY: Float, frameIndex: Int) {
        endBatch()

        val screenY = flipY(worldY, 20)
        val pulse = (sin(time * 6) * 2).toInt()  // Faster, more agitated pulse

        // Main bubble (slightly red-tinted for annoyance)
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = bubbleColor(Colors.RED)
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        // Exclamation mark
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.RED)
        // Stem of !
        shapeRenderer.rect(worldX + 7, screenY + 10 - pulse, 2f, 6f)
        // Dot of !
        shapeRenderer.circle(worldX + 8, screenY + 8 - pulse, 1.2f)
        endShapes()

        // Small connecting bubbles
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.WHITE)
        shapeRenderer.circle(worldX - 2, screenY + 6, 2f)
        shapeRenderer.circle(worldX - 4, screenY + 2, 1f)
        endShapes()

        beginBatch()
    }

    /**
     * Draw Matrix-style coding bubble with cascading green binary digits.
     */
    private fun drawCodingBubble(worldX: Float, worldY: Float, frameIndex: Int) {
        endBatch()

        val screenY = flipY(worldY, 20)

        // Bubble dimensions (slightly wider/taller than standard circle bubbles)
        val bw = 20f
        val bh = 16f
        val bx = worldX
        val by = screenY + 6f

        // Dark background rectangle
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.MATRIX_BG)
        shapeRenderer.rect(bx, by, bw, bh)
        endShapes()

        // Dark green border
        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = bubbleColor(Colors.DARK_GREEN)
        shapeRenderer.rect(bx, by, bw, bh)
        endShapes()

        // Cascading binary digits using font
        beginBatch()
        val oldScale = font.data.scaleX
        font.data.setScale(0.25f)

        val columns = 4
        val colWidth = bw / columns
        val speeds = floatArrayOf(0.4f, 0.6f, 0.3f, 0.5f)
        val offsets = floatArrayOf(0f, 1.7f, 0.8f, 2.3f)
        val rows = 3

        for (col in 0 until columns) {
            val cx = bx + 2f + col * colWidth
            for (row in 0 until rows) {
                // Scroll position based on time, speed, and offset
                val scroll = (time * speeds[col] + offsets[col] + row * 0.7f)
                val digit = if (((scroll * 3).toInt()) % 2 == 0) "1" else "0"
                // Brightness varies by row for depth effect
                val brightness = when (row) {
                    0 -> 1.0f
                    1 -> 0.7f
                    else -> 0.4f
                }
                font.color = Color(0f, brightness, 0.25f * brightness, 0.25f)
                val dy = by + bh - 3f - row * 5f
                // Only draw if within bubble bounds
                if (dy > by + 1f) {
                    font.draw(batch, digit, cx, dy)
                }
            }
        }

        font.data.setScale(oldScale)
        endBatch()

        // Small connecting thought-bubbles below
        beginShapes()
        shapeRenderer.color = bubbleColor(Colors.MATRIX_BG)
        shapeRenderer.circle(worldX - 2, screenY + 4, 2f)
        shapeRenderer.circle(worldX - 4, screenY + 1, 1f)
        endShapes()

        beginBatch()
    }

    /**
     * Draw a ghost effect (procedural, rising animation).
     */
    fun drawGhost(worldX: Float, worldY: Float, frameIndex: Int, alpha: Float) {
        if (alpha <= 0.3f) return

        endBatch()

        // Convert to screen coordinates
        val screenY = flipY(worldY, 24)

        // Ghost rises (in screen coords, rising = increasing Y)
        val riseOffset = ((1.0f - alpha) * 20).toInt()

        // Enable blending for alpha
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        beginShapes()
        shapeRenderer.color = Color(Colors.WHITE.r, Colors.WHITE.g, Colors.WHITE.b, alpha)

        // Body
        shapeRenderer.circle(worldX + 8, screenY + 16 + riseOffset, 6f)

        endShapes()

        // Wavy bottom
        beginShapes(ShapeRenderer.ShapeType.Filled)
        val waveTime = (time * 6).toInt()
        for (i in 0 until 4) {
            val offset = (waveTime + i) % 2
            shapeRenderer.color = Color(Colors.WHITE.r, Colors.WHITE.g, Colors.WHITE.b, alpha)
            shapeRenderer.circle(worldX + 4 + i * 3, screenY + 10 + riseOffset - offset, 1f)
        }
        endShapes()

        // Eyes
        beginShapes(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0f, 0f, 0f, alpha)
        shapeRenderer.circle(worldX + 6, screenY + 18 + riseOffset, 1f)
        shapeRenderer.circle(worldX + 10, screenY + 18 + riseOffset, 1f)
        endShapes()

        Gdx.gl.glDisable(GL20.GL_BLEND)

        beginBatch()
    }

    /**
     * Draw night mode overlay with dim filter and monitor glows.
     * Called after the main scene batch ends, before UI overlay.
     */
    private fun drawNightOverlay(renderData: RenderData) {
        beginBatch()

        // Step 1: Dim overlay covering the full screen
        val savedColor = batch.color.cpy()
        batch.color = NIGHT_OVERLAY_COLOR
        batch.draw(ledPixelRegion, 0f, 0f, width.toFloat(), height.toFloat())

        // Step 2: Monitor glows with additive blending
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (column in renderData.deskColumns) {
            for (row in column.rows) {
                row.westDesk?.let { desk ->
                    if (desk.equipment != Equipment.NONE) {
                        drawMonitorGlow(column.baseX + 12f, row.wallY + 7f, desk.equipment)
                    }
                }
                row.eastDesk?.let { desk ->
                    if (desk.equipment != Equipment.NONE) {
                        drawMonitorGlow(column.baseX + 49f, row.wallY + 7f, desk.equipment)
                    }
                }
            }
        }

        // Step 2b: LED green glows for clock and company sign (still additive)
        val glowGreen = Color(ledGreen.r, ledGreen.g, ledGreen.b, 0.12f)

        // Clock glow — clock sprite is 19×6 at (126,65), center at (135.5, 68)
        val clockGlowSize = 35f
        val clockCenterX = 135.5f
        val clockScreenCenterY = flipY(68f, 0)
        batch.color = glowGreen
        batch.draw(glowRegion, clockCenterX - clockGlowSize / 2f, clockScreenCenterY - clockGlowSize / 2f, clockGlowSize, clockGlowSize)

        // Sign glow — sign centered at world (201, 67), elliptical ~55×30
        val signGlowW = 55f
        val signGlowH = 30f
        val signCenterX = 201f
        val signScreenCenterY = flipY(67f, 0)
        batch.color = glowGreen
        batch.draw(glowRegion, signCenterX - signGlowW / 2f, signScreenCenterY - signGlowH / 2f, signGlowW, signGlowH)

        // Floor lamp glow — warm amber downward cone from lamp head at (283, 82)
        val lampHeadX = 283f + 4f  // center of lamp shade
        val lampHeadY = 87f        // just below the shade (worldY + 5)
        val lampScreenY = flipY(lampHeadY, 0)

        // Outer glow: elliptical, wider at bottom for downward light cone
        val lampOuterW = 45f
        val lampOuterH = 55f
        batch.color = Color(LAMP_GLOW_COLOR.r, LAMP_GLOW_COLOR.g, LAMP_GLOW_COLOR.b, 0.15f)
        batch.draw(glowRegion, lampHeadX - lampOuterW / 2f, lampScreenY - lampOuterH * 0.7f, lampOuterW, lampOuterH)

        // Inner glow: tighter, slightly brighter
        val lampInnerW = 20f
        val lampInnerH = 30f
        batch.color = Color(LAMP_GLOW_COLOR.r, LAMP_GLOW_COLOR.g, LAMP_GLOW_COLOR.b, 0.10f)
        batch.draw(glowRegion, lampHeadX - lampInnerW / 2f, lampScreenY - lampInnerH * 0.6f, lampInnerW, lampInnerH)

        // Step 3: Restore normal blending and color
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // Step 4: Re-draw LED elements over the dark overlay so they appear bright
        drawClockTime(125f, 65f)

        batch.color = savedColor

        // drawCompanySign starts with endBatch() so batch must be open
        drawCompanySign()
        endBatch()
    }

    /**
     * Draw soft radial glow around a monitor/computer at world coordinates.
     */
    private fun drawMonitorGlow(worldX: Float, worldY: Float, equipment: Equipment) {
        val (spriteW, spriteH) = when (equipment) {
            Equipment.MONITOR -> 13f to 23f
            Equipment.COMPUTER -> 15f to 19f
            Equipment.NONE -> return
        }

        val centerX = worldX + spriteW / 2f
        val screenCenterY = flipY(worldY + spriteH / 2f, 0)

        // Outer soft glow
        val outerSize = 60f
        batch.color = Color(NIGHT_GLOW_COLOR.r, NIGHT_GLOW_COLOR.g, NIGHT_GLOW_COLOR.b, 0.18f)
        batch.draw(glowRegion, centerX - outerSize / 2f, screenCenterY - outerSize / 2f, outerSize, outerSize)

        // Inner brighter core
        val innerSize = 30f
        batch.color = Color(NIGHT_GLOW_COLOR.r, NIGHT_GLOW_COLOR.g, NIGHT_GLOW_COLOR.b, 0.12f)
        batch.draw(glowRegion, centerX - innerSize / 2f, screenCenterY - innerSize / 2f, innerSize, innerSize)
    }

    /**
     * Draw UI elements on top of the scene.
     */
    fun drawUIOverlay() {
        // Use a separate projection for UI (not affected by camera)
        batch.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        batch.begin()

        // Settings gear icon (top-right)
        batch.color = Colors.WHITE
        drawGearIcon(width - 12f, height - 12f)

        // FPS (if debug mode)
        if (showDebug) {
            font.color = Colors.WHITE
            font.draw(batch, "FPS: $fps", 4f, height - 18f)
            font.draw(batch, "Time: ${String.format("%.1f", time)}s", 4f, height - 32f)

            // Show forced sitting mode status
            if (PixelOfficeGame.forceSittingMode) {
                font.color = Colors.YELLOW
                font.draw(batch, "[F2] FORCE SITTING: ON", 4f, height - 46f)
            }

            // Cursor position (convert screen Y-down to world Y-up)
            val mouseX = Gdx.input.x
            val mouseY = height.toInt() - Gdx.input.y
            font.draw(batch, "Cursor: $mouseX, $mouseY", 4f, height - 60f)

            // Developer state information
            font.color = Colors.WHITE
            var yOffset = height - 74f  // Start below cursor position

            font.draw(batch, "=== DEVELOPERS ===", 4f, yOffset)
            yOffset -= 14f

            for (dev in debugDevelopers) {
                // Color code by variant
                font.color = when (dev.variant) {
                    0 -> Colors.SKY_BLUE   // Blue variant
                    1 -> Colors.GREEN      // Green variant
                    2 -> Colors.RED        // Red variant
                    else -> Colors.WHITE
                }

                font.draw(batch, "${dev.entityId}:", 4f, yOffset)
                yOffset -= 12f

                font.color = Colors.WHITE
                font.draw(batch, "  state=${dev.state} pos=(${dev.x.toInt()},${dev.y.toInt()})", 4f, yOffset)
                yOffset -= 12f

                font.draw(batch, "  posture=${dev.posture} anim=${dev.animation}", 4f, yOffset)
                yOffset -= 14f
            }
        }

        // Show labels status independently of debug HUD
        if (labelMode != LabelMode.OFF) {
            font.color = Colors.ORANGE
            font.draw(batch, "[F4] ${labelMode.name}", width - 120f, height - 18f)
        }

        batch.end()
    }

    /**
     * Draw walkable zone fills for debugging.
     * Uses semi-transparent yellow to show collision corridors.
     */
    private fun drawWalkableZones() {
        if (!showDebug || walkableZones.isEmpty()) return

        // Enable blending for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        beginShapes(ShapeRenderer.ShapeType.Filled)
        // Yellow with high opacity (0.7 alpha)
        shapeRenderer.color = Color(Colors.YELLOW.r, Colors.YELLOW.g, Colors.YELLOW.b, 0.7f)

        for (zone in walkableZones) {
            // Convert Y-down world coords to Y-up screen coords
            // Zone bottom in world = zone.y + zone.h
            val screenY = flipY(zone.y.toFloat() + zone.h, 0)
            shapeRenderer.rect(
                zone.x.toFloat(),
                screenY,
                zone.w.toFloat(),
                zone.h.toFloat()
            )
        }

        endShapes()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    fun setConnectionCount(count: Int) {
        connectedCount = count
    }

    fun setDemoMode(enabled: Boolean) {
        isDemoMode = enabled
    }

    fun setFps(fps: Int) {
        this.fps = fps
    }

    fun toggleDebug() {
        showDebug = !showDebug
    }

    fun isSkyNightTime(): Boolean = skyRenderer.isNightTime()

    fun cycleLabels() {
        val modes = LabelMode.entries
        labelMode = modes[(labelMode.ordinal + 1) % modes.size]
    }

    fun setWalkableZones(zones: List<WalkableZone>) {
        walkableZones = zones
    }

    fun setLineNetwork(lines: List<NavLine>) {
        lineNetwork = lines
    }

    /**
     * Draw line network for debugging.
     * Shows navigation lines in red with small circles at intersection points.
     */
    private fun drawLineNetwork() {
        if (lineNetwork.isEmpty()) return
        if (!showDebug && labelMode != LabelMode.ROUTES) return

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Colors.RED

        for (line in lineNetwork) {
            // Convert Y-down world coords to Y-up screen coords
            val fromScreenY = flipY(line.from.y, 0)
            val toScreenY = flipY(line.to.y, 0)
            shapeRenderer.line(line.from.x, fromScreenY, line.to.x, toScreenY)
        }

        endShapes()

        // Draw small circles at intersection points
        beginShapes(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Colors.RED

        val drawnPoints = mutableSetOf<String>()
        for (line in lineNetwork) {
            if (line.from.id !in drawnPoints) {
                val screenY = flipY(line.from.y, 0)
                shapeRenderer.circle(line.from.x, screenY, 2f)
                drawnPoints.add(line.from.id)
            }
            if (line.to.id !in drawnPoints) {
                val screenY = flipY(line.to.y, 0)
                shapeRenderer.circle(line.to.x, screenY, 2f)
                drawnPoints.add(line.to.id)
            }
        }

        endShapes()
    }

    /**
     * Draw a character (developer, PM, or PO) from typed render info.
     */
    fun drawCharacterInfo(info: CharacterRenderInfo) {
        when (info.type) {
            "developer" -> {
                drawDeveloper(
                    info.x, info.y,
                    info.animation, info.facing, info.variant,
                    info.entityId, info.posture
                )
                if (info.state == "writing_code" && info.posture == "sitting") {
                    drawMonitorFaceGlow(info)
                }
            }
            "project_manager" -> {
                drawPMOrPO(
                    info.x, info.y,
                    "project_manager",
                    info.animation, info.facing,
                    info.entityId, info.posture
                )
            }
            "product_owner" -> {
                drawPMOrPO(
                    info.x, info.y,
                    "product_owner",
                    info.animation, info.facing,
                    info.entityId, info.posture
                )
            }
        }
        deferredEffects.addAll(info.children)
    }

    /**
     * Draw an effect (thought bubble or ghost) from typed render info.
     */
    fun drawEffectInfo(effect: EffectRenderInfo) {
        when (effect) {
            is EffectRenderInfo.Bubble -> {
                val b = effect.info
                drawThoughtBubble(b.x, b.y, b.frame, b.bubbleType)
            }
            is EffectRenderInfo.Ghost -> {
                val g = effect.info
                drawGhost(g.x, g.y, g.frame, g.alpha)
            }
        }
    }

    // ==================== Data-Driven Desk Rendering ====================

    /**
     * Draw a chair by color enum at the given position.
     * @param facingRight If true, draws chair facing right (east desk); otherwise facing left (west desk).
     */
    private fun drawChairByColor(color: ChairColor, x: Float, y: Float, facingRight: Boolean) {
        val spriteName = when (color) {
            ChairColor.BLACK -> if (facingRight) "chair_white" else "chair_black"
            ChairColor.WHITE -> if (facingRight) "chair_white" else "chair_black"
            ChairColor.BLUE -> if (facingRight) "chair_blue" else "chair_black"
            ChairColor.GREEN -> if (facingRight) "chair_blue" else "chair_green"
            ChairColor.ORANGE -> if (facingRight) "chair_white" else "chair_orange"
        }
        drawFurniture(spriteName, x, y, flipX = facingRight)
    }

    /**
     * Draw equipment (computer/monitor) at the given position.
     * @param facingRight If true, draws facing right (east desk).
     */
    private fun drawEquipment(equipment: Equipment, x: Float, y: Float, facingRight: Boolean) {
        when (equipment) {
            Equipment.COMPUTER -> drawFurniture("computer", x, y, flipX = facingRight)
            Equipment.MONITOR -> drawFurniture("monitor", x, y, flipX = facingRight)
            Equipment.NONE -> { /* no equipment */ }
        }
    }

    /**
     * Draw a wall decoration item at the given position.
     */
    private fun drawWallDecorItem(decor: WallDecor, x: Float, y: Float) {
        val name = when (decor) {
            WallDecor.ART -> "art"
            WallDecor.SMALL_ART_ORANGE -> "small_art_orange"
            WallDecor.SMALL_ART_BLUE -> "small_art_blue"
            WallDecor.SMALL_CALENDAR -> "small_calendar"
            WallDecor.NOTICE -> "notice"
            WallDecor.POST_IT_NOTES -> "post_it_notes"
            WallDecor.NONE -> return
        }
        drawFurniture(name, x, y)
    }

    /**
     * Draw a desk item at the given position.
     */
    private fun drawDeskItem(item: DeskItem, x: Float, y: Float) {
        val name = when (item) {
            DeskItem.RED_BOOK -> "red_book"
            DeskItem.BLUE_BOOK -> "blue_book"
            DeskItem.GREEN_BOOK -> "green_book"
            DeskItem.NOTES -> "notes"
            DeskItem.DOCUMENT -> "document"
            DeskItem.COFFEE_MUG -> "coffee_mug"
            DeskItem.NONE -> return
        }
        drawFurniture(name, x, y)
    }

    /**
     * Draw a single desk from its config.
     * @param baseX Column base X position.
     * @param wallY Wall Y position for this row.
     * @param config The desk configuration.
     * @param deskId The desk ID for occupancy checking.
     */
    private fun drawDeskFromConfig(baseX: Float, wallY: Float, config: DeskConfig, deskId: String) {
        val isEast = config.side == DeskSide.EAST

        if (isEast) {
            // East desk: chair faces right (drawn before desk for layering)
            val chairOffset = if (occupiedDesks.contains(deskId)) EAST_CHAIR_PUSHED_BACK else EAST_CHAIR_PUSHED_IN
            drawChairByColor(config.chairColor, baseX + chairOffset, wallY + 7f, facingRight = true)

            drawFurniture("desk_right", baseX + 40f, wallY + 11f)
            drawEquipment(config.equipment, baseX + 41f, wallY + 7f, facingRight = true)

            var itemOffsetY = 12f
            for (item in config.deskItems) {
                drawDeskItem(item, baseX + 42f, wallY + itemOffsetY)
                itemOffsetY += 10f
            }
        } else {
            // West desk: chair faces left (drawn before desk for layering)
            val chairOffset = if (occupiedDesks.contains(deskId)) WEST_CHAIR_PUSHED_BACK else WEST_CHAIR_PUSHED_IN
            drawChairByColor(config.chairColor, baseX + chairOffset, wallY + 7f, facingRight = false)

            drawFurniture("desk_left", baseX + 19f, wallY + 11f)
            drawEquipment(config.equipment, baseX + 20f, wallY + 7f, facingRight = false)

            var itemOffsetY = 12f
            for (item in config.deskItems) {
                drawDeskItem(item, baseX + 20f, wallY + itemOffsetY)
                itemOffsetY += 10f
            }
        }
    }

    /**
     * Draw a complete row from DeskColumn config.
     * Draws partition, wall decor, and both desks.
     */
    private fun drawRowFromConfig(column: DeskColumn, row: DeskRow, rowIndex: Int) {
        val baseX = column.baseX
        val wallY = row.wallY

        // Draw wall decor for west desk (positioned at standard offset)
        row.westDesk?.let {
            drawWallDecorItem(it.wallDecor, baseX + 9f, wallY + 5f)
        }

        // Draw partition between desks
        drawFurniture("desk_partition", baseX + 36f, wallY + 3f)

        // Draw west desk
        row.westDesk?.let {
            val deskId = column.getDeskId((rowIndex + 1) * 2)
            drawDeskFromConfig(baseX, wallY, it, deskId)
        }

        // Draw east desk
        row.eastDesk?.let {
            val deskId = column.getDeskId((rowIndex + 1) * 2 - 1)
            drawDeskFromConfig(baseX, wallY, it, deskId)
        }
    }

    // ==================== Desk Wall Methods ====================
    // These draw ONLY the desk wall at each Y position

    private fun drawDeskWall1(baseX: Float) { drawFurniture("desk_wall", baseX, 125f) }
    private fun drawDeskWall2(baseX: Float) { drawFurniture("desk_wall", baseX, 155f) }
    private fun drawDeskWall3(baseX: Float) { drawFurniture("desk_wall", baseX, 185f) }
    private fun drawDeskWall4(baseX: Float) { drawFurniture("desk_wall", baseX, 215f) }

    // ==================== Desk Furniture Methods ====================
    // These draw all furniture for each row EXCEPT the wall

    /**
     * Draws furniture for desk row 1 (Y=125) - desks, chairs, monitors, decorations.
     */
    private fun drawDeskFurniture1(baseX: Float) {
        val wallY = 125f
        val isLeftColumn = baseX == LEFT_COLUMN_X
        val westDesk = if (isLeftColumn) "desk_1" else "desk_3"
        val eastDesk = if (isLeftColumn) "desk_2" else "desk_4"

        // Draw notice for left column only (y=131 is between row 1 and row 2)
        if (isLeftColumn) {
            drawFurniture("notice", LEFT_COLUMN_X + 60f, 131f)
        }
        drawFurniture("small_art_orange", baseX + 9f, wallY + 5f)
        drawFurniture("desk_partition", baseX + 36f, wallY + 3f)

        // West chair BEFORE left desk (so desk covers chair)
        val westOffset = if (occupiedDesks.contains(westDesk)) WEST_CHAIR_PUSHED_BACK else WEST_CHAIR_PUSHED_IN
        drawFurniture("chair_black", baseX + westOffset, wallY + 7f)

        drawFurniture("desk_left", baseX + 19f, wallY + 11f)
        drawFurniture("monitor", baseX + 20f, wallY + 7f)

        // East chair BEFORE right desk (so desk covers chair)
        val eastOffset = if (occupiedDesks.contains(eastDesk)) EAST_CHAIR_PUSHED_BACK else EAST_CHAIR_PUSHED_IN
        drawFurniture("chair_white", baseX + eastOffset, wallY + 7f, flipX = true)

        drawFurniture("desk_right", baseX + 40f, wallY + 11f)
        drawFurniture("computer", baseX + 41f, wallY + 7f, flipX = true)
    }

    /**
     * Draws furniture for desk row 2 (Y=155) - desks, chairs, monitors, decorations.
     */
    private fun drawDeskFurniture2(baseX: Float, isLeftColumn: Boolean = true) {
        val wallY = 155f
        val westDesk = if (isLeftColumn) "desk_5" else "desk_7"
        val eastDesk = if (isLeftColumn) "desk_6" else "desk_8"

        if (!isLeftColumn) {
            drawFurniture("post_it_notes", baseX + 70f, wallY + 5f)
        }
        drawFurniture("art", baseX + 7f, wallY + 5f)
        drawFurniture("desk_partition", baseX + 36f, wallY + 3f)

        val westOffset = if (occupiedDesks.contains(westDesk)) WEST_CHAIR_PUSHED_BACK else WEST_CHAIR_PUSHED_IN
        drawFurniture("chair_green", baseX + westOffset, wallY + 7f)

        drawFurniture("desk_left", baseX + 19f, wallY + 11f)
        drawFurniture("computer", baseX + 20f, wallY + 7f)

        val eastOffset = if (occupiedDesks.contains(eastDesk)) EAST_CHAIR_PUSHED_BACK else EAST_CHAIR_PUSHED_IN
        drawFurniture("chair_blue", baseX + eastOffset, wallY + 7f, flipX = true)

        drawFurniture("desk_right", baseX + 40f, wallY + 11f)
        drawFurniture("monitor", baseX + 41f, wallY + 7f, flipX = true)
    }

    /**
     * Draws furniture for desk row 3 (Y=185) in the LEFT column - desks, chairs, etc.
     */
    private fun drawDeskFurniture3Left(baseX: Float) {
        val wallY = 185f
        drawFurniture("small_art_blue", baseX + 9f, wallY + 5f)
        drawFurniture("desk_partition", baseX + 36f, wallY + 3f)

        val westOffset = if (occupiedDesks.contains("desk_9")) WEST_CHAIR_PUSHED_BACK else WEST_CHAIR_PUSHED_IN
        drawFurniture("chair_orange", baseX + westOffset, wallY + 7f)

        drawFurniture("desk_left", baseX + 19f, wallY + 11f)
        drawFurniture("monitor", baseX + 20f, wallY + 7f)

        drawFurniture("desk_right", baseX + 40f, wallY + 11f)
        drawFurniture("red_book", baseX + 42f, wallY + 12f)
        drawFurniture("notes", baseX + 42f, wallY + 22f)
    }

    /**
     * Draws furniture for desk row 3 (Y=185) in the RIGHT column - lounge area.
     */
    private fun drawDeskFurniture3Right(baseX: Float) {
        val wallY = 185f
        drawFurniture("small_art_blue", baseX + 9f, wallY + 5f)
        drawFurniture("couch_green", baseX + 22f, wallY + 10f)
        drawFurniture("red_trash_can", baseX + 56f, wallY + 10f)
        drawFurniture("tree", baseX + 66f, wallY + 5f)
    }

    // Row 4 has no furniture, only the wall

    /**
     * Draws desk furniture for a specific wall Y position.
     * Uses DeskColumn data if available, falls back to legacy hardcoded methods.
     */
    private fun drawDeskFurnitureForRow(wallY: Float) {
        if (deskColumns.isNotEmpty()) {
            // Data-driven rendering from desk columns
            for (column in deskColumns) {
                for ((rowIndex, row) in column.rows.withIndex()) {
                    if (row.wallY == wallY) {
                        drawRowFromConfig(column, row, rowIndex)
                    }
                }
            }

            // Right column row 3 lounge area stays hardcoded (not a desk row)
            if (wallY == 185f) {
                val rightColumn = deskColumns.find { it.baseX == RIGHT_COLUMN_X }
                val hasRow3 = rightColumn?.rows?.any { it.wallY == 185f } ?: false
                if (!hasRow3) {
                    drawDeskFurniture3Right(RIGHT_COLUMN_X)
                }

                // Left column row 3: standalone east desk surface + items (no chair)
                val leftColumn = deskColumns.find { it.baseX == LEFT_COLUMN_X }
                val leftRow3 = leftColumn?.rows?.find { it.wallY == 185f }
                if (leftRow3 != null && leftRow3.eastDesk == null) {
                    val baseX = LEFT_COLUMN_X
                    drawFurniture("desk_right", baseX + 40f, 185f + 11f)
                    drawFurniture("red_book", baseX + 42f, 185f + 12f)
                    drawFurniture("notes", baseX + 42f, 185f + 22f)
                }
            }

            // Extra decorations that were part of legacy rows
            if (wallY == 125f) {
                drawFurniture("notice", LEFT_COLUMN_X + 60f, 131f)
            }
            if (wallY == 155f) {
                val rightColumn = deskColumns.find { it.baseX == RIGHT_COLUMN_X }
                val hasRow2 = rightColumn?.rows?.any { it.wallY == 155f } ?: false
                if (hasRow2) {
                    drawFurniture("post_it_notes", RIGHT_COLUMN_X + 70f, 160f)
                }
                drawFurniture("small_art_blue", 110f, 160f)
            }
            if (wallY == 185f) {
                drawFurniture("small_calendar", 110f, 190f)
            }
        } else {
            // Legacy fallback: hardcoded furniture rendering
            when (wallY) {
                125f -> {
                    drawDeskFurniture1(LEFT_COLUMN_X)
                    drawDeskFurniture1(RIGHT_COLUMN_X)
                }
                155f -> {
                    drawDeskFurniture2(LEFT_COLUMN_X, isLeftColumn = true)
                    drawDeskFurniture2(RIGHT_COLUMN_X, isLeftColumn = false)
                }
                185f -> {
                    drawDeskFurniture3Left(LEFT_COLUMN_X)
                    drawDeskFurniture3Right(RIGHT_COLUMN_X)
                }
                215f -> {
                    // Row 4 has no furniture, just the wall (left column only)
                }
            }
        }
    }

    /**
     * Draws desk walls for a specific wall Y position.
     * Called during interleaved rendering before furniture.
     */
    private fun drawDeskWallsForRow(wallY: Float) {
        when (wallY) {
            125f -> {
                drawDeskWall1(LEFT_COLUMN_X)
                drawDeskWall1(RIGHT_COLUMN_X)
            }
            155f -> {
                drawDeskWall2(LEFT_COLUMN_X)
                drawDeskWall2(RIGHT_COLUMN_X)
            }
            185f -> {
                drawDeskWall3(LEFT_COLUMN_X)
                drawDeskWall3(RIGHT_COLUMN_X)
            }
            215f -> {
                // Only left column has row 4
                drawDeskWall4(LEFT_COLUMN_X)
            }
        }
    }

    /**
     * Collect all characters from render data and sort by Y position.
     * Returns a list of visible characters sorted by Y (lower Y = behind).
     */
    private fun collectAndSortEntities(renderData: RenderData): List<CharacterRenderInfo> {
        val entities = mutableListOf<CharacterRenderInfo>()
        entities.addAll(renderData.developers)
        renderData.projectManager?.let { entities.add(it) }
        renderData.productOwner?.let { entities.add(it) }
        return entities
            .filter { it.visible }
            .sortedBy { it.y }
    }

    /**
     * Draw the complete scene from render data.
     * Uses interleaved Y-zone rendering so characters appear behind desk walls
     * but on top of other furniture.
     */
    fun drawScene(renderData: RenderData) {
        furnitureLabels.clear()

        // Store character data for debug overlay / labels
        debugDevelopers = renderData.developers
        debugPM = renderData.projectManager
        debugPO = renderData.productOwner

        // Extract occupied desks from render data
        occupiedDesks = renderData.desks.filter { it.occupied }.map { it.id }.toSet()

        // Extract desk columns for data-driven rendering
        deskColumns = renderData.deskColumns

        // Start batch for background and tiles
        beginBatch()

        // Draw background (sky + clouds + wall)
        drawBackground()

        // Draw floor tiles
        drawFloor()

        // Draw top area furniture (above all desk rows)
        drawFurniture("vending_blue", 5f, 78f)
        drawFurniture("water_cooler", 31f, 95f)
        drawFurniture("small_table", 41f, 95f)
        drawFurniture("coffee_mug", 43f, 98f)
        drawFurniture("coffee_machine", 53f, 93f)
        drawFurniture("whiteboard", 73f, 83f)
        drawFurniture("tree", 95f, 90f)
        drawFurniture("tree", 162f, 90f)
        drawFurniture("whiteboard", 180f, 83f)
        drawFurniture("whiteboard", 205f, 83f)
        drawFurniture("couch_orange", 225f, 95f)
        drawFurniture("blue_trash_can", 260f, 95f)
        drawFurniture("bookshelf", 290f, 80f)
        drawFloorLamp(283f, 82f)

        // Collect and sort all entities once
        val entities = collectAndSortEntities(renderData)
        val drawnEntities = mutableSetOf<String>()

        // Interleaved rendering: for each desk wall Y level, draw characters
        // that should appear BEHIND that wall, then the wall, then furniture
        for (wallY in DESK_ROW_Y_POSITIONS) {
            // Draw characters with y <= wallY (these appear behind this wall)
            for (entity in entities) {
                if (entity.y <= wallY && entity.entityId !in drawnEntities) {
                    drawCharacterInfo(entity)
                    drawnEntities.add(entity.entityId)
                }
            }

            // Draw desk walls at this Y level for both columns
            drawDeskWallsForRow(wallY)

            // Draw desk furniture for this row (desks, chairs, monitors)
            drawDeskFurnitureForRow(wallY)
        }

        // Draw remaining characters (in front of all walls - Y > 215)
        for (entity in entities) {
            if (entity.entityId !in drawnEntities) {
                drawCharacterInfo(entity)
            }
        }

        // Additional decorations not part of desk columns
        drawDog(105f, 200f)
        drawCat(165f, 190f)
        drawFurniture("tree", 76f, 218f)

        drawFurniture("large_table", 193f, 220f)
        drawFurniture("printer", 215f, 221f)
        drawFurniture("document", 205f, 222f)

        drawFurniture("tree", 305f, 123f)
        drawFurniture("tree", 305f, 153f)
        drawFurniture("tree", 305f, 188f)

        endBatch()

        // Night mode overlay (dim + monitor glows)
        if (nightMode) drawNightOverlay(renderData)

        // Draw effects on top of everything (including night overlay sign)
        beginBatch()
        for (effect in renderData.effects) {
            drawEffectInfo(effect)
        }
        for (effect in deferredEffects) {
            drawEffectInfo(effect)
        }
        deferredEffects.clear()
        endBatch()

        // Draw debug overlays
        drawWalkableZones()
        drawLineNetwork()
        drawDebugLabels()

        // Draw UI overlay
        drawUIOverlay()
    }

    // ==================== Debug Labels ====================

    /**
     * Draw a single label at a world position with a semi-transparent background.
     * @param text The label text.
     * @param worldX X position in world coordinates.
     * @param worldY Y position in world coordinates (Y-down).
     * @param color Text color.
     */
    private fun drawLabel(text: String, worldX: Float, worldY: Float, color: Color) {
        val screenY = flipY(worldY, 0)

        // Measure text width at current scale
        val glyphLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout(font, text)
        val textWidth = glyphLayout.width
        val textHeight = glyphLayout.height
        val pad = 2f

        // Draw background rectangle
        endBatch()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        beginShapes(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0f, 0f, 0f, 0.6f)
        shapeRenderer.rect(worldX - pad, screenY - textHeight - pad, textWidth + pad * 2, textHeight + pad * 2)
        endShapes()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // Draw text
        beginBatch()
        font.color = color
        font.draw(batch, text, worldX, screenY)
    }

    private fun abbreviateNavId(id: String): String = when {
        id.startsWith("wb_corridor_") -> "w${id.last()}"
        id.startsWith("white_board_") -> "W${id.last()}"
        id.startsWith("corridor_") -> "c${id.removePrefix("corridor_").first().uppercase()}"
        id.startsWith("left_aisle_y") -> "L${id.removePrefix("left_aisle_y").takeLast(2)}"
        id.startsWith("center_aisle_y") -> "C${id.removePrefix("center_aisle_y").takeLast(2)}"
        id.startsWith("right_aisle_y") -> "R${id.removePrefix("right_aisle_y").takeLast(2)}"
        id == "left_aisle_bottom" -> "Lb"
        id == "center_aisle_bottom" -> "Cb"
        id == "right_aisle_bottom" -> "Rb"
        id == "bottom_corridor_center" -> "bc"
        id == "bottom_corridor_right" -> "br"
        id.startsWith("mid_deskColumn") -> "m${id[14]}_d${id.substringAfterLast("desk")}"
        id.startsWith("deskColumn") -> "dC${id[10]}_d${id.substringAfterLast("desk")}"
        id.startsWith("desk_") -> "d${id.removePrefix("desk_")}"
        else -> id
    }

    /**
     * Draw debug labels for the active label mode.
     * Cycles through categories with F4, independent of F1 debug HUD.
     */
    private fun drawDebugLabels() {
        if (labelMode == LabelMode.OFF) return

        // Set font to small scale for labels
        font.data.setScale(0.5f)

        beginBatch()

        when (labelMode) {
            LabelMode.CHARACTERS -> {
                for (dev in debugDevelopers) {
                    if (!dev.visible) continue
                    val color = when (dev.variant) {
                        0 -> Colors.SKY_BLUE
                        1 -> Colors.GREEN
                        2 -> Colors.RED
                        else -> Colors.GREEN
                    }
                    drawLabel(dev.entityId, dev.x, dev.y - 10f, color)
                }
                debugPM?.let {
                    if (it.visible) drawLabel(it.entityId, it.x, it.y - 10f, Colors.GREEN)
                }
                debugPO?.let {
                    if (it.visible) drawLabel(it.entityId, it.x, it.y - 10f, Colors.GREEN)
                }
            }
            LabelMode.DESKS -> {
                for (column in deskColumns) {
                    val firstRowY = column.rows.firstOrNull()?.wallY ?: 125f
                    drawLabel(column.id, column.baseX + 40f, firstRowY - 15f, Colors.ORANGE)
                }
                for (column in deskColumns) {
                    for ((rowIndex, row) in column.rows.withIndex()) {
                        row.westDesk?.let {
                            val deskNumber = (rowIndex + 1) * 2
                            val (dx, dy) = DeskColumn.getDeskPosition(column.baseX, rowIndex, isLeftDesk = true)
                            drawLabel("D$deskNumber", dx, dy, Colors.WHITE)
                        }
                        row.eastDesk?.let {
                            val deskNumber = (rowIndex + 1) * 2 - 1
                            val (dx, dy) = DeskColumn.getDeskPosition(column.baseX, rowIndex, isLeftDesk = false)
                            drawLabel("D$deskNumber", dx, dy, Colors.WHITE)
                        }
                    }
                }
            }
            LabelMode.ROUTES -> {
                val drawnPoints = mutableSetOf<String>()
                for (line in lineNetwork) {
                    if (line.from.id !in drawnPoints) {
                        drawLabel(abbreviateNavId(line.from.id), line.from.x, line.from.y, Colors.RED)
                        drawnPoints.add(line.from.id)
                    }
                    if (line.to.id !in drawnPoints) {
                        drawLabel(abbreviateNavId(line.to.id), line.to.x, line.to.y, Colors.RED)
                        drawnPoints.add(line.to.id)
                    }
                }
            }
            LabelMode.FURNITURE -> {
                for ((name, wx, wy) in furnitureLabels) {
                    drawLabel(name, wx, wy, Colors.PEACH)
                }
            }
            LabelMode.OFF -> { /* unreachable */ }
        }

        endBatch()

        // Restore font scale
        font.data.setScale(1f)
    }

    /**
     * Dispose of rendering resources.
     */
    fun dispose() {
        skyRenderer.dispose()
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        texture?.dispose()
        ledPixelTexture?.dispose()
        glowTexture?.dispose()
    }
}
