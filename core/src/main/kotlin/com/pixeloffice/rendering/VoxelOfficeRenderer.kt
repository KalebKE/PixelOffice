package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable

/**
 * Renders the 3D voxel office with two-pass shadow mapping.
 *
 * Uses libGDX ModelBatch + DirectionalShadowLight for reliable shadow map
 * generation, with PBR material attributes for better surface response.
 *
 * Two-pass rendering:
 * 1. Depth pass: render scene from light's POV into shadow map
 * 2. Main pass: render scene with shadows applied via environment.shadowMap
 */
class VoxelOfficeRenderer : Disposable {

    private lateinit var modelBatch: ModelBatch
    private lateinit var shadowBatch: ModelBatch
    private lateinit var camera: PerspectiveCamera
    private lateinit var environment: Environment
    private lateinit var noShadowEnv: Environment
    private lateinit var shadowLight: DirectionalShadowLight

    private val catalog = VoxelAssetCatalog()
    private val layout = VoxelOfficeLayout(catalog)

    private val proceduralModels = mutableListOf<Model>()
    private val floorInstances = mutableListOf<ModelInstance>()
    private val wallInstances = mutableListOf<ModelInstance>()
    private val debugInstances = mutableListOf<ModelInstance>()
    private var shadowCasterInstances: List<ModelInstance> = emptyList()
    private var nonShadowInstances: List<ModelInstance> = emptyList()
    private var characterBillboard: CharacterBillboard? = null

    private var initialized = false
    private var renderFrameCount = 0
    private var screenshotTaken = false

    companion object {
        private const val TAG = "VoxelOfficeRenderer"
        private val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.ColorUnpacked).toLong()

        // AO darkening values
        private const val AO_EDGE = 0.82f     // where one surface meets another
        private const val AO_CORNER = 0.65f   // where two+ surfaces meet
        private const val AO_NONE = 1.0f      // no occlusion
        private const val AO_FADE = 3.0f      // units from wall edge where AO fades

        private val FLOOR_COLOR = Color(0.93f, 0.93f, 0.94f, 1f)
        private val WALL_COLOR = Color(0.85f, 0.86f, 0.87f, 1f)
        private val BG_COLOR = Color(0.78f, 0.80f, 0.82f, 1f)

        /** Build a subdivided floor with AO darkening at edges (near walls) */
        fun buildFloorWithAO(mb: ModelBuilder, w: Float, d: Float, mat: Material): Model {
            val segsX = 8
            val segsZ = 8
            mb.begin()
            val mpb = mb.part("floor", GL20.GL_TRIANGLES, attrs, mat)
            val normal = Vector3(0f, 1f, 0f)

            for (iz in 0 until segsZ) {
                for (ix in 0 until segsX) {
                    val x0 = (ix.toFloat() / segsX) * w
                    val x1 = ((ix + 1).toFloat() / segsX) * w
                    val z0 = -(iz.toFloat() / segsZ) * d
                    val z1 = -((iz + 1).toFloat() / segsZ) * d

                    fun aoAt(x: Float, z: Float): Color {
                        val distLeft = x
                        val distRight = w - x
                        val distBackWall = d + z    // z goes from 0 to -d, so d+z = distance from back wall
                        val minDistX = minOf(distLeft, distRight)
                        val minDistZ = distBackWall  // only back wall matters (no front wall)
                        val minDist = minOf(minDistX, minDistZ)

                        val ao = if (minDist >= AO_FADE) AO_NONE
                        else {
                            val t = minDist / AO_FADE
                            // Check if in corner (near two walls)
                            val nearX = minDistX < AO_FADE
                            val nearZ = minDistZ < AO_FADE
                            val target = if (nearX && nearZ) AO_CORNER else AO_EDGE
                            target + (AO_NONE - target) * t
                        }
                        return Color(ao, ao, ao, 1f)
                    }

                    val c00 = aoAt(x0, z0)
                    val c10 = aoAt(x1, z0)
                    val c01 = aoAt(x0, z1)
                    val c11 = aoAt(x1, z1)
                    val y = -0.025f

                    // Two triangles per quad
                    val v0 = mpb.vertex(Vector3(x0, y, z0), normal, c00, null)
                    val v1 = mpb.vertex(Vector3(x1, y, z0), normal, c10, null)
                    val v2 = mpb.vertex(Vector3(x1, y, z1), normal, c11, null)
                    val v3 = mpb.vertex(Vector3(x0, y, z1), normal, c01, null)
                    mpb.index(v0, v1, v2)
                    mpb.index(v0, v2, v3)
                }
            }
            return mb.end()
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true

        // Custom per-pixel lighting shader (eliminates Gouraud banding, adds 9-tap PCF shadows)
        val vertShader = Gdx.files.internal("shaders/voxel.vert").readString()
        val fragShader = Gdx.files.internal("shaders/voxel.frag").readString()
        val shaderConfig = DefaultShader.Config(vertShader, fragShader)
        modelBatch = ModelBatch(DefaultShaderProvider(shaderConfig))
        shadowBatch = ModelBatch(DepthShaderProvider())

        // Camera
        camera = PerspectiveCamera(40f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(13f, 18f, 24f)
        camera.lookAt(13f, 0f, -10f)
        camera.up.set(0f, 1f, 0f)
        camera.near = 0.1f
        camera.far = 100f
        camera.update()

        // Office ceiling lighting: high ambient (diffuse fluorescent bounce) + subtle downward directional
        // Shadow light: nearly straight down for contact shadows
        shadowLight = DirectionalShadowLight(4096, 4096, 40f, 40f, 1f, 60f)
        shadowLight.set(0.25f, 0.25f, 0.24f, -0.05f, -1f, -0.05f)

        // Environment with shadow map
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.75f, 0.75f, 0.77f, 1f))
        environment.add(shadowLight)
        environment.shadowMap = shadowLight
        // Subtle ceiling fill
        environment.add(DirectionalLight().set(0.15f, 0.15f, 0.15f, 0f, -1f, 0f))

        // Same lighting without shadow map for walls/cubicles
        noShadowEnv = Environment()
        noShadowEnv.set(ColorAttribute(ColorAttribute.AmbientLight, 0.75f, 0.75f, 0.77f, 1f))
        noShadowEnv.add(DirectionalLight().set(0.25f, 0.25f, 0.24f, -0.05f, -1f, -0.05f))
        noShadowEnv.add(DirectionalLight().set(0.15f, 0.15f, 0.15f, 0f, -1f, 0f))

        val mb = ModelBuilder()
        val w = VoxelOfficeLayout.OFFICE_WIDTH
        val d = VoxelOfficeLayout.OFFICE_DEPTH
        val wallH = 3.0f
        val wallThick = 0.2f

        // Floor with vertex color AO (darkening at wall edges and corners)
        val floorMat = Material(ColorAttribute.createDiffuse(FLOOR_COLOR))
        val floorModel = buildFloorWithAO(mb, w, d, floorMat)
        proceduralModels.add(floorModel)
        floorInstances.add(ModelInstance(floorModel))

        val wallMat = Material(ColorAttribute.createDiffuse(WALL_COLOR))

        // Back wall (no shadow receive — prevents shadow map edge artifacts)
        val backWall = mb.createBox(w, wallH, wallThick, wallMat, attrs)
        proceduralModels.add(backWall)
        wallInstances.add(ModelInstance(backWall).also {
            it.transform.setToTranslation(w / 2f, wallH / 2f, -d)
        })

        // Left wall — extend 4 units past Z=0 so south end face is behind camera
        val sideWallLen = d + 4f
        val leftWall = mb.createBox(wallThick, wallH, sideWallLen, wallMat, attrs)
        proceduralModels.add(leftWall)
        wallInstances.add(ModelInstance(leftWall).also {
            it.transform.setToTranslation(0f, wallH / 2f, -(sideWallLen / 2f) + 2f)
        })

        // Right wall
        val rightWall = mb.createBox(wallThick, wallH, sideWallLen, wallMat, attrs)
        proceduralModels.add(rightWall)
        wallInstances.add(ModelInstance(rightWall).also {
            it.transform.setToTranslation(w, wallH / 2f, -(sideWallLen / 2f) + 2f)
        })

        Gdx.app?.log(TAG, "Room built with shadow mapping (4096x4096 shadow map)")

        // Debug markers
        fun debugBox(color: Color, x: Float, y: Float, z: Float, label: String) {
            val model = mb.createBox(0.4f, 0.4f, 0.4f, Material(ColorAttribute.createDiffuse(color)), attrs)
            proceduralModels.add(model)
            debugInstances.add(ModelInstance(model).also { it.transform.setToTranslation(x, y, z) })
            Gdx.app?.log(TAG, "DEBUG MARKER [$label] at ($x, $y, $z)")
        }
        debugBox(Color.RED, 0f, 0.2f, 0f, "FRONT-LEFT corner")
        debugBox(Color.GREEN, w / 2f, 0.2f, -d / 2f, "CENTER")
        debugBox(Color.BLUE, w, 0.2f, -d, "BACK-RIGHT corner")
        debugBox(Color.YELLOW, w / 2f, 0.2f, -d, "BACK WALL CENTER")
        debugBox(Color.CYAN, 0f, 0.2f, -d, "BACK-LEFT corner")
        debugBox(Color.MAGENTA, w, 0.2f, 0f, "FRONT-RIGHT corner")

        // Load models and build layout
        catalog.loadAll()
        layout.buildStaticLayout()

        // Split: cubicles don't receive shadows (prevents acne), everything else does
        val (cubicles, others) = layout.splitByCubicle()
        nonShadowInstances = cubicles.mapNotNull { p ->
            catalog.createInstance(p.assetPath)?.also { instance ->
                instance.transform.setToTranslation(p.x, p.y, p.z)
                if (p.rotY != 0f) instance.transform.rotate(Vector3.Y, p.rotY)
            }
        }
        shadowCasterInstances = others.mapNotNull { p ->
            catalog.createInstance(p.assetPath)?.also { instance ->
                instance.transform.setToTranslation(p.x, p.y, p.z)
                if (p.rotY != 0f) instance.transform.rotate(Vector3.Y, p.rotY)
            }
        }
        Gdx.app?.log(TAG, "Office built: ${shadowCasterInstances.size} shadow casters, ${nonShadowInstances.size} cubicles (no shadow receive)")

        characterBillboard = CharacterBillboard(camera)
    }

    private fun renderMainPass(batch: ModelBatch) {
        // Floor receives shadows
        for (instance in floorInstances) batch.render(instance, environment)
        for (instance in debugInstances) batch.render(instance, environment)
        // Walls + cubicles: no shadow map (prevents edge artifacts and acne)
        for (instance in wallInstances) batch.render(instance, noShadowEnv)
        for (instance in nonShadowInstances) batch.render(instance, noShadowEnv)
        // All other furniture: with shadow map
        for (instance in shadowCasterInstances) batch.render(instance, environment)
    }

    /** All geometry casts shadows (including cubicles/walls for contact shadows on floor) */
    private fun renderShadowCasters(batch: ModelBatch) {
        for (instance in floorInstances) batch.render(instance)
        for (instance in wallInstances) batch.render(instance)
        for (instance in shadowCasterInstances) batch.render(instance)
        for (instance in nonShadowInstances) batch.render(instance)
    }

    fun render(renderData: RenderData) {
        if (!initialized) return

        val width = Gdx.graphics.width
        val height = Gdx.graphics.height

        // === Pass 1: Shadow depth map ===
        shadowLight.begin(Vector3.Zero, camera.direction)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_FRONT)
        Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL)
        Gdx.gl.glPolygonOffset(50f, 100f)
        shadowBatch.begin(shadowLight.camera)
        renderShadowCasters(shadowBatch)
        shadowBatch.end()
        Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        shadowLight.end()

        // === Pass 2: Main render with shadows ===
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()

        modelBatch.begin(camera)
        renderMainPass(modelBatch)
        modelBatch.end()

        // Auto-screenshot
        renderFrameCount++
        if (renderFrameCount == 10 && !screenshotTaken) {
            screenshotTaken = true
            try {
                Gdx.app?.log(TAG, "Screenshot resolution: ${width}x${height}")
                val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)
                PixmapIO.writePNG(Gdx.files.absolute("/tmp/voxel-office-debug.png"), pixmap)
                pixmap.dispose()
                Gdx.app?.log(TAG, "Screenshot saved to /tmp/voxel-office-debug.png")
            } catch (e: Exception) {
                Gdx.app?.log(TAG, "Screenshot failed: ${e.message}")
            }
        }

        // Character labels
        characterBillboard?.render(renderData)

        // Restore 2D GL state
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
    }

    fun handleCameraInput() {
        if (!initialized) return

        val speed = 10f * Gdx.graphics.deltaTime
        val right = Vector3(camera.direction).crs(camera.up).nor()
        val forward = Vector3(camera.direction.x, 0f, camera.direction.z).nor()
        val move = Vector3()

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  move.add(Vector3(right).scl(-speed))
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) move.add(Vector3(right).scl(speed))
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    move.add(Vector3(forward).scl(speed))
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))   move.add(Vector3(forward).scl(-speed))

        if (move.len2() > 0f) {
            camera.position.add(move)
            camera.update()
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS) ||
            Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ADD)) {
            camera.position.add(Vector3(camera.direction).nor().scl(2f))
            camera.update()
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) ||
            Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            camera.position.add(Vector3(camera.direction).nor().scl(-2f))
            camera.update()
        }
    }

    override fun dispose() {
        if (!initialized) return
        modelBatch.dispose()
        shadowBatch.dispose()
        shadowLight.dispose()
        for (model in proceduralModels) model.dispose()
        catalog.dispose()
        characterBillboard?.dispose()
    }
}
