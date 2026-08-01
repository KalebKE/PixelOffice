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
data class DisplayConfig(
    val width: Int = 320,
    val height: Int = 240,
    val fps: Int = 30,
    val title: String = "Pixel Office - Agent Visualization"
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
    val colorVariants: List<String> = listOf("blue", "green", "red", "red_hair", "dark_hair")
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

    fun getDeveloperCharacterSittingReading(variant: String): SpriteRect? {
        val charName = developerVariants[variant] ?: return null
        return characters["${charName}_sitting_reading"]
    }

    fun getFurniture(name: String): SpriteRect? = furniture[name]

    fun getTile(name: String): SpriteRect? = tiles[name]
}

@Serializable
data class DeveloperConfig(
    @SerialName("walk_speed")
    val walkSpeed: Float = 40.0f,
    @SerialName("despair_duration")
    val despairDuration: Float = 2.0f,
    @SerialName("idle_patrol_min_seconds")
    val idlePatrolMinSeconds: Float = 20.0f,
    @SerialName("idle_patrol_max_seconds")
    val idlePatrolMaxSeconds: Float = 45.0f,
    @SerialName("idle_patrol_max_stops")
    val idlePatrolMaxStops: Int = 3,
    @SerialName("social_duration_seconds")
    val socialDurationSeconds: Float = 3.0f
)

@Serializable
data class PetConfig(
    val enabled: Boolean = true,
    @SerialName("roam_min_seconds")
    val roamMinSeconds: Float = 60.0f,
    @SerialName("roam_max_seconds")
    val roamMaxSeconds: Float = 120.0f,
    @SerialName("walk_speed")
    val walkSpeed: Float = 20.0f
)

@Serializable
data class UfoConfig(
    val enabled: Boolean = true,
    @SerialName("spawn_chance")
    val spawnChance: Float = 0.3f,
    @SerialName("hover_duration")
    val hoverDuration: Float = 3f,
    @SerialName("beam_duration")
    val beamDuration: Float = 4f
)

@Serializable
data class SkyTrafficConfig(
    val enabled: Boolean = true,
    @SerialName("spawn_interval")
    val spawnInterval: Float = 30f,
    val sprite: String = "sprites/32bit-PaperAirplane",
    @SerialName("frame_count")
    val frameCount: Int = 4,
    val ufo: UfoConfig = UfoConfig()
)

@Serializable
data class DemoConfig(
    val enabled: Boolean = false
)

@Serializable
data class EventReceiverConfig(
    val enabled: Boolean = true,
    val host: String = "127.0.0.1",
    @SerialName("udp_port")
    val udpPort: Int = 9997,
    @SerialName("http_port")
    val httpPort: Int = 3003,
    @SerialName("stale_session_minutes")
    val staleSessionMinutes: Int = 30,
    @SerialName("grid_columns")
    val gridColumns: Int = 2
)

@Serializable
data class Config(
    val display: DisplayConfig = DisplayConfig(),
    val office: OfficeConfig = OfficeConfig(),
    val animation: AnimationConfig = AnimationConfig(),
    val sprites: SpriteConfig = SpriteConfig(),
    @SerialName("sprite_sheet")
    val spriteSheet: SpriteSheetConfig = SpriteSheetConfig(),
    val developer: DeveloperConfig = DeveloperConfig(),
    val pets: PetConfig = PetConfig(),
    val demo: DemoConfig = DemoConfig(),
    @SerialName("sky_traffic")
    val skyTraffic: SkyTrafficConfig = SkyTrafficConfig(),
    val events: EventReceiverConfig = EventReceiverConfig()
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
