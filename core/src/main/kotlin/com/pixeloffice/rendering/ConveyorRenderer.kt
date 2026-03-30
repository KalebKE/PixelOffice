package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.pixeloffice.world.ConveyorPipeline

/**
 * Renders 3D conveyor factory scenes below each office.
 * Uses programmatic ModelBuilder geometry (OBJ loader has vertex format issues).
 * Colors match the Kenney conveyor-kit style.
 */
class ConveyorRenderer(
    private val officeWidth: Int = 320,
    private val factoryHeight: Int = 80
) {
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: PerspectiveCamera
    private lateinit var environment: Environment
    private val models = mutableMapOf<String, Model>()
    private val instances = mutableListOf<ModelInstance>()

    fun initialize() {
        modelBatch = ModelBatch()

        // Side-view camera
        camera = PerspectiveCamera(25f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(0f, 5f, 8f)
        camera.lookAt(0f, 0.5f, 0f)
        camera.near = 0.1f
        camera.far = 100f
        camera.update()

        // Standard lighting
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.4f))
        environment.add(DirectionalLight().set(0.3f, 0.3f, 0.3f, 1f, -0.5f, 0.2f))

        // Build all conveyor models programmatically
        val mb = ModelBuilder()
        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        // Kenney conveyor-kit color palette
        val beltGray = Color(0.45f, 0.45f, 0.52f, 1f)        // blue-gray conveyor belt
        val beltStripe = Color(0.85f, 0.65f, 0.15f, 1f)       // yellow warning stripe
        val frameGray = Color(0.35f, 0.35f, 0.42f, 1f)        // darker frame/rail
        val boxOrange = Color(0.92f, 0.58f, 0.2f, 1f)         // orange cargo box
        val scannerBlue = Color(0.4f, 0.45f, 0.6f, 1f)        // scanner arch
        val armGray = Color(0.55f, 0.55f, 0.6f, 1f)           // robot arm
        val armJoint = Color(0.7f, 0.7f, 0.2f, 1f)            // yellow joint
        val floorGray = Color(0.32f, 0.33f, 0.38f, 1f)        // factory floor
        val greenLight = Color(0.15f, 0.85f, 0.25f, 1f)       // pass indicator
        val redLight = Color(0.9f, 0.15f, 0.15f, 1f)          // fail indicator
        val arrowGreen = Color(0.2f, 0.75f, 0.3f, 1f)         // direction arrow

        fun mat(c: Color) = Material(ColorAttribute.createDiffuse(c))

        // Belt segment: flat with side rails
        val beltMb = ModelBuilder()
        beltMb.begin()
        val beltNode = beltMb.node()
        // Belt surface
        beltMb.part("belt", GL20.GL_TRIANGLES, attrs, mat(beltGray))
            .box(0f, 0.075f, 0f, 1f, 0.08f, 0.6f)
        // Side rails
        beltMb.part("rail_l", GL20.GL_TRIANGLES, attrs, mat(frameGray))
            .box(0f, 0.12f, 0.35f, 1f, 0.04f, 0.06f)
        beltMb.part("rail_r", GL20.GL_TRIANGLES, attrs, mat(frameGray))
            .box(0f, 0.12f, -0.35f, 1f, 0.04f, 0.06f)
        // Warning stripe
        beltMb.part("stripe", GL20.GL_TRIANGLES, attrs, mat(beltStripe))
            .box(0f, 0.08f, 0f, 0.15f, 0.02f, 0.5f)
        models["belt"] = beltMb.end()

        // Scanner arch
        val scanMb = ModelBuilder()
        scanMb.begin()
        scanMb.node()
        scanMb.part("base_l", GL20.GL_TRIANGLES, attrs, mat(scannerBlue))
            .box(-0.05f, 0.3f, 0.4f, 0.1f, 0.6f, 0.1f)
        scanMb.part("base_r", GL20.GL_TRIANGLES, attrs, mat(scannerBlue))
            .box(-0.05f, 0.3f, -0.4f, 0.1f, 0.6f, 0.1f)
        scanMb.part("arch", GL20.GL_TRIANGLES, attrs, mat(scannerBlue))
            .box(-0.05f, 0.65f, 0f, 0.1f, 0.1f, 0.9f)
        scanMb.part("light", GL20.GL_TRIANGLES, attrs, mat(greenLight))
            .box(-0.05f, 0.55f, 0f, 0.06f, 0.06f, 0.06f)
        models["scanner"] = scanMb.end()

        // Robot arm
        val armMb = ModelBuilder()
        armMb.begin()
        armMb.node()
        armMb.part("base", GL20.GL_TRIANGLES, attrs, mat(armGray))
            .box(0f, 0.08f, 0f, 0.25f, 0.16f, 0.25f)
        armMb.part("arm_lower", GL20.GL_TRIANGLES, attrs, mat(armGray))
            .box(0f, 0.35f, 0f, 0.1f, 0.4f, 0.1f)
        armMb.part("joint", GL20.GL_TRIANGLES, attrs, mat(armJoint))
            .box(0f, 0.55f, 0f, 0.12f, 0.05f, 0.12f)
        armMb.part("arm_upper", GL20.GL_TRIANGLES, attrs, mat(armGray))
            .box(0.1f, 0.65f, 0f, 0.25f, 0.08f, 0.08f)
        models["robot-arm"] = armMb.end()

        // Cargo box
        models["box"] = mb.createBox(0.3f, 0.25f, 0.3f, mat(boxOrange), attrs)

        // Floor tile
        models["floor"] = mb.createBox(2f, 0.04f, 2f, mat(floorGray), attrs)

        // Direction arrow (flat triangle-ish)
        models["arrow"] = mb.createBox(0.2f, 0.05f, 0.3f, mat(arrowGreen), attrs)

        // Status lights
        models["green-light"] = mb.createBox(0.12f, 0.12f, 0.12f, mat(greenLight), attrs)
        models["red-light"] = mb.createBox(0.12f, 0.12f, 0.12f, mat(redLight), attrs)

        buildStaticLayout()
    }

    private fun buildStaticLayout() {
        instances.clear()

        fun add(name: String, x: Float, y: Float, z: Float) {
            models[name]?.let {
                val inst = ModelInstance(it)
                inst.transform.setToTranslation(x, y, z)
                instances.add(inst)
            }
        }

        // Floor tiles
        for (i in 0 until 5) {
            add("floor", i * 2f - 4f, 0f, 0f)
        }

        // Conveyor belt segments
        for (i in 0 until 9) {
            add("belt", i * 1.1f - 4.5f, 0f, 0f)
        }

        // Station 1: Scanner (lint)
        add("scanner", -3f, 0f, 0f)

        // Station 2: Robot arm (test)
        add("robot-arm", -1f, 0f, 0.7f)

        // Cargo boxes
        for (x in listOf(-2f, 0.5f, 2.5f)) {
            add("box", x, 0.22f, 0f)
        }

        // Station 3: Robot arm (build)
        add("robot-arm", 1.5f, 0f, 0.7f)

        // Station 4: Scanner (deploy)
        add("scanner", 3.5f, 0f, 0f)

        // Direction arrows
        add("arrow", 4.8f, 0.15f, 0f)
        add("arrow", -4.8f, 0.15f, 0f)
    }

    fun render(pipeline: ConveyorPipeline, screenX: Int, screenY: Int, viewWidth: Int, viewHeight: Int) {
        Gdx.gl.glViewport(screenX, screenY, viewWidth, viewHeight)
        Gdx.gl.glScissor(screenX, screenY, viewWidth, viewHeight)
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)

        Gdx.gl.glClearColor(0.12f, 0.12f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)

        camera.viewportWidth = viewWidth.toFloat()
        camera.viewportHeight = viewHeight.toFloat()
        camera.update()

        modelBatch.begin(camera)
        for (instance in instances) {
            modelBatch.render(instance, environment)
        }
        modelBatch.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
    }

    fun dispose() {
        modelBatch.dispose()
        for (model in models.values) {
            model.dispose()
        }
    }
}
