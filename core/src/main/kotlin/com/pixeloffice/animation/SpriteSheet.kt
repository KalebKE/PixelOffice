package com.pixeloffice.animation

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.pixeloffice.core.AnimatedSpriteRect
import com.pixeloffice.core.SpriteRect
import com.pixeloffice.core.SpriteSheetConfig

/**
 * A single frame from a sprite sheet.
 */
data class SpriteFrame(
    val region: TextureRegion,
    val width: Int,
    val height: Int
) {
    companion object {
        fun fromRect(texture: Texture, rect: SpriteRect): SpriteFrame {
            val region = TextureRegion(texture, rect.x, rect.y, rect.w, rect.h)
            return SpriteFrame(region, rect.w, rect.h)
        }
    }
}

/**
 * An animation sequence of sprite frames.
 */
data class Animation(
    val name: String,
    val frames: List<SpriteFrame>,
    val frameDuration: Float,
    val loop: Boolean = true
) {
    fun getFrame(frameIndex: Int): SpriteFrame {
        return frames[frameIndex % frames.size]
    }

    fun getFrameAtTime(stateTime: Float): SpriteFrame {
        val frameIndex = ((stateTime / frameDuration).toInt()) % frames.size
        return frames[frameIndex]
    }
}

/**
 * Definition of a sprite with its animations.
 */
data class SpriteDefinition(
    val name: String,
    val width: Int,
    val height: Int,
    val animations: MutableMap<String, Animation> = mutableMapOf()
) {
    fun addAnimation(animation: Animation) {
        animations[animation.name] = animation
    }
}

/**
 * Manager for sprite sheets and animations.
 * Reads sprite coordinates from config and creates TextureRegions.
 */
class SpriteSheet(
    private val config: SpriteSheetConfig?,
    private var texture: Texture? = null
) {
    private val sprites = mutableMapOf<String, SpriteDefinition>()
    private var initialized = false

    /**
     * Set the texture to use for sprites.
     * Must be called before initialize().
     */
    fun setTexture(texture: Texture) {
        this.texture = texture
        // Set nearest neighbor filtering for pixel-perfect rendering
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
    }

    /**
     * Initialize sprite definitions from config.
     */
    fun initialize() {
        if (initialized || texture == null) return

        createDeveloperSprites()
        createPMSprites()
        createProductOwnerSprites()
        createThoughtBubbleSprites()
        createGhostSprites()
        createFurnitureSprites()
        createAnimalSprites()
        createTileSprites()

        initialized = true
    }

    private fun createDeveloperSprites() {
        val tex = texture ?: return
        val variants = listOf("blue", "green", "red", "purple")

        for (variant in variants) {
            val sprite = SpriteDefinition(
                name = "developer_$variant",
                width = 16,
                height = 24
            )

            // Get character rect from config
            val rect = config?.getDeveloperCharacter(variant)
            val baseFrame = if (rect != null) {
                SpriteFrame.fromRect(tex, rect)
            } else {
                // Fallback defaults
                val defaults = mapOf(
                    "blue" to (0 to 104),
                    "green" to (16 to 104),
                    "red" to (32 to 104),
                    "purple" to (48 to 104)
                )
                val (x, y) = defaults[variant] ?: (0 to 104)
                SpriteFrame(TextureRegion(tex, x, y, 16, 24), 16, 24)
            }

            // All animations use the same static frame
            val devAnimNames = listOf("idle", "walking_down", "walking_left", "walking_right",
                "walking_up", "typing", "thinking", "despair")
            createStandardAnimations(sprite, baseFrame, devAnimNames)
            sprites[sprite.name] = sprite

            // Create sitting variant if available in config
            val sittingRect = config?.getDeveloperCharacterSitting(variant)
            if (sittingRect != null) {
                val sittingSprite = SpriteDefinition(
                    name = "developer_${variant}_sitting",
                    width = 16,
                    height = 24
                )
                val sittingFrame = SpriteFrame.fromRect(tex, sittingRect)
                createStandardAnimations(sittingSprite, sittingFrame, devAnimNames)
                sprites[sittingSprite.name] = sittingSprite
            }
        }
    }

    private fun createPMSprites() {
        val tex = texture ?: return
        val sprite = SpriteDefinition(
            name = "project_manager",
            width = 16,
            height = 24
        )

        // Get PM character from config
        val pmChar = config?.projectManager ?: "blonde"
        val rect = config?.getCharacter(pmChar)
        val baseFrame = if (rect != null) {
            SpriteFrame.fromRect(tex, rect)
        } else {
            SpriteFrame(TextureRegion(tex, 64, 104, 16, 24), 16, 24) // Default: blonde
        }

        val managerAnimNames = listOf("idle", "walking_down", "walking_left", "walking_right",
            "walking_up", "walking")
        createStandardAnimations(sprite, baseFrame, managerAnimNames)
        sprites[sprite.name] = sprite

        // Create sitting variant if available in config
        val sittingRect = config?.getPMCharacterSitting()
        if (sittingRect != null) {
            val sittingSprite = SpriteDefinition(
                name = "project_manager_sitting",
                width = sittingRect.w,
                height = sittingRect.h
            )
            val sittingFrame = SpriteFrame.fromRect(tex, sittingRect)
            createStandardAnimations(sittingSprite, sittingFrame, managerAnimNames)
            sprites[sittingSprite.name] = sittingSprite
        }
    }

    private fun createProductOwnerSprites() {
        val tex = texture ?: return
        val sprite = SpriteDefinition(
            name = "product_owner",
            width = 16,
            height = 24
        )

        // Get PO character from config
        val poChar = config?.productOwner ?: "dark_hair"
        val rect = config?.getCharacter(poChar)
        val baseFrame = if (rect != null) {
            SpriteFrame.fromRect(tex, rect)
        } else {
            SpriteFrame(TextureRegion(tex, 48, 104, 16, 24), 16, 24) // Default: dark_hair
        }

        val managerAnimNames = listOf("idle", "walking_down", "walking_left", "walking_right",
            "walking_up", "walking")
        createStandardAnimations(sprite, baseFrame, managerAnimNames)
        sprites[sprite.name] = sprite

        // Create sitting variant if available in config
        val sittingRect = config?.getPOCharacterSitting()
        if (sittingRect != null) {
            val sittingSprite = SpriteDefinition(
                name = "product_owner_sitting",
                width = sittingRect.w,
                height = sittingRect.h
            )
            val sittingFrame = SpriteFrame.fromRect(tex, sittingRect)
            createStandardAnimations(sittingSprite, sittingFrame, managerAnimNames)
            sprites[sittingSprite.name] = sittingSprite
        }
    }

    private fun createThoughtBubbleSprites() {
        // Thought bubble is procedurally drawn, but we keep placeholder
        val sprite = SpriteDefinition(
            name = "thought_bubble",
            width = 16,
            height = 16
        )
        sprites[sprite.name] = sprite
    }

    private fun createGhostSprites() {
        // Ghost is procedurally drawn, but we keep placeholder
        val sprite = SpriteDefinition(
            name = "ghost",
            width = 16,
            height = 24
        )
        sprites[sprite.name] = sprite
    }

    private fun createFurnitureSprites() {
        val tex = texture ?: return
        val cfg = config ?: return

        // Create sprites for all furniture items in config
        for ((name, rect) in cfg.furniture) {
            val sprite = SpriteDefinition(name = name, width = rect.w, height = rect.h)
            val frame = SpriteFrame.fromRect(tex, rect)
            sprite.addAnimation(Animation(
                name = "default",
                frames = listOf(frame),
                frameDuration = 1.0f,
                loop = false
            ))
            sprites[name] = sprite
        }
    }

    private fun createAnimalSprites() {
        val tex = texture ?: return
        val cfg = config ?: return

        // Create sprites for all animal items in config
        for ((name, animRect) in cfg.animals) {
            val sprite = SpriteDefinition(name = name, width = animRect.w, height = animRect.h)

            // Extract multiple frames from horizontal strip
            // Frame offset is the full layer width (256px for Aseprite), not sprite width
            val frames = mutableListOf<SpriteFrame>()
            for (frameIndex in 0 until animRect.frames) {
                val frameX = animRect.x + (frameIndex * animRect.frameOffset)
                val region = TextureRegion(tex, frameX, animRect.y, animRect.w, animRect.h)
                frames.add(SpriteFrame(region, animRect.w, animRect.h))
            }

            sprite.addAnimation(Animation(
                name = "default",
                frames = frames,
                frameDuration = animRect.frameDuration,
                loop = true
            ))
            sprites["animal_$name"] = sprite
        }
    }

    private fun createTileSprites() {
        val tex = texture ?: return
        val cfg = config ?: return

        // Mapping from internal sprite names to config keys
        val tileNameMap = mapOf(
            "floor_tile" to "floor_brick",
            "wall_tile" to "wall_tile"
        )

        // Create sprites for mapped tiles
        for ((spriteName, configKey) in tileNameMap) {
            val rect = cfg.getTile(configKey)
            if (rect != null) {
                val sprite = SpriteDefinition(name = spriteName, width = rect.w, height = rect.h)
                val frame = SpriteFrame.fromRect(tex, rect)
                sprite.addAnimation(Animation(
                    name = "default",
                    frames = listOf(frame),
                    frameDuration = 1.0f,
                    loop = false
                ))
                sprites[spriteName] = sprite
            }
        }

        // Also create sprites for all tiles using their config names
        for ((name, rect) in cfg.tiles) {
            if (name !in sprites) {
                val sprite = SpriteDefinition(name = name, width = rect.w, height = rect.h)
                val frame = SpriteFrame.fromRect(tex, rect)
                sprite.addAnimation(Animation(
                    name = "default",
                    frames = listOf(frame),
                    frameDuration = 1.0f,
                    loop = false
                ))
                sprites[name] = sprite
            }
        }
    }

    /**
     * Create a standard set of animations for a character sprite from a single base frame.
     */
    private fun createStandardAnimations(
        sprite: SpriteDefinition,
        baseFrame: SpriteFrame,
        animNames: List<String>
    ) {
        for (animName in animNames) {
            sprite.addAnimation(Animation(
                name = animName,
                frames = listOf(baseFrame),
                frameDuration = if (animName == "idle") 0.5f else 0.15f
            ))
        }
    }

    fun getSprite(name: String): SpriteDefinition? = sprites[name]

    fun getAnimation(spriteName: String, animName: String): Animation? {
        return sprites[spriteName]?.animations?.get(animName)
    }

    fun getDeveloperSpriteName(variant: Int): String {
        val variants = listOf("blue", "green", "red", "purple")
        val variantName = variants[variant % variants.size]
        return "developer_$variantName"
    }

    fun getFurnitureFrame(name: String): SpriteFrame? {
        val sprite = sprites[name] ?: return null
        return sprite.animations["default"]?.frames?.firstOrNull()
    }

    fun getAnimalFrame(name: String): SpriteFrame? {
        val sprite = sprites["animal_$name"] ?: return null
        return sprite.animations["default"]?.frames?.firstOrNull()
    }

    fun getAnimalAnimation(name: String): Animation? {
        val sprite = sprites["animal_$name"] ?: return null
        return sprite.animations["default"]
    }

    fun getTileFrame(name: String): SpriteFrame? = getFurnitureFrame(name)

    fun getCloudList(): List<SpriteRect> {
        return config?.clouds?.ifEmpty {
            listOf(SpriteRect(0, 0, 256, 38))
        } ?: listOf(SpriteRect(0, 0, 256, 38))
    }
}
