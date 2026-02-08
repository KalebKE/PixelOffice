package com.pixeloffice

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureAdapter
import com.pixeloffice.animation.SpriteSheet
import com.pixeloffice.core.Config
import com.pixeloffice.core.EventBus
import com.pixeloffice.network.TmuxReceiver
import com.pixeloffice.parsing.ActivityType
import com.pixeloffice.parsing.DetectedActivity
import com.pixeloffice.parsing.StreamParser
import com.pixeloffice.rendering.GameCamera
import com.pixeloffice.rendering.Renderer
import com.pixeloffice.ui.SettingsConfig
import com.pixeloffice.ui.SettingsOverlay
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
        private const val DEMO_CYCLE_DURATION = 20f
        private const val DEMO_INITIAL_SIT_DURATION = 5f
        private const val DEMO_STAGGER_INTERVAL = 2f
    }

    // Configuration
    private lateinit var config: Config

    // Core systems
    private lateinit var eventBus: EventBus

    // Per-connection parsers and agent mappings
    private val streamParsers = mutableMapOf<String, StreamParser>()
    private val connectionToAgent = mutableMapOf<String, String>()
    private val pendingConnections = mutableSetOf<String>()

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
    private var demoPrevStates = mutableListOf<String>()

    // Settings overlay
    private lateinit var settingsOverlay: SettingsOverlay
    private var settingsOpen = false
    private var settingsConfig = SettingsConfig.fromDefaults()

    // Night mode
    private var nightModeAutomatic = true

    // Touch input
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun create() {
        // Load configuration
        config = Config.load("config.json")

        // Initialize core systems
        eventBus = EventBus()

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
        office.setupDefaultDeskColumns()

        // Initialize rendering
        renderer = Renderer(
            config.display.width,
            config.display.height,
            spriteSheet,
            skyTrafficInterval = config.skyTraffic.spawnInterval,
            skyTrafficEnabled = config.skyTraffic.enabled,
            skyTrafficSprite = config.skyTraffic.sprite,
            skyTrafficFrameCount = config.skyTraffic.frameCount
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

        // Settings overlay
        settingsOverlay = SettingsOverlay(
            onApply = { cfg -> applySettings(cfg) },
            onClose = { toggleSettings() }
        )

        // Demo mode
        demoMode = config.demo.enabled
        if (demoMode) {
            renderer.setDemoMode(true)
        } else {
            // Start network receiver
            receiver.start()
        }

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
                // Check for settings button click (top-right area)
                // The button is drawn at (width-75, height-4) in screen coords
                // Tap coords: x is from left, y is from top (Gdx.input style)
                val screenWidth = config.display.width
                if (x >= screenWidth - 16f && y <= 16f && !settingsOpen) {
                    toggleSettings()
                    return true
                }

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
        receiver.onConnect = { connectionId ->
            receiver.postToGLThread {
                handleNewConnection(connectionId)
            }
        }

        receiver.onDisconnect = { connectionId ->
            receiver.postToGLThread {
                handleDisconnection(connectionId)
            }
        }
    }

    private fun handleNewConnection(connectionId: String) {
        val parser = StreamParser()
        streamParsers[connectionId] = parser

        val agentId = "agent_$connectionId"
        connectionToAgent[connectionId] = agentId
        pendingConnections.add(connectionId)

        renderer.setConnectionCount(receiver.getConnectionCount())
        Gdx.app.log("PixelOffice", "New connection (pending): $connectionId → agent $agentId")
    }

    private fun handleDisconnection(connectionId: String) {
        val wasPending = pendingConnections.remove(connectionId)
        streamParsers.remove(connectionId)

        val agentId = connectionToAgent.remove(connectionId)
        if (agentId != null && !wasPending) {
            office.getDeveloper(agentId)?.handleEvent("idle")
        }

        renderer.setConnectionCount(receiver.getConnectionCount())
        if (wasPending) {
            Gdx.app.log("PixelOffice", "Probe disconnected (no developer spawned): $connectionId")
        } else {
            Gdx.app.log("PixelOffice", "Disconnected: $connectionId (agent $agentId)")
        }
    }

    private fun handleActivity(activity: DetectedActivity) {
        // Record every activity to the tracker before dispatching animations
        val agentId = activity.agentId ?: office.getAllDevelopers().lastOrNull()?.agentId
        if (agentId != null) {
            office.recordAgentActivity(agentId, activity.type, activity.toolName, extractActivityContext(activity))
        }

        when (activity.type) {
            ActivityType.AGENT_SPAWN -> {
                val parentId = activity.agentId ?: "unknown"
                val spawnId = "${parentId}_sub_${office.getAllDevelopers().size}"
                office.spawnDeveloper(spawnId)
            }
            ActivityType.THINKING -> {
                resolveDeveloper(activity)?.handleEvent("thinking_started")
            }
            ActivityType.PLANNING -> {
                resolveDeveloper(activity)?.handleEvent("planning_started")
            }
            ActivityType.FILE_READ, ActivityType.WEB_SEARCH -> {
                resolveDeveloper(activity)?.handleEvent("researching_started")
            }
            ActivityType.CODE_WRITING, ActivityType.CODE_EDITING -> {
                resolveDeveloper(activity)?.handleEvent("code_writing_started")
            }
            ActivityType.TEST_EXECUTION, ActivityType.BUILD_EXECUTION,
            ActivityType.BASH_EXECUTION, ActivityType.COMMITTING,
            ActivityType.INSTALLING_DEPS -> {
                resolveDeveloper(activity)?.handleEvent("command_started")
            }
            ActivityType.TEST_FAILURE, ActivityType.BUILD_FAILURE -> {
                resolveDeveloper(activity)?.handleEvent("tests_failed")
                camera.shake(3f)
            }
            ActivityType.TEST_SUCCESS, ActivityType.BUILD_SUCCESS -> {
                resolveDeveloper(activity)?.handleEvent("command_succeeded")
            }
            ActivityType.USER_QUESTION -> {
                office.getAllDevelopers().lastOrNull()?.let { dev ->
                    office.spawnProductOwner(dev.agentId)
                }
            }
            ActivityType.UNKNOWN -> {
                // Unknown activities don't trigger animations
            }
        }
    }

    /**
     * Resolve the developer for an activity, preferring the activity's agentId.
     */
    private fun resolveDeveloper(activity: DetectedActivity): com.pixeloffice.entities.Developer? {
        val id = activity.agentId
        return if (id != null) office.getDeveloper(id) else office.getAllDevelopers().lastOrNull()
    }

    /**
     * Extract brief context from an activity's details for tracking.
     */
    private fun extractActivityContext(activity: DetectedActivity): String? {
        val details = activity.details ?: return null
        return when (activity.type) {
            ActivityType.CODE_WRITING, ActivityType.CODE_EDITING -> {
                (details["file_path"] as? String)?.substringAfterLast('/')
            }
            ActivityType.FILE_READ -> {
                (details["file_path"] as? String)?.substringAfterLast('/')
                    ?: (details["pattern"] as? String)
            }
            ActivityType.BASH_EXECUTION, ActivityType.TEST_EXECUTION,
            ActivityType.BUILD_EXECUTION, ActivityType.COMMITTING,
            ActivityType.INSTALLING_DEPS -> {
                (details["command"] as? String)?.take(40)
            }
            else -> null
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
            val drained = receiver.drainData()
            for ((connectionId, data) in drained) {
                val parser = streamParsers[connectionId] ?: continue
                val agentId = connectionToAgent[connectionId] ?: continue

                // Spawn developer on first real data (skips TCP probes)
                if (pendingConnections.remove(connectionId)) {
                    office.spawnDeveloper(agentId)
                    Gdx.app.log("PixelOffice", "Developer spawned on first data: $agentId")
                }

                val activities = parser.feed(data)
                for (activity in activities) {
                    activity.agentId = activity.agentId ?: agentId
                    handleActivity(activity)
                }
            }
        } else {
            runDemo(dt)
        }

        // Update systems
        camera.update(dt)
        renderer.update(dt)
        office.update(dt)

        // Night mode auto-detection (synced with procedural sky)
        if (nightModeAutomatic) {
            renderer.nightMode = renderer.isSkyNightTime()
        }

        // Clear and draw
        renderer.clear()

        // Get render data from office
        val renderData = office.getRenderData()
        renderer.drawScene(renderData)

        // Draw settings overlay on top
        if (settingsOpen) {
            settingsOverlay.render()
        }
    }

    private fun handleInput() {
        // Settings overlay intercepts ESC
        if (settingsOpen) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                settingsOverlay.close()
                toggleSettings()
            }
            return
        }

        // Quit
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            Gdx.app.exit()
        }

        // Toggle debug
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            renderer.toggleDebug()
            settingsConfig.debugMode = renderer.showDebug
        }

        // Toggle forced sitting mode (F2)
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            forceSittingMode = !forceSittingMode
            Gdx.app.log("Debug", "Force sitting mode: $forceSittingMode")
        }

        // Cycle label mode (F4)
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            renderer.cycleLabels()
        }

        // Toggle night mode (F5)
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            renderer.nightMode = !renderer.nightMode
            nightModeAutomatic = false
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
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7)) {
                // Toggle PM sitting
                val pm = office.getProjectManager()
                if (pm?.getAssignedDeskId() != null) {
                    pm.clearAssignedDesk()
                } else {
                    val deskId = office.getNextAvailableDeskId()
                    if (deskId != null) office.assignPMToDesk(deskId)
                }
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8)) {
                // Toggle PO sitting
                val po = office.getProductOwner()
                if (po?.getAssignedDeskId() != null) {
                    po.clearAssignedDesk()
                } else {
                    val deskId = office.getNextAvailableDeskId()
                    if (deskId != null) office.assignPOToDesk(deskId)
                }
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_9)) {
                // Cycle through new states: researching → command → celebrating
                val dev = office.getAllDevelopers().lastOrNull()
                if (dev != null) {
                    when (dev.getState()) {
                        "researching" -> dev.handleEvent("command_started")
                        "running_command" -> dev.handleEvent("command_succeeded")
                        else -> dev.handleEvent("researching_started")
                    }
                }
            }
        }
    }

    private fun runDemo(dt: Float) {
        demoTimer += dt

        // Initialize demo on first frame
        if (!demoInitialized && demoTimer > 0.1f) {
            demoInitialized = true
            office.spawnDeveloper("demo_agent_1", colorVariant = 0)
            office.spawnDeveloper("demo_agent_2", colorVariant = 1)
            office.spawnDeveloper("demo_agent_3", colorVariant = 2)

            // Assign PM to available desk with 15s sit-patrol cycle
            val pmDeskId = office.getNextAvailableDeskId()
            if (pmDeskId != null) {
                val pm = office.assignPMToDesk(pmDeskId)
                pm?.sitDuration = 15f
            }

            // Assign PO to available desk (staggered — first sit is 22.5s)
            val poDeskId = office.getNextAvailableDeskId()
            if (poDeskId != null) {
                val po = office.assignPOToDesk(poDeskId)
                po?.sitDuration = 15f
                po?.setSitTimerStart(-7.5f)
            }
        }

        // Cycle through states for all developers with initial sit period and stagger
        val developers = office.getAllDevelopers()
        if (developers.isNotEmpty()) {
            // Ensure per-developer state tracking is sized correctly
            while (demoPrevStates.size < developers.size) {
                demoPrevStates.add("")
            }

            // adjustedTimer accounts for the init delay (0.1s) and sit period
            val adjustedTimer = demoTimer - 0.1f - DEMO_INITIAL_SIT_DURATION

            // During initial sit period, skip all event processing
            if (adjustedTimer >= 0f) {
                developers.forEachIndexed { index, dev ->
                    val devStartTime = index * DEMO_STAGGER_INTERVAL
                    val devTimer = adjustedTimer - devStartTime

                    // This developer hasn't started yet
                    if (devTimer < 0f) return@forEachIndexed

                    val cycleTime = devTimer % DEMO_CYCLE_DURATION
                    val devState = when {
                        cycleTime < 3f -> "thinking_started"
                        cycleTime < 6f -> "walk_to_whiteboard"
                        cycleTime < 9f -> "done"
                        cycleTime < 12f -> "code_writing_started"
                        cycleTime < 15f -> "tests_failed"
                        cycleTime < 18f -> "despair_complete"
                        else -> ""
                    }

                    if (devState.isNotEmpty()) {
                        dev.handleEvent(devState)
                    }

                    // Trigger camera shake on transition to tests_failed
                    if (devState == "tests_failed" && demoPrevStates[index] != "tests_failed") {
                        camera.shake(3f)
                    }
                    demoPrevStates[index] = devState
                }
            }
        }

    }

    private fun toggleSettings() {
        settingsOpen = !settingsOpen
        if (settingsOpen) {
            settingsOverlay.open(settingsConfig)
            Gdx.input.inputProcessor = settingsOverlay.getInputProcessor()
        } else {
            // Restore game input
            setupTouchInput()
        }
    }

    private fun applySettings(cfg: SettingsConfig) {
        settingsConfig = cfg.deepCopy()
        renderer.showDebug = cfg.debugMode
        office.resetAndApply(cfg)
        demoInitialized = true
        renderer.setLineNetwork(office.getLineNetwork().getAllLines())
    }

    override fun dispose() {
        // Stop network receiver
        receiver.stop()

        settingsOverlay.dispose()
        renderer.dispose()
        eventBus.clear()
        Gdx.app.log("PixelOffice", "Game disposed")
    }
}
