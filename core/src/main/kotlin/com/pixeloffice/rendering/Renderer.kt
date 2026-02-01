package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.pixeloffice.animation.SpriteFrame
import com.pixeloffice.animation.SpriteSheet
import kotlin.math.sin

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
    private val spriteSheet: SpriteSheet
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
    private var showDebug = false
    private var connectionStatus = "Disconnected"
    private var fps = 0

    // Animation constants
    companion object {
        const val BOB_SPEED = 8.0f
        const val BOB_AMPLITUDE = 1.0f

        // Desk column base X positions
        const val LEFT_COLUMN_X = 45f
        const val RIGHT_COLUMN_X = 175f

        // Desk row Y positions (wall positions)
        val DESK_ROW_Y_POSITIONS = listOf(125f, 155f, 185f, 215f)

        /**
         * Calculate actual desk positions for character assignment.
         * @param columnX Base X position of the column (LEFT_COLUMN_X or RIGHT_COLUMN_X)
         * @param rowIndex Row index (0-3)
         * @param isLeftDesk true for left desk, false for right desk
         * @return Pair of (x, y) coordinates for the desk
         */
        fun getDeskPosition(columnX: Float, rowIndex: Int, isLeftDesk: Boolean): Pair<Float, Float> {
            val wallY = DESK_ROW_Y_POSITIONS[rowIndex]
            val deskX = if (isLeftDesk) columnX + 19f else columnX + 40f
            val deskY = wallY + 11f
            return Pair(deskX, deskY)
        }
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
    }

    fun setCamera(camera: GameCamera) {
        this.camera = camera
    }

    fun getCamera(): GameCamera = camera

    fun update(dt: Float) {
        time += dt
    }

    fun clear(color: Color = Colors.SKY_BLUE) {
        Gdx.gl.glClearColor(color.r, color.g, color.b, color.a)
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
     * Draw the background: clouds at top, gray bar, wall tiles.
     */
    fun drawBackground() {
        val tex = texture ?: return
        val clouds = spriteSheet.getCloudList()

        // Draw clouds at top of screen
        if (clouds.isNotEmpty()) {
            val cloud = clouds[0]
            val cloudY = flipY(0f, cloud.h)  // Y=0 in world = top of screen
            var x = 0
            while (x < width) {
                batch.draw(tex, x.toFloat(), cloudY,
                    cloud.w.toFloat(), cloud.h.toFloat(),
                    cloud.x, cloud.y, cloud.w, cloud.h, false, false)
                x += cloud.w
            }
        }

        // End batch to draw shapes (gray bar)
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

        drawWindowWithNote(40f, 70f)
        drawLeftDoor(120f, 75f)
        drawRightDoor(136f, 75f)

        drawClock(126f, 65f)

        drawWindow(250f, 70f)
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
        val floorFrame = spriteSheet.getTileFrame("floor_tile") ?: return

        val tileW = floorFrame.width
        val tileH = floorFrame.height

        // Floor starts at Y=104 in world coordinates (Y-down)
        // Draw from Y=104 to Y=height
        var worldY = 104
        while (worldY < height) {
            val screenY = flipY(worldY.toFloat(), tileH)
            var x = 0
            while (x < width) {
                batch.draw(floorFrame.region, x.toFloat(), screenY)
                x += tileW
            }
            worldY += tileH
        }
    }

    /**
     * Draw a desk using sprite from PNG.
     */
    fun drawDeskLeft(worldX: Float, worldY: Float, occupied: Boolean = false) {
        val deskFrame = spriteSheet.getFurnitureFrame("desk") ?: return
        val screenY = flipY(worldY, deskFrame.height)
        batch.draw(deskFrame.region, worldX, screenY)
    }

    /**
     * Draw a whiteboard/vending machine.
     */
    fun drawWhiteboard(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("whiteboard") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawOrangeCouch(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("couch_orange") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawGreenCouch(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("couch_green") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawGrayCouch(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("couch_gray") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawPrinter(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("printer") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    /**
     * Draw a whiteboard/vending machine.
     */
    fun drawTree(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("tree") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawRedBook(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("red_book") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawBlueBook(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("blue_book") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawGreenBook(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("green_book") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawNotes(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("notes") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawPostItNotes(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("post_it_notes") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawNotice(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("notice") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawDocument(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("document") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawArt(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("art") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawSmallOrangeArt(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("small_art_orange") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawSmallBlueArt(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("small_art_blue") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawSmallCalendar(worldX: Float, worldY: Float) {
        val wbFrame = spriteSheet.getFurnitureFrame("small_calendar") ?: return
        val screenY = flipY(worldY, wbFrame.height)
        batch.draw(wbFrame.region, worldX, screenY)
    }

    fun drawComputerLeft(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("computer") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawComputerRight(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("computer") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(
            frame.region,
            worldX + frame.width, screenY,
            -frame.width.toFloat(), frame.height.toFloat()
        )
    }

    fun drawMonitorLeft(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("monitor") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawMonitorRight(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("monitor") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(
            frame.region,
            worldX + frame.width, screenY,
            -frame.width.toFloat(), frame.height.toFloat()
        )
    }

    /**
     * Draw blue vending machine.
     */
    fun drawVendingMachine(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("vending_blue") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawBookshelf(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("bookshelf") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawDeskWall(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("desk_wall") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawDeskLeft(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("desk_left") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawDeskRight(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("desk_right") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawDeskPartition(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("desk_partition") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawClock(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("clock") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawWhiteChairRight(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("chair_white") ?: return
        val screenY = flipY(worldY, frame.height)
        // Draw flipped on X axis
        batch.draw(
            frame.region,
            worldX + frame.width, screenY,
            -frame.width.toFloat(), frame.height.toFloat()
        )
    }

    fun drawBlueChairRight(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("chair_blue") ?: return
        val screenY = flipY(worldY, frame.height)
        // Draw flipped on X axis
        batch.draw(
            frame.region,
            worldX + frame.width, screenY,
            -frame.width.toFloat(), frame.height.toFloat()
        )
    }

    fun drawBlackChairLeft(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("chair_black") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawGreenChairLeft(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("chair_green") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawOrangeChairLeft(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("chair_orange") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    /**
     * Draw water cooler.
     */
    fun drawWaterCooler(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("water_cooler") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawSmallTable(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("small_table") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawLargeTable(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("large_table") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawCoffeeMug(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("coffee_mug") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawCoffeeMachine(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("coffee_machine") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawBlueTrashCan(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("blue_trash_can") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawRedTrashCan(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("red_trash_can") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawGreenTrashCan(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("green_trash_can") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawLeftDoor(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("door") ?: return
        val screenY = flipY(worldY, frame.height)
        // Draw flipped on X axis
        batch.draw(
            frame.region,
            worldX + frame.width, screenY,
            -frame.width.toFloat(), frame.height.toFloat()
        )
    }

    fun drawRightDoor(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("door") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawWindowWithNote(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("window_with_note") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawWindow(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("window") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawDog(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getAnimalFrame("dog") ?: return
        val screenY = flipY(worldY, frame.height)
        batch.draw(frame.region, worldX, screenY)
    }

    fun drawCat(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getAnimalFrame("cat") ?: return
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
        entityId: String
    ) {
        val spriteName = spriteSheet.getDeveloperSpriteName(variant)
        val sprite = spriteSheet.getSprite(spriteName) ?: return

        val anim = sprite.animations[animation] ?: sprite.animations["idle"] ?: return
        val frame = anim.getFrameAtTime(time)

        val flipX = facing == "left"
        val isWalking = animation.startsWith("walking")
        val bobOffset = getBobOffset(entityId, isWalking)

        drawSprite(worldX, worldY, frame, flipX, bobOffset)
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
     * Draw a thought bubble effect (procedural).
     * @param bubbleType Type of bubble: "thinking", "blah", "question", "annoyed"
     */
    fun drawThoughtBubble(worldX: Float, worldY: Float, frameIndex: Int, bubbleType: String = "thinking") {
        when (bubbleType) {
            "thinking" -> drawThinkingBubble(worldX, worldY, frameIndex)
            "blah" -> drawBlahBubble(worldX, worldY, frameIndex)
            "question" -> drawQuestionBubble(worldX, worldY, frameIndex)
            "annoyed" -> drawAnnoyedBubble(worldX, worldY, frameIndex)
            else -> drawThinkingBubble(worldX, worldY, frameIndex)
        }
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
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)

        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Colors.DARK_GRAY
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        // Animated dots inside bubble
        beginShapes()
        shapeRenderer.color = Colors.DARK_GRAY
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
        shapeRenderer.color = Colors.WHITE
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
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 10f)
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Colors.DARK_GRAY
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 10f)
        endShapes()

        // Speech lines inside (horizontal lines to represent talking)
        beginShapes()
        shapeRenderer.color = Colors.DARK_GRAY
        val lineY = screenY + 12 - pulse
        shapeRenderer.rectLine(worldX + 2, lineY + 2, worldX + 14, lineY + 2, 1f)
        shapeRenderer.rectLine(worldX + 4, lineY - 1, worldX + 12, lineY - 1, 1f)
        shapeRenderer.rectLine(worldX + 3, lineY - 4, worldX + 13, lineY - 4, 1f)
        endShapes()

        // Speech bubble tail (pointing down-left)
        beginShapes()
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.triangle(
            worldX, screenY + 6,
            worldX + 4, screenY + 6,
            worldX - 2, screenY + 2
        )
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Colors.DARK_GRAY
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
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Colors.DARK_GRAY
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        // Question mark drawn with shapes
        beginShapes()
        shapeRenderer.color = Colors.DARK_PURPLE
        // Top curve of ?
        shapeRenderer.circle(worldX + 8, screenY + 15 - pulse, 3f)
        // Clear center to make it hollow
        endShapes()

        beginShapes()
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.circle(worldX + 8, screenY + 15 - pulse, 1.5f)
        endShapes()

        beginShapes()
        shapeRenderer.color = Colors.DARK_PURPLE
        // Stem of ?
        shapeRenderer.rect(worldX + 7, screenY + 10 - pulse, 2f, 3f)
        // Dot of ?
        shapeRenderer.circle(worldX + 8, screenY + 8 - pulse, 1f)
        endShapes()

        // Small connecting bubbles
        beginShapes()
        shapeRenderer.color = Colors.WHITE
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
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        beginShapes(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Colors.RED
        shapeRenderer.circle(worldX + 8, screenY + 12 - pulse, 8f)
        endShapes()

        // Exclamation mark
        beginShapes()
        shapeRenderer.color = Colors.RED
        // Stem of !
        shapeRenderer.rect(worldX + 7, screenY + 10 - pulse, 2f, 6f)
        // Dot of !
        shapeRenderer.circle(worldX + 8, screenY + 8 - pulse, 1.2f)
        endShapes()

        // Small connecting bubbles
        beginShapes()
        shapeRenderer.color = Colors.WHITE
        shapeRenderer.circle(worldX - 2, screenY + 6, 2f)
        shapeRenderer.circle(worldX - 4, screenY + 2, 1f)
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
     * Draw UI elements on top of the scene.
     */
    fun drawUIOverlay() {
        // Use a separate projection for UI (not affected by camera)
        batch.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        batch.begin()

        // Connection status (top-left, Y-up so high Y = top)
        val statusColor = when (connectionStatus) {
            "Connected" -> Colors.GREEN
            "Demo Mode" -> Colors.YELLOW
            else -> Colors.RED
        }
        font.color = statusColor
        font.draw(batch, "Status: $connectionStatus", 4f, height - 4f)

        // FPS (if debug mode)
        if (showDebug) {
            font.color = Colors.WHITE
            font.draw(batch, "FPS: $fps", 4f, height - 18f)
            font.draw(batch, "Time: ${String.format("%.1f", time)}s", 4f, height - 32f)
        }

        batch.end()
    }

    fun setConnectionStatus(status: String) {
        connectionStatus = status
    }

    fun setFps(fps: Int) {
        this.fps = fps
    }

    fun toggleDebug() {
        showDebug = !showDebug
    }

    /**
     * Draw an entity based on its render info.
     * Coordinates in renderInfo are Y-down world coordinates.
     */
    fun drawEntity(renderInfo: Map<String, Any>) {
        val entityType = renderInfo["type"] as? String ?: ""
        val x = (renderInfo["x"] as? Number)?.toFloat() ?: 0f
        val y = (renderInfo["y"] as? Number)?.toFloat() ?: 0f

        when (entityType) {
            "developer" -> {
                drawDeveloper(
                    x, y,
                    renderInfo["animation"] as? String ?: "idle",
                    renderInfo["facing"] as? String ?: "down",
                    (renderInfo["variant"] as? Number)?.toInt() ?: 0,
                    renderInfo["entity_id"] as? String ?: ""
                )
                // Draw children (thought bubble, ghost)
                @Suppress("UNCHECKED_CAST")
                val children = renderInfo["children"] as? List<Map<String, Any>> ?: emptyList()
                for (child in children) {
                    drawEntity(child)
                }
            }
            "project_manager" -> {
                drawCharacter(
                    x, y,
                    "project_manager",
                    renderInfo["animation"] as? String ?: "idle",
                    renderInfo["facing"] as? String ?: "down",
                    "pm"
                )
                // Draw children (thought bubble)
                @Suppress("UNCHECKED_CAST")
                val pmChildren = renderInfo["children"] as? List<Map<String, Any>> ?: emptyList()
                for (child in pmChildren) {
                    drawEntity(child)
                }
            }
            "project_owner" -> {
                drawCharacter(
                    x, y,
                    "project_owner",
                    renderInfo["animation"] as? String ?: "idle",
                    renderInfo["facing"] as? String ?: "down",
                    "po"
                )
            }
            "thought_bubble" -> {
                drawThoughtBubble(
                    x, y,
                    (renderInfo["frame"] as? Number)?.toInt() ?: 0,
                    renderInfo["bubble_type"] as? String ?: "thinking"
                )
            }
            "ghost" -> {
                drawGhost(
                    x, y,
                    (renderInfo["frame"] as? Number)?.toInt() ?: 0,
                    (renderInfo["alpha"] as? Number)?.toFloat() ?: 1f
                )
            }
        }
    }

    /**
     * Draws the first desk row (Y=125) with orange art decoration.
     */
    private fun drawDeskRow1(baseX: Float) {
        val wallY = 125f
        drawDeskWall(baseX, wallY)
        drawSmallOrangeArt(baseX + 9f, wallY + 5f)
        drawDeskPartition(baseX + 36f, wallY + 3f)
        drawDeskLeft(baseX + 19f, wallY + 11f)
        drawMonitorLeft(baseX + 20f, wallY + 7f)
        drawBlackChairLeft(baseX + 5f, wallY + 7f)
        drawDeskRight(baseX + 40f, wallY + 11f)
        drawComputerRight(baseX + 41f, wallY + 7f)
        drawWhiteChairRight(baseX + 60f, wallY + 7f)
    }

    /**
     * Draws the second desk row (Y=155) with art decoration.
     */
    private fun drawDeskRow2(baseX: Float, isLeftColumn: Boolean = true) {
        val wallY = 155f
        drawDeskWall(baseX, wallY)
        drawNotice(baseX + 60f, wallY + -24)
        if(!isLeftColumn) {
            drawPostItNotes(baseX + 70f, wallY + 5)
        }
        drawArt(baseX + 7f, wallY + 5f)
        drawDeskPartition(baseX + 36f, wallY + 3f)
        drawDeskLeft(baseX + 19f, wallY + 11f)
        drawComputerLeft(baseX + 20f, wallY + 7f)
        drawGreenChairLeft(baseX + 5f, wallY + 7f)
        drawDeskRight(baseX + 40f, wallY + 11f)
        drawMonitorRight(baseX + 41f, wallY + 7f)
        drawBlueChairRight(baseX + 52f, wallY + 7f)
    }

    /**
     * Draws the third desk row (Y=185) with blue art decorations.
     */
    private fun drawDeskRow3(baseX: Float) {
        val wallY = 185f
        drawDeskWall(baseX, wallY)
        drawSmallBlueArt(baseX + 9f, wallY + 5f)
        drawDeskPartition(baseX + 36f, wallY + 3f)
        drawDeskLeft(baseX + 19f, wallY + 11f)
        drawMonitorLeft(baseX + 20f, wallY + 7f)
        drawOrangeChairLeft(baseX + 5f, wallY + 7f)
        drawDeskRight(baseX + 40f, wallY + 11f)
        drawRedBook(baseX + 42f, wallY + 12f)
        drawNotes(baseX + 42f, wallY + 22f)
    }

    /**
     * Draws the fourth desk row (Y=215) with desk wall only (minimal decorations).
     */
    private fun drawDeskRow4(baseX: Float) {
        val wallY = 215f
        drawDeskWall(baseX, wallY)
    }

    /**
     * Draws a complete column of desk rows at the specified base X position.
     * Left column has 4 desk rows. Right column has 3 desk rows plus a lounge area.
     * Each row contains a desk wall, partition, left/right desks, chairs, and decorations.
     */
    private fun drawDeskColumn(baseX: Float, isLeftColumn: Boolean) {
        drawDeskRow1(baseX)
        drawDeskRow2(baseX, isLeftColumn)
        if (isLeftColumn) {
            drawDeskRow3(baseX)
            drawDeskRow4(baseX)
        } else {
            // Right column row 3: desk wall + art + lounge furniture
            val row3Y = 185f
            drawDeskWall(baseX, row3Y)
            drawSmallBlueArt(baseX + 9f, row3Y + 5f)
            drawGreenCouch(baseX + 22f, row3Y + 10f)
            drawRedTrashCan(baseX + 56f, row3Y + 10f)
            drawTree(baseX + 66f, row3Y + 5f)
            // No row 4 - removed entirely
        }
    }

    /**
     * Draw the complete scene from render data.
     */
    fun drawScene(renderData: Map<String, Any>) {
        // Start batch for background and tiles
        beginBatch()

        // Draw background (sky + clouds + wall)
        drawBackground()

        // Draw floor tiles
        drawFloor()

        // Draw vending machine and water cooler
        drawVendingMachine(5f, 78f)
        drawWaterCooler(31f, 95f)
        drawSmallTable(41f, 95f)
        drawCoffeeMug(43f, 98f)
        drawCoffeeMachine(53f, 93f)
        drawWhiteboard(73f, 83f)
        drawTree(95f, 90f)
        drawTree(162f, 90f)
        drawWhiteboard(180f, 83f)
        drawWhiteboard(205f, 83f)
        drawOrangeCouch(225f,95f)
        drawBlueTrashCan(260f, 95f)
        drawBookshelf(290f, 80f)

        // Left desk column (X=45f)
        drawDeskColumn(LEFT_COLUMN_X, isLeftColumn = true)

        // Right desk column (X=175f)
        drawDeskColumn(RIGHT_COLUMN_X, isLeftColumn = false)

        // Additional decorations not part of desk columns
        drawSmallBlueArt(110f, 160f)
        drawSmallCalendar(110f, 190f)
        drawDog(105f, 200f)
        drawCat(165f, 190f)
        drawTree(76f, 218f)

        drawLargeTable(193f, 220f)
        drawPrinter(215f, 221f)
        drawDocument(205f, 222f)

        drawTree(305f, 123f)
        drawTree(305f, 153f)
        drawTree(305f, 188f)

        // Collect and sort entities by y position for depth ordering
        // In Y-up, higher Y = further back, so sort descending
        val entities = mutableListOf<Map<String, Any>>()

        @Suppress("UNCHECKED_CAST")
        val developers = renderData["developers"] as? List<Map<String, Any>> ?: emptyList()
        entities.addAll(developers)

        @Suppress("UNCHECKED_CAST")
        val pm = renderData["project_manager"] as? Map<String, Any>
        if (pm != null) entities.add(pm)

//        @Suppress("UNCHECKED_CAST")
//        val po = renderData["project_owner"] as? Map<String, Any>
//        if (po != null) entities.add(po)

        // Sort by y position (lower Y in world = draw first = behind)
        entities.sortBy { (it["y"] as? Number)?.toFloat() ?: 0f }

        for (entity in entities) {
            val visible = entity["visible"] as? Boolean ?: true
            if (visible) {
                drawEntity(entity)
            }
        }

        // Draw effects on top
        @Suppress("UNCHECKED_CAST")
        val effects = renderData["effects"] as? List<Map<String, Any>> ?: emptyList()
        for (effect in effects) {
            drawEntity(effect)
        }

        endBatch()

        // Draw UI overlay
        drawUIOverlay()
    }

    /**
     * Dispose of rendering resources.
     */
    fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        texture?.dispose()
    }
}
