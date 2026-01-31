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
        const val BOB_AMPLITUDE = 2.0f
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

        drawWindowWithNote(40f, 40f)
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
    fun drawDesk(worldX: Float, worldY: Float, occupied: Boolean = false) {
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

    /**
     * Draw blue vending machine.
     */
    fun drawBlueVendingMachine(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("vending_blue") ?: return
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

    fun drawWindowWithNote(worldX: Float, worldY: Float) {
        val frame = spriteSheet.getFurnitureFrame("window_with_note") ?: return
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
     */
    fun drawThoughtBubble(worldX: Float, worldY: Float, frameIndex: Int) {
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
                    (renderInfo["frame"] as? Number)?.toInt() ?: 0
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
        drawBlueVendingMachine(5f, 78f)
        drawWaterCooler(31f, 95f)
        drawSmallTable(41f, 95f)
        drawCoffeeMug(43f, 98f)
        drawCoffeeMachine(53f, 93f)

//        // Draw furniture
//        @Suppress("UNCHECKED_CAST")
//        val whiteboards = renderData["whiteboards"] as? List<Map<String, Any>> ?: emptyList()
//        for (wb in whiteboards) {
//            val x = (wb["x"] as? Number)?.toFloat() ?: 0f
//            val y = (wb["y"] as? Number)?.toFloat() ?: 0f
//            drawWhiteboard(x, y)
//        }

//        @Suppress("UNCHECKED_CAST")
//        val desks = renderData["desks"] as? List<Map<String, Any>> ?: emptyList()
//        for (desk in desks) {
//            val x = (desk["x"] as? Number)?.toFloat() ?: 0f
//            val y = (desk["y"] as? Number)?.toFloat() ?: 0f
//            val occupied = desk["occupied"] as? Boolean ?: false
//            drawDesk(x, y, occupied)
//        }
//
//        // Collect and sort entities by y position for depth ordering
//        // In Y-up, higher Y = further back, so sort descending
//        val entities = mutableListOf<Map<String, Any>>()
//
//        @Suppress("UNCHECKED_CAST")
//        val developers = renderData["developers"] as? List<Map<String, Any>> ?: emptyList()
//        entities.addAll(developers)
//
//        @Suppress("UNCHECKED_CAST")
//        val pm = renderData["project_manager"] as? Map<String, Any>
//        if (pm != null) entities.add(pm)
//
//        @Suppress("UNCHECKED_CAST")
//        val po = renderData["project_owner"] as? Map<String, Any>
//        if (po != null) entities.add(po)
//
//        // Sort by y position (lower Y in world = draw first = behind)
//        entities.sortBy { (it["y"] as? Number)?.toFloat() ?: 0f }
//
//        for (entity in entities) {
//            val visible = entity["visible"] as? Boolean ?: true
//            if (visible) {
//                drawEntity(entity)
//            }
//        }
//
//        // Draw effects on top
//        @Suppress("UNCHECKED_CAST")
//        val effects = renderData["effects"] as? List<Map<String, Any>> ?: emptyList()
//        for (effect in effects) {
//            drawEntity(effect)
//        }

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
