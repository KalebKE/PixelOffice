# Sprite System and Configuration Guide

This document explains how to set up sprite sheets, configure animations, and use the rendering system in Pixel Office.

## Table of Contents

- [Overview](#overview)
- [Directory Structure](#directory-structure)
- [Sprite Sheet Setup](#sprite-sheet-setup)
- [Configuration (config.json)](#configuration-configjson)
- [Core Classes](#core-classes)
- [Adding New Sprites](#adding-new-sprites)
- [Animation System](#animation-system)

---

## Overview

Pixel Office uses a single PNG sprite sheet (`PixelOfficeAssets.png`) combined with a JSON configuration file (`config.json`) that defines sprite coordinates, animation frames, and game settings. The system supports:

- Static sprites (furniture, tiles, decorations)
- Single-frame character sprites with directional facing
- Multi-frame animated sprites (animals with variable frame counts)

---

## Directory Structure

```
pixel-office-libgdx/
├── assets/
│   ├── config.json              # All game configuration
│   └── sprites/
│       └── PixelOfficeAssets.png  # Main sprite sheet
├── core/src/main/kotlin/com/pixeloffice/
│   ├── animation/
│   │   └── SpriteSheet.kt       # Sprite extraction and animation
│   ├── core/
│   │   └── Config.kt            # Configuration data classes
│   └── rendering/
│       └── Renderer.kt          # Drawing and scene composition
```

---

## Sprite Sheet Setup

### Creating the Sprite Sheet

1. **Use a pixel art editor** (Aseprite, Piskel, etc.) to create your sprites
2. **Export as a single PNG** with all sprites arranged in a grid or packed layout
3. **Use nearest-neighbor filtering** - the engine automatically sets this for pixel-perfect rendering
4. **Note the coordinates** (x, y, width, height) of each sprite region

### Sprite Sheet Requirements

- **Format**: PNG with transparency
- **Location**: `assets/sprites/PixelOfficeAssets.png`
- **Coordinate System**: Origin (0,0) is top-left of the image

### Animated Sprites (Horizontal Strips)

For animated sprites, arrange frames horizontally in a strip:

```
┌─────┬─────┬─────┬─────┬─────┐
│  0  │  1  │  2  │  3  │  4  │  ← 5-frame dog animation
└─────┴─────┴─────┴─────┴─────┘
  24px  24px  24px  24px  24px
```

The config specifies the first frame's position, and the system calculates subsequent frames by adding `width` to the x-coordinate.

---

## Configuration (config.json)

The configuration file defines all sprite coordinates, animation settings, and game parameters.

### Generated Office Appearance

Assignable desks are furnished deterministically from the event project ID.
Each nine-desk office keeps a balanced mix of five computers and four monitors,
uses every available chair color, and distributes wall art with some empty
spaces. Working desktops do not also receive loose books or mugs; those items
are reserved for equipment-free surfaces. This is generated at runtime, so a
project keeps the same appearance across restarts without a persisted layout
file.

Each project also receives a coordinated accent palette. Sunset, Forest,
Ocean, and Slate select the couch sprite, trash-can sprite, lava-lamp base,
animated lava, and nighttime glow together. Both lounge areas within an office
use the same palette.

The taller three-row desk block and shorter two-row lounge block can exchange
physical sides per project. This orientation is independently seeded, so it
does not reshuffle desk furnishings or accent colors. The fourth-row wall and
tree stay with the desk block; the lower printer table and lava lamp stay with
the lounge block.

The cat and dog are office entities rather than fixed renderer decorations.
They start at distinct, project-specific desk or lounge anchors and use the
office navigation graph when roaming. Their timing can be configured with:

```json
"pets": {
  "enabled": true,
  "roam_min_seconds": 60.0,
  "roam_max_seconds": 120.0,
  "walk_speed": 20.0
}
```

### Sprite Sheet Section

```json
{
  "sprite_sheet": {
    "transparent_color": 12,
    "palette": [0, 1908563, ...],

    "characters": {
      "blue_shirt": {"x": 2, "y": 105, "w": 15, "h": 23},
      "glasses": {"x": 40, "y": 107, "w": 13, "h": 21}
    },

    "developer_variants": {
      "blue": "blue_shirt",
      "green": "glasses",
      "red": "cool_hair",
      "red_hair": "red_hair",
      "dark_hair": "dark_hair"
    },

    "animals": {
      "cat": {"x": 65, "y": 129, "w": 16, "h": 13, "frames": 2, "frame_duration": 0.3},
      "dog": {"x": 59, "y": 146, "w": 24, "h": 11, "frames": 5, "frame_duration": 0.15}
    },

    "furniture": {
      "desk_left": {"x": 188, "y": 63, "w": 17, "h": 19},
      "chair_black": {"x": 71, "y": 41, "w": 11, "h": 22}
    },

    "tiles": {
      "floor_brick": {"x": 3, "y": 68, "w": 73, "h": 24},
      "wall_tile": {"x": 84, "y": 70, "w": 26, "h": 20}
    },

    "clouds": [
      {"x": 0, "y": 0, "w": 256, "h": 38}
    ]
  }
}
```

### Sprite Rect Types

#### Basic SpriteRect (Static Sprites)
```json
{"x": 188, "y": 63, "w": 17, "h": 19}
```
- `x`, `y`: Top-left corner in sprite sheet
- `w`, `h`: Width and height in pixels

#### AnimatedSpriteRect (Animated Sprites)
```json
{"x": 59, "y": 146, "w": 24, "h": 11, "frames": 5, "frame_duration": 0.15}
```
- `x`, `y`: Top-left corner of **first frame**
- `w`, `h`: Width and height of **each frame**
- `frames`: Number of animation frames (default: 1)
- `frame_duration`: Seconds per frame (default: 0.15)

### Animation Configuration

```json
{
  "animation": {
    "idle_frame_duration": 0.5,
    "walk_frame_duration": 0.15,
    "typing_frame_duration": 0.1,
    "thinking_frame_duration": 0.3,
    "ghost_frame_duration": 0.15
  }
}
```

### Display Configuration

```json
{
  "display": {
    "width": 320,
    "height": 240,
    "fps": 30,
    "title": "Pixel Office - Agent Visualization"
  }
}
```

---

## Core Classes

### Config.kt

Defines data classes for JSON deserialization using Kotlin Serialization.

```kotlin
// Basic sprite rectangle
data class SpriteRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

// Animated sprite rectangle with frame info
data class AnimatedSpriteRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val frames: Int = 1,
    val frameDuration: Float = 0.15f
)

// Sprite sheet configuration
data class SpriteSheetConfig(
    val characters: Map<String, SpriteRect>,
    val animals: Map<String, AnimatedSpriteRect>,
    val furniture: Map<String, SpriteRect>,
    val tiles: Map<String, SpriteRect>,
    // ...
)
```

**Usage:**
```kotlin
val config = Config.load("config.json")
val dogSprite = config.spriteSheet.animals["dog"]
```

### SpriteSheet.kt

Manages sprite extraction from the texture and creates animations.

```kotlin
// Single frame from sprite sheet
data class SpriteFrame(
    val region: TextureRegion,
    val width: Int,
    val height: Int
)

// Animation sequence
data class Animation(
    val name: String,
    val frames: List<SpriteFrame>,
    val frameDuration: Float,
    val loop: Boolean = true
) {
    fun getFrameAtTime(stateTime: Float): SpriteFrame
}

// Sprite definition with multiple animations
data class SpriteDefinition(
    val name: String,
    val width: Int,
    val height: Int,
    val animations: MutableMap<String, Animation>
)

// Main sprite sheet manager
class SpriteSheet(config: SpriteSheetConfig, developerVariants: List<String>) {
    fun setTexture(texture: Texture)
    fun initialize()

    // Retrieval methods
    fun getSprite(name: String): SpriteDefinition?
    fun getAnimation(spriteName: String, animName: String): Animation?
    fun getFurnitureFrame(name: String): SpriteFrame?
    fun getAnimalFrame(name: String): SpriteFrame?
    fun getAnimalAnimation(name: String): Animation?
    fun getTileFrame(name: String): SpriteFrame?
}
```

**Usage:**
```kotlin
val spriteSheet = SpriteSheet(config.spriteSheet, config.sprites.colorVariants)
spriteSheet.setTexture(texture)
spriteSheet.initialize()

// Get static frame
val chairFrame = spriteSheet.getFurnitureFrame("chair_black")

// Get animated frame at specific time
val dogAnim = spriteSheet.getAnimalAnimation("dog")
val currentFrame = dogAnim?.getFrameAtTime(elapsedTime)
```

### Renderer.kt

Handles all drawing operations with Y-coordinate conversion (world uses Y-down, libGDX uses Y-up).

```kotlin
class Renderer(
    width: Int,
    height: Int,
    spriteSheet: SpriteSheet
) {
    fun initialize()
    fun update(dt: Float)
    fun clear(color: Color = Colors.SKY_BLUE)

    // Batch operations
    fun beginBatch()
    fun endBatch()

    // Drawing methods
    fun drawSprite(worldX: Float, worldY: Float, frame: SpriteFrame, flipX: Boolean = false)
    fun drawDeveloper(worldX: Float, worldY: Float, animation: String, facing: String, variant: Int, entityId: String)
    fun drawCharacter(worldX: Float, worldY: Float, spriteName: String, animation: String, facing: String, entityId: String)

    // Furniture drawing
    fun drawDeskLeft(worldX: Float, worldY: Float)
    fun drawChair(worldX: Float, worldY: Float)
    // ... many more furniture methods

    // Animal drawing (animated)
    fun drawDog(worldX: Float, worldY: Float)
    fun drawCat(worldX: Float, worldY: Float)

    // Scene composition
    fun drawBackground()
    fun drawFloor()
    fun drawScene(renderData: Map<String, Any>)

    fun dispose()
}
```

**Usage:**
```kotlin
val renderer = Renderer(320, 240, spriteSheet)
renderer.initialize()

// Game loop
renderer.update(deltaTime)
renderer.clear()
renderer.beginBatch()
renderer.drawDog(100f, 150f)  // Automatically animates
renderer.drawDeskLeft(50f, 100f)
renderer.endBatch()
```

---

## Adding New Sprites

### Adding Static Furniture

1. **Add sprite to PNG** at a known position
2. **Update config.json**:
   ```json
   "furniture": {
     "new_lamp": {"x": 100, "y": 200, "w": 12, "h": 24}
   }
   ```
3. **Add draw method to Renderer.kt**:
   ```kotlin
   fun drawLamp(worldX: Float, worldY: Float) {
       val frame = spriteSheet.getFurnitureFrame("new_lamp") ?: return
       val screenY = flipY(worldY, frame.height)
       batch.draw(frame.region, worldX, screenY)
   }
   ```

### Adding Animated Sprites

1. **Create horizontal sprite strip** in PNG (frames side by side)
2. **Update config.json** with animation data:
   ```json
   "animals": {
     "bird": {"x": 100, "y": 50, "w": 16, "h": 16, "frames": 4, "frame_duration": 0.1}
   }
   ```
3. **Add draw method to Renderer.kt**:
   ```kotlin
   fun drawBird(worldX: Float, worldY: Float) {
       val anim = spriteSheet.getAnimalAnimation("bird")
       val frame = anim?.getFrameAtTime(time) ?: spriteSheet.getAnimalFrame("bird") ?: return
       val screenY = flipY(worldY, frame.height)
       batch.draw(frame.region, worldX, screenY)
   }
   ```

### Adding Character Variants

1. **Add character sprite to PNG**
2. **Update config.json**:
   ```json
   "characters": {
     "purple_shirt": {"x": 60, "y": 105, "w": 15, "h": 23}
   },
   "developer_variants": {
     "blue": "blue_shirt",
     "green": "glasses",
     "red": "cool_hair",
     "purple": "purple_shirt"
   }
   ```
3. Characters are automatically handled by `createDeveloperSprites()` in SpriteSheet.kt

---

## Animation System

### Time-Based Animation

The animation system uses elapsed time to determine which frame to display:

```kotlin
// In Animation class
fun getFrameAtTime(stateTime: Float): SpriteFrame {
    val frameIndex = ((stateTime / frameDuration).toInt()) % frames.size
    return frames[frameIndex]
}
```

### Animation Loop

The Renderer tracks elapsed time and passes it to animations:

```kotlin
class Renderer {
    private var time = 0f

    fun update(dt: Float) {
        time += dt
    }

    fun drawDog(worldX: Float, worldY: Float) {
        val anim = spriteSheet.getAnimalAnimation("dog")
        val frame = anim?.getFrameAtTime(time) ?: return
        // draw frame...
    }
}
```

### Character Bobbing

Walking characters have a bobbing animation based on sine wave:

```kotlin
private fun getBobOffset(entityId: String, isWalking: Boolean): Float {
    if (!isWalking) return 0f
    val phaseOffset = (entityId.hashCode() % 100) / 100f * Math.PI.toFloat() * 2
    return (sin(time * BOB_SPEED + phaseOffset) * BOB_AMPLITUDE).toFloat()
}
```

---

## Coordinate System

### World Coordinates (Y-Down)
- Origin (0,0) at top-left
- Y increases downward
- Used in config.json and game logic

### Screen Coordinates (Y-Up)
- Origin (0,0) at bottom-left
- Y increases upward
- Used by libGDX rendering

### Conversion
```kotlin
private fun flipY(worldY: Float, spriteHeight: Int = 0): Float {
    return height - worldY - spriteHeight
}
```

---

## Best Practices

1. **Use power-of-two textures** when possible for better GPU compatibility
2. **Pack sprites tightly** to minimize texture memory usage
3. **Group related sprites** together in the sheet for easier maintenance
4. **Use consistent frame sizes** for animated sprites
5. **Test with `frames: 1`** to verify static rendering before adding animation
6. **Keep frame durations reasonable** (0.1-0.5 seconds for most animations)

---

## Troubleshooting

### Sprite not appearing
- Verify coordinates in config.json match the actual sprite position
- Check that the sprite name matches exactly (case-sensitive)
- Ensure `spriteSheet.initialize()` is called after `setTexture()`

### Animation not playing
- Verify `frames` > 1 in config
- Check that `renderer.update(dt)` is called each frame
- Ensure you're using `getFrameAtTime(time)` not `getFrame(0)`

### Sprite appears stretched/squished
- Verify width and height match the actual sprite dimensions
- Check that texture filtering is set to Nearest (not Linear)

### Wrong frame showing
- Verify frame order in sprite sheet (left to right)
- Check `frame_duration` value (in seconds, not milliseconds)
