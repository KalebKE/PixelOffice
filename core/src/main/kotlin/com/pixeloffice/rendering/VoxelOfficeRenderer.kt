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
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute

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
    private val roomInstances = mutableListOf<ModelInstance>()
    private val debugInstances = mutableListOf<ModelInstance>()
    private var shadowCasterInstances: List<ModelInstance> = emptyList()
    private var nonShadowInstances: List<ModelInstance> = emptyList()
    private var characterBillboard: CharacterBillboard? = null

    private var initialized = false
    private var renderFrameCount = 0
    private var screenshotTaken = false

    companion object {
        private const val TAG = "VoxelOfficeRenderer"
        private val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        private val FLOOR_COLOR = Color(0.93f, 0.93f, 0.94f, 1f)
        private val WALL_COLOR = Color(0.85f, 0.86f, 0.87f, 1f)
        private val BG_COLOR = Color(0.78f, 0.80f, 0.82f, 1f)
    }

    fun initialize() {
        if (initialized) return
        initialized = true

        modelBatch = ModelBatch()
        shadowBatch = ModelBatch(DepthShaderProvider())

        // Camera
        camera = PerspectiveCamera(40f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(13f, 18f, 24f)
        camera.lookAt(13f, 0f, -10f)
        camera.up.set(0f, 1f, 0f)
        camera.near = 0.1f
        camera.far = 100f
        camera.update()

        // Shadow light: slight angle for contact shadows with subtle directional spread
        shadowLight = DirectionalShadowLight(4096, 4096, 40f, 40f, 1f, 60f)
        shadowLight.set(0.35f, 0.34f, 0.33f, -0.2f, -1f, -0.15f)

        // Environment with shadow map
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.57f, 1f))
        environment.add(shadowLight)
        environment.shadowMap = shadowLight
        // Fill light from front-left
        environment.add(DirectionalLight().set(0.35f, 0.34f, 0.33f, 0.2f, -0.6f, -0.8f))
        // Subtle top light for ceiling bounce
        environment.add(DirectionalLight().set(0.12f, 0.12f, 0.13f, 0f, -1f, 0f))

        // Same lighting without shadow map for cubicles (prevents shadow acne on thin geometry)
        noShadowEnv = Environment()
        noShadowEnv.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.57f, 1f))
        noShadowEnv.add(DirectionalLight().set(0.35f, 0.34f, 0.33f, -0.2f, -1f, -0.15f))
        noShadowEnv.add(DirectionalLight().set(0.35f, 0.34f, 0.33f, 0.2f, -0.6f, -0.8f))
        noShadowEnv.add(DirectionalLight().set(0.12f, 0.12f, 0.13f, 0f, -1f, 0f))

        val mb = ModelBuilder()
        val w = VoxelOfficeLayout.OFFICE_WIDTH
        val d = VoxelOfficeLayout.OFFICE_DEPTH
        val wallH = 3.0f
        val wallThick = 0.2f

        // Floor
        val floorMat = Material(ColorAttribute.createDiffuse(FLOOR_COLOR))
        val floorModel = mb.createBox(w, 0.05f, d, floorMat, attrs)
        proceduralModels.add(floorModel)
        roomInstances.add(ModelInstance(floorModel).also {
            it.transform.setToTranslation(w / 2f, -0.025f, -d / 2f)
        })

        val wallMat = Material(ColorAttribute.createDiffuse(WALL_COLOR))

        // Back wall
        val backWall = mb.createBox(w, wallH, wallThick, wallMat, attrs)
        proceduralModels.add(backWall)
        roomInstances.add(ModelInstance(backWall).also {
            it.transform.setToTranslation(w / 2f, wallH / 2f, -d)
        })

        // Left wall
        val leftWall = mb.createBox(wallThick, wallH, d, wallMat, attrs)
        proceduralModels.add(leftWall)
        roomInstances.add(ModelInstance(leftWall).also {
            it.transform.setToTranslation(0f, wallH / 2f, -d / 2f)
        })

        // Right wall
        val rightWall = mb.createBox(wallThick, wallH, d, wallMat, attrs)
        proceduralModels.add(rightWall)
        roomInstances.add(ModelInstance(rightWall).also {
            it.transform.setToTranslation(w, wallH / 2f, -d / 2f)
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
        // Room + debug with shadows
        for (instance in roomInstances) batch.render(instance, environment)
        for (instance in debugInstances) batch.render(instance, environment)
        // Cubicles: no shadow map (prevents shadow acne on thin voxel geometry)
        for (instance in nonShadowInstances) batch.render(instance, noShadowEnv)
        // All other furniture: with shadow map
        for (instance in shadowCasterInstances) batch.render(instance, environment)
    }

    /** All geometry casts shadows (including cubicles for contact shadows on floor) */
    private fun renderShadowCasters(batch: ModelBatch) {
        for (instance in roomInstances) batch.render(instance)
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
