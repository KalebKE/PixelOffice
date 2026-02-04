package com.pixeloffice

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureAdapter
import com.pixeloffice.animation.SpriteSheet
import com.pixeloffice.core.Config
import com.pixeloffice.core.EventBus
import com.pixeloffice.core.EventType
import com.pixeloffice.network.TmuxReceiver
import com.pixeloffice.parsing.ActivityType
import com.pixeloffice.parsing.DetectedActivity
import com.pixeloffice.parsing.StreamParser
import com.pixeloffice.rendering.GameCamera
import com.pixeloffice.rendering.Renderer
import com.pixeloffice.world.Office

/**
 * Main libGDX application for Pixel Office.
 *
 * Orchestrates the network receiver, stream parser, office simulation,
 * and rendering.
 */
class PixelOfficeGame : ApplicationAdapter() {

    companion object {
        var forceSittingMode = false
    }

    // Configuration
    private lateinit var config: Config

    // Core systems
    private lateinit var eventBus: EventBus
    private lateinit var streamParser: StreamParser

    // Network
    private lateinit var receiver: TmuxReceiver

    // Sprites and animation
    private lateinit var spriteSheet: SpriteSheet

    // World
    private lateinit var office: Office

    // Rendering
    private lateinit var renderer: Renderer
    private lateinit var camera: GameCamera

    // Timing
    private var frameCount = 0
    private var fpsTimer = 0f

    // Demo mode
    private var demoMode = false
    private var demoTimer = 0f
    private var demoInitialized = false
    private var demoPrevState = ""

    // Touch input
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun create() {
        // Load configuration
        config = Config.load("config.json")

        // Initialize core systems
        eventBus = EventBus()
        streamParser = StreamParser()

        // Initialize network
        receiver = TmuxReceiver(
            host = config.network.host,
            port = config.network.port,
            bufferSize = config.network.bufferSize,
            reconnectDelay = config.network.reconnectDelay
        )
        setupNetworkCallbacks()

        // Initialize sprite sheet (without texture yet)
        spriteSheet = SpriteSheet(config.spriteSheet)

        // Initialize world
        office = Office(config)

        // Initialize rendering
        renderer = Renderer(
            config.display.width,
            config.display.height,
            spriteSheet
        )
        renderer.initialize()
        renderer.setWalkableZones(config.office.walkableZones)
        renderer.setLineNetwork(office.getLineNetwork().getAllLines())

        // Initialize camera
        camera = GameCamera(
            config.display.width,
            config.display.height,
            config.display.width,  // World size = screen size for now
            config.display.height
        )
        renderer.setCamera(camera)

        // Demo mode
        demoMode = config.demo.enabled
        if (demoMode) {
            renderer.setConnectionStatus("Demo Mode")
        } else {
            // Start network receiver
            receiver.start()
        }

        // Set up event handlers
        setupEventHandlers()

        // Set up touch input for mobile
        setupTouchInput()

        Gdx.app.log("PixelOffice", "Game initialized")
    }

    private fun setupTouchInput() {
        val gestureListener = object : GestureAdapter() {
            override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
                // Pan camera with inverted Y for natural feel
                camera.move(-deltaX * camera.zoom, deltaY * camera.zoom)
                return true
            }

            override fun zoom(initialDistance: Float, distance: Float): Boolean {
                // Pinch to zoom
                val ratio = initialDistance / distance
                camera.setZoom(camera.zoom * ratio)
                return true
            }

            override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
                // Double-tap to toggle debug
                if (count == 2) {
                    renderer.toggleDebug()
                    return true
                }
                return false
            }
        }

        val inputMultiplexer = InputMultiplexer()
        inputMultiplexer.addProcessor(GestureDetector(gestureListener))
        Gdx.input.inputProcessor = inputMultiplexer
    }

    private fun setupNetworkCallbacks() {
        receiver.onConnect = {
            // Post to GL thread for thread safety
            receiver.postToGLThread {
                renderer.setConnectionStatus("Connected")
                eventBus.emit(EventType.CONNECTION_ESTABLISHED)
            }
        }

        receiver.onDisconnect = {
            receiver.postToGLThread {
                renderer.setConnectionStatus("Disconnected")
                eventBus.emit(EventType.CONNECTION_LOST)
            }
        }

        receiver.onData = { data ->
            receiver.postToGLThread {
                eventBus.emit(EventType.DATA_RECEIVED, mapOf("data" to data))
            }
        }
    }

    private fun setupEventHandlers() {
        eventBus.subscribe(EventType.DATA_RECEIVED) { event ->
            handleData(event.data["data"] as? String ?: "")
        }
    }

    private fun handleData(data: String) {
        for (activity in streamParser.feed(data)) {
            handleActivity(activity)
        }
    }

    private fun handleActivity(activity: DetectedActivity) {
        when (activity.type) {
            ActivityType.AGENT_SPAWN -> {
                // Spawn a new developer
                val agentId = activity.agentId ?: "agent_${office.getAllDevelopers().size}"
                office.spawnDeveloper(agentId)
            }
            ActivityType.THINKING -> {
                // Developer starts thinking
                val agentId = activity.agentId
                val dev = if (agentId != null) {
                    office.getDeveloper(agentId)
                } else {
                    office.getAllDevelopers().lastOrNull()
                }
                dev?.handleEvent("thinking_started")
            }
            ActivityType.CODE_WRITING, ActivityType.CODE_EDITING -> {
                // Developer starts coding
                val agentId = activity.agentId
                val dev = if (agentId != null) {
                    office.getDeveloper(agentId)
                } else {
                    office.getAllDevelopers().lastOrNull()
                }
                dev?.handleEvent("code_writing_started")
            }
            ActivityType.TEST_FAILURE -> {
                // Tests failed - trigger despair and camera shake
                office.getAllDevelopers().lastOrNull()?.handleEvent("tests_failed")
                camera.shake(3f)
            }
            ActivityType.TEST_SUCCESS -> {
                // Tests passed - back to idle
                office.getAllDevelopers().lastOrNull()?.handleEvent("code_writing_ended")
            }
            ActivityType.USER_QUESTION -> {
                // Spawn PO for user question
                office.getAllDevelopers().lastOrNull()?.let { dev ->
                    office.spawnProductOwner(dev.agentId)
                }
            }
            else -> {
                // Other activities don't trigger specific animations
            }
        }
    }

    override fun render() {
        // Calculate delta time
        val dt = Gdx.graphics.deltaTime.coerceAtMost(0.1f)

        // Update FPS counter
        frameCount++
        fpsTimer += dt
        if (fpsTimer >= 1.0f) {
            renderer.setFps(frameCount)
            frameCount = 0
            fpsTimer = 0f
        }

        // Handle input
        handleInput()

        // Process network data (if not demo mode)
        if (!demoMode) {
            for (data in receiver.drainData()) {
                handleData(data)
            }
        } else {
            runDemo(dt)
        }

        // Update systems
        camera.update(dt)
        renderer.update(dt)
        office.update(dt)

        // Clear and draw
        renderer.clear()

        // Get render data from office
        val renderData = office.getRenderData()
        renderer.drawScene(renderData)
    }

    private fun handleInput() {
        // Quit
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            Gdx.app.exit()
        }

        // Toggle debug
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            renderer.toggleDebug()
        }

        // Toggle forced sitting mode (F2)
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            forceSittingMode = !forceSittingMode
            Gdx.app.log("Debug", "Force sitting mode: $forceSittingMode")
        }

        // Camera controls
        val cameraSpeed = 100f * Gdx.graphics.deltaTime

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.move(-cameraSpeed, 0f)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.move(cameraSpeed, 0f)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.move(0f, -cameraSpeed)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.move(0f, cameraSpeed)
        }

        // Zoom
        if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS) ||
            Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ADD)) {
            camera.zoomIn()
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) ||
            Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            camera.zoomOut()
        }

        // Demo mode: manual controls
        if (demoMode) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                // Spawn a new developer
                val devCount = office.getAllDevelopers().size
                office.spawnDeveloper("agent_${devCount + 1}")
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                // Trigger thinking
                office.getAllDevelopers().lastOrNull()?.handleEvent("thinking_started")
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
                // Trigger coding
                office.getAllDevelopers().lastOrNull()?.handleEvent("code_writing_started")
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
                // Trigger test failure with camera shake
                office.getAllDevelopers().lastOrNull()?.handleEvent("tests_failed")
                camera.shake(3f)
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) {
                // Spawn Product Owner to ask question to last developer
                office.getAllDevelopers().lastOrNull()?.let { dev ->
                    office.spawnProductOwner(dev.agentId)
                }
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6)) {
                // Dismiss Product Owner (simulate answer received)
                office.dismissProductOwner()
            }
        }
    }

    private fun runDemo(dt: Float) {
        demoTimer += dt

        // Initialize demo on first frame
        if (!demoInitialized && demoTimer > 0.1f) {
            demoInitialized = true
            // Spawn first developer (blue variant at desk_1)
            office.spawnDeveloper("demo_agent_1", colorVariant = 0)
            // Spawn second developer (green/glasses variant at desk_2)
            office.spawnDeveloper("demo_agent_2", colorVariant = 1)
            // Spawn third developer (red/cool_hair variant at desk_3)
            office.spawnDeveloper("demo_agent_3", colorVariant = 2)
            // Spawn project manager
            office.spawnProjectManager()
            // Spawn product owner for patrol
            office.spawnProductOwnerPatrol()
        }

        // Cycle through states for all developers
        val developers = office.getAllDevelopers()
        if (developers.isNotEmpty()) {
            val cycleTime = demoTimer % 20f

            val newState = when {
                cycleTime < 3f -> "thinking_started"
                cycleTime < 6f -> "walk_to_whiteboard"
                cycleTime < 9f -> "done"
                cycleTime < 12f -> "code_writing_started"
                cycleTime < 15f -> "tests_failed"
                cycleTime < 18f -> "despair_complete"
                else -> ""
            }

            if (newState.isNotEmpty()) {
                // Send event to developers with staggered timing
                developers.forEachIndexed { index, dev ->
                    // Stagger by 1 second per developer
                    val staggeredCycleTime = (demoTimer - index * 1f) % 20f
                    val staggeredState = when {
                        staggeredCycleTime < 0f -> "" // Not started yet
                        staggeredCycleTime < 3f -> "thinking_started"
                        staggeredCycleTime < 6f -> "walk_to_whiteboard"
                        staggeredCycleTime < 9f -> "done"
                        staggeredCycleTime < 12f -> "code_writing_started"
                        staggeredCycleTime < 15f -> "tests_failed"
                        staggeredCycleTime < 18f -> "despair_complete"
                        else -> ""
                    }
                    if (staggeredState.isNotEmpty()) {
                        dev.handleEvent(staggeredState)
                    }
                }

                // Trigger camera shake only when transitioning TO tests_failed
                if (newState == "tests_failed" && demoPrevState != "tests_failed") {
                    camera.shake(3f)
                }
                demoPrevState = newState
            }
        }
    }

    override fun dispose() {
        // Stop network receiver
        receiver.stop()

        renderer.dispose()
        eventBus.clear()
        Gdx.app.log("PixelOffice", "Game disposed")
    }
}
