package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.pixeloffice.world.ConveyorPipeline

/**
 * Renders enclosed 3D CI/CD conveyor factories below each office.
 * Each office/project gets its own independent factory instance.
 */
class ConveyorRenderer(
    private val officeWidth: Int = 320,
    private val factoryHeight: Int = 140
) {
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var environment: Environment

    private val models = mutableMapOf<String, Model>()
    private val factoryInstances = mutableMapOf<String, List<ModelInstance>>()

    // Dynamic model templates
    private var cargoBoxModel: Model? = null
    private var scannerLightGreen: Model? = null
    private var scannerLightRed: Model? = null
    private var scannerLightOff: Model? = null

    private val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

    // Kenney conveyor-kit palette
    private val wallMid = Color(0.365f, 0.373f, 0.443f, 1f)
    private val wallLight = Color(0.486f, 0.498f, 0.569f, 1f)
    private val wallDark = Color(0.267f, 0.275f, 0.353f, 1f)
    private val floorGray = Color(0.322f, 0.329f, 0.369f, 1f)
    private val yellow = Color(0.961f, 0.718f, 0.192f, 1f)
    private val orange = Color(0.922f, 0.580f, 0.200f, 1f)
    private val orangeDark = Color(0.710f, 0.396f, 0.114f, 1f)
    private val beltGray = Color(0.447f, 0.451f, 0.498f, 1f)
    private val beltDark = Color(0.353f, 0.357f, 0.400f, 1f)
    private val green = Color(0.243f, 0.769f, 0.478f, 1f)
    private val red = Color(0.878f, 0.251f, 0.251f, 1f)

    // Zone accent colors
    private val zoneLint = Color(0.29f, 0.56f, 0.89f, 1f)
    private val zoneTest = Color(0.24f, 0.77f, 0.48f, 1f)
    private val zoneBuild = Color(0.96f, 0.72f, 0.19f, 1f)
    private val zoneDeploy = Color(0.60f, 0.40f, 0.85f, 1f)

    private fun mat(c: Color) = Material(ColorAttribute.createDiffuse(c))

    fun initialize() {
        modelBatch = ModelBatch()

        // Orthographic isometric camera — no perspective distortion, all factories look identical
        camera = OrthographicCamera()
        camera.position.set(0f, 5f, 5f)
        camera.direction.set(0f, -5f, -5f).nor()
        camera.up.set(0f, 1f, 0f)
        camera.near = 0.1f
        camera.far = 50f
        camera.zoom = 1f
        camera.update()

        // Balanced factory lighting
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.58f, 1f))
        environment.add(DirectionalLight().set(0.7f, 0.68f, 0.65f, -1f, -1.2f, -0.6f))
        environment.add(DirectionalLight().set(0.25f, 0.25f, 0.28f, 0.5f, -0.3f, 0.8f))

        buildModels()
    }

    private fun buildModels() {
        val mb = ModelBuilder()
        var b: ModelBuilder

        // === FLOOR (deep enough to fill the viewport below the factory) ===
        models["floor"] = mb.createBox(9.8f, 0.05f, 8f, mat(floorGray), attrs)

        // === BACK WALL (narrowed to avoid overlap between adjacent factories) ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("panel", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(0f, 1.0f, -2.0f, 9.8f, 2.0f, 0.12f)
        b.part("stripe", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(0f, 2.1f, -2.0f, 9.8f, 0.2f, 0.14f)
        b.part("cap", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 2.25f, -2.0f, 9.8f, 0.08f, 0.14f)
        for (wx in listOf(-2.5f, 0f, 2.5f)) {
            b.part("w$wx", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(wx, 1.2f, -1.93f, 0.8f, 0.6f, 0.06f)
            b.part("wt$wx", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(wx, 1.55f, -1.93f, 0.9f, 0.05f, 0.07f)
            b.part("wb$wx", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(wx, 0.85f, -1.93f, 0.9f, 0.05f, 0.07f)
        }
        models["back-wall"] = b.end()

        // === SIDE WALLS (full height with garage door cutout) ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("wall", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(-4.9f, 1.15f, -0.7f, 0.12f, 2.3f, 2.6f)
        b.part("stripe", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(-4.9f, 2.1f, -0.7f, 0.14f, 0.2f, 2.6f)
        b.part("lintel", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(-4.9f, 1.15f, -0.7f, 0.14f, 0.1f, 1.4f)
        models["wall-left"] = b.end()

        b = ModelBuilder(); b.begin(); b.node()
        b.part("wall", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(4.9f, 1.15f, -0.7f, 0.12f, 2.3f, 2.6f)
        b.part("stripe", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(4.9f, 2.1f, -0.7f, 0.14f, 0.2f, 2.6f)
        b.part("lintel", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(4.9f, 1.15f, -0.7f, 0.14f, 0.1f, 1.4f)
        models["wall-right"] = b.end()

        // === GARAGE DOORS (taller, with frame) ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("panel", GL20.GL_TRIANGLES, attrs, mat(orange)).box(0f, 0.5f, 0f, 0.08f, 1.0f, 1.2f)
        b.part("frame", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 1.05f, 0f, 0.12f, 0.08f, 1.3f)
        for (sy in listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.95f)) {
            b.part("s$sy", GL20.GL_TRIANGLES, attrs, mat(orangeDark)).box(0f, sy, 0f, 0.09f, 0.03f, 1.2f)
        }
        models["garage-door"] = b.end()

        // === FRONT LIP (knee-height wall to close the front) ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("lip", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 0.075f, 0.65f, 9.8f, 0.15f, 0.12f)
        b.part("stripe", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(0f, 0.16f, 0.65f, 9.8f, 0.03f, 0.13f)
        models["front-lip"] = b.end()

        // === CONVEYOR BELT ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("surface", GL20.GL_TRIANGLES, attrs, mat(beltGray)).box(0f, 0.15f, -0.7f, 9f, 0.1f, 0.8f)
        b.part("rail_l", GL20.GL_TRIANGLES, attrs, mat(beltDark)).box(0f, 0.22f, -0.3f, 9f, 0.05f, 0.06f)
        b.part("rail_r", GL20.GL_TRIANGLES, attrs, mat(beltDark)).box(0f, 0.22f, -1.1f, 9f, 0.05f, 0.06f)
        for (i in 0 until 18) {
            val rx = -4.25f + i * 0.5f
            b.part("r$i", GL20.GL_TRIANGLES, attrs, mat(beltDark)).box(rx, 0.21f, -0.7f, 0.04f, 0.02f, 0.7f)
        }
        for (lx in listOf(-3.5f, -1f, 1.5f, 4f)) {
            b.part("lf$lx", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(lx, 0.05f, -0.3f, 0.1f, 0.1f, 0.1f)
            b.part("lb$lx", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(lx, 0.05f, -1.1f, 0.1f, 0.1f, 0.1f)
        }
        models["belt"] = b.end()

        // === ZONE DIVIDERS (thin pipes) ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("pipe", GL20.GL_TRIANGLES, attrs, mat(beltDark)).box(0f, 1.0f, -0.7f, 0.03f, 0.03f, 0.9f)
        b.part("ll", GL20.GL_TRIANGLES, attrs, mat(beltDark)).box(0f, 0.5f, -0.25f, 0.03f, 1.0f, 0.03f)
        b.part("lr", GL20.GL_TRIANGLES, attrs, mat(beltDark)).box(0f, 0.5f, -1.15f, 0.03f, 1.0f, 0.03f)
        models["divider"] = b.end()

        // === ROBOT ARM ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("base", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 0.1f, 0f, 0.3f, 0.2f, 0.3f)
        b.part("lower", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 0.45f, 0f, 0.1f, 0.5f, 0.1f)
        b.part("joint", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(0f, 0.7f, 0f, 0.14f, 0.06f, 0.14f)
        b.part("upper", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0.12f, 0.75f, 0f, 0.28f, 0.08f, 0.08f)
        b.part("grip", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0.26f, 0.68f, 0f, 0.06f, 0.15f, 0.06f)
        models["robot-arm"] = b.end()

        // === SCANNER ARCH ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("pl", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 0.45f, -0.25f, 0.1f, 0.9f, 0.1f)
        b.part("pr", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 0.45f, -1.15f, 0.1f, 0.9f, 0.1f)
        b.part("beam", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 0.95f, -0.7f, 0.12f, 0.12f, 1.0f)
        b.part("scan", GL20.GL_TRIANGLES, attrs, mat(green)).box(0f, 0.22f, -0.7f, 0.02f, 0.02f, 0.8f)
        b.part("accent", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(0f, 1.02f, -0.7f, 0.14f, 0.03f, 1.02f)
        models["scanner"] = b.end()

        // === COVER/ENCLOSURE ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("roof", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(0f, 1.2f, -0.7f, 2.2f, 0.06f, 1.2f)
        b.part("lf", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 1.1f, -0.1f, 2.2f, 0.2f, 0.06f)
        b.part("lb", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 1.1f, -1.3f, 2.2f, 0.2f, 0.06f)
        b.part("sf", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(0f, 1.2f, -0.1f, 2.2f, 0.04f, 0.07f)
        b.part("sb", GL20.GL_TRIANGLES, attrs, mat(yellow)).box(0f, 1.2f, -1.3f, 2.2f, 0.04f, 0.07f)
        b.part("win", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 1.05f, -0.09f, 0.6f, 0.1f, 0.03f)
        models["cover"] = b.end()

        // === HOPPER ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("body", GL20.GL_TRIANGLES, attrs, mat(wallLight)).box(0f, 1.3f, -0.7f, 0.5f, 0.4f, 0.5f)
        b.part("open", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 1.1f, -0.7f, 0.3f, 0.05f, 0.3f)
        b.part("bl", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(0.2f, 1.1f, -0.3f, 0.06f, 0.3f, 0.06f)
        b.part("br", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(-0.2f, 1.1f, -1.1f, 0.06f, 0.3f, 0.06f)
        models["hopper"] = b.end()

        // === MONITOR BEZEL ===
        models["monitor-bezel"] = mb.createBox(0.5f, 0.35f, 0.04f, mat(wallDark), attrs)

        // === VENTILATION ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("body", GL20.GL_TRIANGLES, attrs, mat(wallDark)).box(0f, 2.35f, -1.9f, 0.6f, 0.3f, 0.4f)
        b.part("fan", GL20.GL_TRIANGLES, attrs, mat(wallMid)).box(0f, 2.55f, -1.9f, 0.15f, 0.15f, 0.15f)
        models["vent"] = b.end()

        // === CARGO BOX ===
        b = ModelBuilder(); b.begin(); b.node()
        b.part("box", GL20.GL_TRIANGLES, attrs, mat(orange)).box(0f, 0f, 0f, 0.28f, 0.22f, 0.28f)
        b.part("label", GL20.GL_TRIANGLES, attrs, mat(orangeDark)).box(0f, 0.115f, 0f, 0.28f, 0.01f, 0.06f)
        cargoBoxModel = b.end()

        // === SCANNER LIGHTS ===
        scannerLightGreen = mb.createBox(0.08f, 0.08f, 0.08f, mat(green), attrs)
        scannerLightRed = mb.createBox(0.08f, 0.08f, 0.08f, mat(red), attrs)
        scannerLightOff = mb.createBox(0.08f, 0.08f, 0.08f, mat(wallDark), attrs)

        // === ZONE FLOOR STRIPS ===
        models["zone-lint"] = mb.createBox(2.0f, 0.01f, 0.8f, mat(zoneLint), attrs)
        models["zone-test"] = mb.createBox(2.0f, 0.01f, 0.8f, mat(zoneTest), attrs)
        models["zone-build"] = mb.createBox(2.0f, 0.01f, 0.8f, mat(zoneBuild), attrs)
        models["zone-deploy"] = mb.createBox(2.0f, 0.01f, 0.8f, mat(zoneDeploy), attrs)

        // === ZONE SIGNS (back wall plaques) ===
        models["sign-lint"] = mb.createBox(0.5f, 0.2f, 0.06f, mat(zoneLint), attrs)
        models["sign-test"] = mb.createBox(0.5f, 0.2f, 0.06f, mat(zoneTest), attrs)
        models["sign-build"] = mb.createBox(0.5f, 0.2f, 0.06f, mat(zoneBuild), attrs)
        models["sign-deploy"] = mb.createBox(0.5f, 0.2f, 0.06f, mat(zoneDeploy), attrs)

        // === SIMPLE SHAPES ===
        models["arrow"] = mb.createBox(0.25f, 0.03f, 0.15f, mat(green), attrs)
        models["floor-stripe"] = mb.createBox(0.08f, 0.01f, 0.7f, mat(yellow), attrs)
        models["barrel"] = mb.createBox(0.25f, 0.35f, 0.25f, mat(wallDark), attrs)
        models["crate"] = mb.createBox(0.35f, 0.25f, 0.35f, mat(orangeDark), attrs)
        models["output-box"] = mb.createBox(0.3f, 0.25f, 0.3f, mat(orange), attrs)
        models["monitor-green"] = mb.createBox(0.42f, 0.27f, 0.02f, mat(green), attrs)
        models["monitor-red"] = mb.createBox(0.42f, 0.27f, 0.02f, mat(red), attrs)
        models["monitor-off"] = mb.createBox(0.42f, 0.27f, 0.02f, mat(wallDark), attrs)

        // === LOAD OBJ MODELS (preprocessed to strip non-standard vertex colors) ===
        val objBasePath = "kenney_conveyor-kit/Models/OBJ-fixed/"
        val colormapTexture = Texture(Gdx.files.internal("${objBasePath}Textures/colormap.png"))
        colormapTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)

        val objLoader = ObjLoader()
        val objNames = listOf(
            "conveyor", "conveyor-long", "conveyor-stripe", "conveyor-bars-stripe",
            "scanner-high", "scanner-low",
            "robot-arm-a", "robot-arm-b",
            "box-small", "box-large",
            "floor-large",
            "cover-window", "cover-stripe-window",
            "structure-wall", "structure-window",
            "door-wide-closed", "door-wide-half", "door-wide-open",
            "arrow", "arrow-basic"
        )
        // Map each OBJ model to its Kenney palette color
        val objColors = mapOf(
            "conveyor" to beltGray, "conveyor-long" to beltGray,
            "conveyor-stripe" to beltGray, "conveyor-bars-stripe" to beltGray,
            "scanner-high" to wallLight, "scanner-low" to wallLight,
            "robot-arm-a" to wallLight, "robot-arm-b" to wallLight,
            "box-small" to orange, "box-large" to orange,
            "floor-large" to floorGray,
            "cover-window" to wallMid, "cover-stripe-window" to wallMid,
            "structure-wall" to wallMid, "structure-window" to wallMid,
            "door-wide-closed" to orange, "door-wide-half" to orange, "door-wide-open" to orange,
            "arrow" to yellow, "arrow-basic" to yellow
        )
        for (name in objNames) {
            try {
                val model = objLoader.loadModel(Gdx.files.internal("${objBasePath}${name}.obj"))
                val color = objColors[name] ?: wallLight
                for (material in model.materials) {
                    material.clear()
                    material.set(ColorAttribute.createDiffuse(color))
                }
                models["obj-$name"] = model
                Gdx.app?.log("ConveyorRenderer", "Loaded OBJ: $name")
            } catch (e: Exception) {
                Gdx.app?.log("ConveyorRenderer", "Failed OBJ: $name — ${e.message}")
            }
        }
    }

    /** Build a complete factory layout. Each project gets its own instance list. */
    private fun buildFactoryLayout(): List<ModelInstance> {
        val instances = mutableListOf<ModelInstance>()

        // Prefer OBJ model (prefixed "obj-") over ModelBuilder fallback
        fun add(name: String, x: Float, y: Float, z: Float) {
            val model = models["obj-$name"] ?: models[name] ?: return
            val inst = ModelInstance(model)
            inst.transform.setToTranslation(x, y, z)
            instances.add(inst)
        }
        // Use ModelBuilder model only (no OBJ lookup)
        fun addMB(name: String, x: Float, y: Float, z: Float) {
            val model = models[name] ?: return
            val inst = ModelInstance(model)
            inst.transform.setToTranslation(x, y, z)
            instances.add(inst)
        }

        // Floor (extended forward to fill viewport below factory)
        addMB("floor", 0f, -0.025f, 1.5f)

        // Walls (custom-sized ModelBuilder, not OBJ)
        addMB("back-wall", 0f, 0f, 0f)
        addMB("wall-left", 0f, 0f, 0f)
        addMB("wall-right", 0f, 0f, 0f)
        addMB("front-lip", 0f, 0f, 0f)

        // Garage doors
        models["garage-door"]?.let {
            val left = ModelInstance(it); left.transform.setToTranslation(-4.85f, 0f, -0.7f)
            instances.add(left)
            val right = ModelInstance(it); right.transform.setToTranslation(4.85f, 0f, -0.7f)
            instances.add(right)
        }

        // Conveyor belt — OBJ segments or fallback to ModelBuilder belt
        val beltModel = models["obj-conveyor-bars-stripe"] ?: models["obj-conveyor-stripe"] ?: models["obj-conveyor"]
        if (beltModel != null) {
            for (i in 0 until 9) {
                val inst = ModelInstance(beltModel)
                inst.transform.setToTranslation(i * 1.1f - 4.4f, 0.1f, -0.7f)
                instances.add(inst)
            }
        } else {
            addMB("belt", 0f, 0f, 0f)
        }

        // Zone dividers
        for (dx in listOf(-2.2f, 0f, 2.2f)) addMB("divider", dx, 0f, 0f)

        // Zone floor strips (colored overlays)
        addMB("zone-lint", -3.25f, 0.005f, -0.7f)
        addMB("zone-test", -1.1f, 0.005f, -0.7f)
        addMB("zone-build", 1.1f, 0.005f, -0.7f)
        addMB("zone-deploy", 3.35f, 0.005f, -0.7f)

        // Zone signs on back wall
        addMB("sign-lint", -3.25f, 0.55f, -1.93f)
        addMB("sign-test", -1.1f, 0.55f, -1.93f)
        addMB("sign-build", 1.1f, 0.55f, -1.93f)
        addMB("sign-deploy", 3.35f, 0.55f, -1.93f)

        // === STAGE 1: LINT/BUILD (OBJ robot arms + cover) ===
        add("robot-arm-a", -3.5f, 0f, -1.5f)
        add("robot-arm-b", -3.0f, 0f, 0.05f)
        add("cover-stripe-window", -3.35f, 0f, -0.7f)

        // === STAGE 2: TEST (OBJ scanners) ===
        add("scanner-high", -1.5f, 0f, -0.7f)
        add("scanner-low", -0.7f, 0f, -0.7f)

        // === STAGE 3: BUILD/COMPILE ===
        add("robot-arm-a", 1.0f, 0f, -1.5f)
        addMB("hopper", 1.0f, 0f, 0f)
        addMB("monitor-bezel", 1.5f, 1.3f, -1.94f)

        // === STAGE 4: DEPLOY ===
        add("scanner-high", 3.0f, 0f, -0.7f)
        add("box-small", 4.5f, 0.125f, -1.5f)
        add("box-small", 4.2f, 0.125f, -1.6f)
        add("box-small", 4.35f, 0.375f, -1.55f)
        add("arrow", 4.8f, 0.25f, -0.7f)

        // Roof elements
        addMB("vent", -1.5f, 0f, 0f)
        addMB("vent", 2.0f, 0f, 0f)

        // Ambient details
        for (sx in listOf(-4.8f, -4.6f, 4.6f, 4.8f)) addMB("floor-stripe", sx, 0.01f, -0.7f)
        addMB("barrel", -4.0f, 0.175f, -1.7f)
        addMB("barrel", -4.3f, 0.175f, -1.8f)
        addMB("crate", 3.8f, 0.125f, -1.7f)
        addMB("crate", -0.5f, 0.125f, -1.7f)
        add("box-small", -4.8f, 0.125f, -0.4f)
        add("box-small", -4.6f, 0.125f, -0.6f)
        add("box-large", -4.5f, 0.125f, -1.5f)

        return instances
    }

    /** Get or create factory instances for a project. */
    private fun getFactoryInstances(projectId: String): List<ModelInstance> {
        return factoryInstances.getOrPut(projectId) { buildFactoryLayout() }
    }

    /** Remove factory instances for projects that no longer exist. */
    fun retainFactories(activeIds: Set<String>) {
        factoryInstances.keys.retainAll(activeIds)
    }

    fun render(projectId: String, pipeline: ConveyorPipeline, screenX: Int, screenY: Int, viewWidth: Int, viewHeight: Int) {
        Gdx.gl.glViewport(screenX, screenY, viewWidth, viewHeight)
        Gdx.gl.glScissor(screenX, screenY, viewWidth, viewHeight)
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)

        Gdx.gl.glClearColor(0.08f, 0.09f, 0.14f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)

        // Scale ortho viewport to fill the factory strip — tighter framing
        val aspect = viewWidth.toFloat() / viewHeight.toFloat()
        camera.viewportWidth = 5f * aspect
        camera.viewportHeight = 5f
        camera.update()

        modelBatch.begin(camera)

        // Render this project's static factory
        for (instance in getFactoryInstances(projectId)) {
            modelBatch.render(instance, environment)
        }

        // === DYNAMIC: Cargo box ===
        if (pipeline.boxActive && cargoBoxModel != null) {
            val boxInst = ModelInstance(cargoBoxModel!!)
            val boxX = -4.3f + pipeline.boxProgress * 8.6f
            boxInst.transform.setToTranslation(boxX, 0.31f, -0.7f)
            modelBatch.render(boxInst, environment)
        }

        // === DYNAMIC: Scanner lights ===
        val testStatus = pipeline.stages.getOrNull(1)?.status ?: ConveyorPipeline.StageStatus.IDLE
        val lightModel = when (testStatus) {
            ConveyorPipeline.StageStatus.PASSED, ConveyorPipeline.StageStatus.RUNNING -> scannerLightGreen
            ConveyorPipeline.StageStatus.FAILED -> scannerLightRed
            else -> scannerLightOff
        }
        lightModel?.let { model ->
            for (sx in listOf(-1.5f, -0.7f, 3.0f)) {
                val light = ModelInstance(model)
                light.transform.setToTranslation(sx, 0.85f, -0.7f)
                modelBatch.render(light, environment)
            }
        }

        // === DYNAMIC: Status monitor ===
        val buildStatus = pipeline.stages.getOrNull(2)?.status ?: ConveyorPipeline.StageStatus.IDLE
        val screenName = when (buildStatus) {
            ConveyorPipeline.StageStatus.RUNNING, ConveyorPipeline.StageStatus.PASSED -> "monitor-green"
            ConveyorPipeline.StageStatus.FAILED -> "monitor-red"
            else -> "monitor-off"
        }
        models[screenName]?.let {
            val screen = ModelInstance(it)
            screen.transform.setToTranslation(1.5f, 1.3f, -1.92f)
            modelBatch.render(screen, environment)
        }

        modelBatch.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
    }

    fun dispose() {
        modelBatch.dispose()
        for (model in models.values) model.dispose()
        cargoBoxModel?.dispose()
        scannerLightGreen?.dispose()
        scannerLightRed?.dispose()
        scannerLightOff?.dispose()
    }
}
