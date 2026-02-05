package com.pixeloffice.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle

/**
 * Configuration data classes matching config.yaml structure.
 * Uses Kotlin Serialization for JSON parsing.
 */

@Serializable
data class NetworkConfig(
    val host: String = "localhost",
    val port: Int = 9999,
    @SerialName("buffer_size")
    val bufferSize: Int = 4096,
    @SerialName("reconnect_delay")
    val reconnectDelay: Float = 5.0f
)

@Serializable
data class DisplayConfig(
    val width: Int = 320,
    val height: Int = 240,
    val fps: Int = 30,
    val title: String = "Pixel Office - Claude Code Visualization"
)

@Serializable
data class Position(
    val x: Int,
    val y: Int,
    val id: String = ""
)

@Serializable
data class CollisionRect(
    val x: Int = 0,
    val y: Int = 0,
    val w: Int,
    val h: Int
)

@Serializable
data class FurnitureItem(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val collision: CollisionRect? = null
)

@Serializable
data class WalkableZone(
    val id: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

@Serializable
data class OfficeConfig(
    @SerialName("tile_size")
    val tileSize: Int = 16,
    @SerialName("desk_positions")
    val deskPositions: List<Position> = emptyList(),
    @SerialName("whiteboard_positions")
    val whiteboardPositions: List<Position> = emptyList(),
    @SerialName("pm_patrol_path")
    val pmPatrolPath: List<Position> = emptyList(),
    val furniture: List<FurnitureItem> = emptyList(),
    @SerialName("walkable_zones")
    val walkableZones: List<WalkableZone> = emptyList()
)

@Serializable
data class AnimationConfig(
    @SerialName("idle_frame_duration")
    val idleFrameDuration: Float = 0.5f,
    @SerialName("walk_frame_duration")
    val walkFrameDuration: Float = 0.15f,
    @SerialName("typing_frame_duration")
    val typingFrameDuration: Float = 0.1f,
    @SerialName("thinking_frame_duration")
    val thinkingFrameDuration: Float = 0.3f,
    @SerialName("ghost_frame_duration")
    val ghostFrameDuration: Float = 0.15f
)

@Serializable
data class SpriteConfig(
    @SerialName("character_width")
    val characterWidth: Int = 16,
    @SerialName("character_height")
    val characterHeight: Int = 24,
    @SerialName("desk_width")
    val deskWidth: Int = 48,
    @SerialName("desk_height")
    val deskHeight: Int = 32,
    @SerialName("whiteboard_width")
    val whiteboardWidth: Int = 24,
    @SerialName("whiteboard_height")
    val whiteboardHeight: Int = 48,
    @SerialName("effect_size")
    val effectSize: Int = 16,
    @SerialName("tile_size")
    val tileSize: Int = 16,
    @SerialName("color_variants")
    val colorVariants: List<String> = listOf("blue", "green", "red")
)

@Serializable
data class SpriteRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

@Serializable
data class AnimatedSpriteRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val frames: Int = 1,
    @SerialName("frame_duration")
    val frameDuration: Float = 0.15f,
    @SerialName("frame_offset")
    val frameOffset: Int = 256  // Default to Aseprite layer width
)

@Serializable
data class SpriteSheetConfig(
    @SerialName("transparent_color")
    val transparentColor: Int = 12,
    val palette: List<Int> = emptyList(),
    val characters: Map<String, SpriteRect> = emptyMap(),
    @SerialName("developer_variants")
    val developerVariants: Map<String, String> = emptyMap(),
    @SerialName("project_manager")
    val projectManager: String = "blonde",
    @SerialName("product_owner")
    val productOwner: String = "dark_hair",
    val animals: Map<String, AnimatedSpriteRect> = emptyMap(),
    val furniture: Map<String, SpriteRect> = emptyMap(),
    val tiles: Map<String, SpriteRect> = emptyMap(),
    val clouds: List<SpriteRect> = emptyList()
) {
    fun getCharacter(name: String): SpriteRect? = characters[name]

    fun getDeveloperCharacter(variant: String): SpriteRect? {
        val charName = developerVariants[variant] ?: return null
        return characters[charName]
    }

    fun getDeveloperCharacterSitting(variant: String): SpriteRect? {
        val charName = developerVariants[variant] ?: return null
        val sittingName = "${charName}_sitting"
        return characters[sittingName]
    }

    fun getFurniture(name: String): SpriteRect? = furniture[name]

    fun getTile(name: String): SpriteRect? = tiles[name]

    fun getPMCharacter(): SpriteRect? = characters[projectManager]

    fun getPMCharacterSitting(): SpriteRect? {
        val sittingName = "${projectManager}_sitting"
        return characters[sittingName]
    }

    fun getPOCharacter(): SpriteRect? = characters[productOwner]

    fun getPOCharacterSitting(): SpriteRect? {
        val sittingName = "${productOwner}_sitting"
        return characters[sittingName]
    }
}

@Serializable
data class DeveloperConfig(
    @SerialName("walk_speed")
    val walkSpeed: Float = 40.0f,
    @SerialName("thinking_duration")
    val thinkingDuration: Float = 3.0f,
    @SerialName("despair_duration")
    val despairDuration: Float = 2.0f
)

@Serializable
data class PMConfig(
    @SerialName("patrol_speed")
    val patrolSpeed: Float = 30.0f,
    @SerialName("interrupt_chance")
    val interruptChance: Float = 0.1f,
    @SerialName("interrupt_duration")
    val interruptDuration: Float = 3.0f
)

@Serializable
data class POConfig(
    @SerialName("walk_speed")
    val walkSpeed: Float = 35.0f,
    @SerialName("question_timeout")
    val questionTimeout: Float = 30.0f
)

@Serializable
data class DemoConfig(
    val enabled: Boolean = false,
    @SerialName("sample_events_file")
    val sampleEventsFile: String = "tests/fixtures/sample_events.json"
)

@Serializable
data class Config(
    val network: NetworkConfig = NetworkConfig(),
    val display: DisplayConfig = DisplayConfig(),
    val office: OfficeConfig = OfficeConfig(),
    val animation: AnimationConfig = AnimationConfig(),
    val sprites: SpriteConfig = SpriteConfig(),
    @SerialName("sprite_sheet")
    val spriteSheet: SpriteSheetConfig = SpriteSheetConfig(),
    val developer: DeveloperConfig = DeveloperConfig(),
    @SerialName("project_manager")
    val projectManager: PMConfig = PMConfig(),
    @SerialName("product_owner")
    val productOwner: POConfig = POConfig(),
    val demo: DemoConfig = DemoConfig()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Load configuration from a JSON file.
         * Falls back to defaults if file doesn't exist.
         */
        fun load(path: String = "config.json"): Config {
            return try {
                val file: FileHandle = Gdx.files.internal(path)
                if (file.exists()) {
                    json.decodeFromString<Config>(file.readString())
                } else {
                    Config()
                }
            } catch (e: Exception) {
                Gdx.app?.log("Config", "Failed to load config: ${e.message}")
                Config()
            }
        }
    }

    /**
     * Get the first available desk position.
     */
    fun getAvailableDesk(occupiedIds: Set<String>): Position? {
        return office.deskPositions.firstOrNull { it.id !in occupiedIds }
    }

    /**
     * Get the first available whiteboard position.
     */
    fun getAvailableWhiteboard(occupiedIds: Set<String>): Position? {
        return office.whiteboardPositions.firstOrNull { it.id !in occupiedIds }
    }
}
