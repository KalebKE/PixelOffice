package com.pixeloffice

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureAdapter
import com.badlogic.gdx.math.Matrix4
import com.pixeloffice.animation.SpriteSheet
import com.pixeloffice.core.Config
import com.pixeloffice.integration.AgentSessionStore
import com.pixeloffice.network.AgentEventReceiver
import com.pixeloffice.world.OfficeGrid
import com.pixeloffice.rendering.GameCamera
import com.pixeloffice.rendering.Renderer
import com.pixeloffice.ui.SettingsConfig
import com.pixeloffice.ui.SettingsOverlay
import com.pixeloffice.world.Office

/**
 * Main libGDX application for Pixel Office.
 *
 * Orchestrates structured agent events, the office simulation, and rendering.
 */
class PixelOfficeGame(
    private val onWindowLayoutChanged: (
        width: Int,
        height: Int,
        fixedSky: Boolean
    ) -> Unit = { _, _, _ -> },
    private val onWindowResizeRequested: (
        worldWidth: Int,
        worldHeight: Int,
        fixedSky: Boolean,
        requestedWidth: Int,
        requestedHeight: Int
    ) -> Unit = { _, _, _, _, _ -> }
) : ApplicationAdapter() {

    companion object {
        var forceSittingMode = false
        private const val DEMO_CYCLE_DURATION = 20f
        private const val DEMO_INITIAL_SIT_DURATION = 5f
        private const val DEMO_STAGGER_INTERVAL = 2f
        private const val STALE_CHECK_INTERVAL_SECONDS = 5f
    }

    // Configuration
    private lateinit var config: Config

    // Structured agent events
    private var eventReceiver: AgentEventReceiver? = null
    private var sessionStore: AgentSessionStore? = null
    private var staleCheckTimer = 0f

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

    private var officeGrid: OfficeGrid? = null

    // Night mode
    private var nightModeAutomatic = true

    override fun create() {
        // Load configuration
        config = Config.load("config.json")

        // Initialize sprite sheet (without texture yet)
        spriteSheet = SpriteSheet(config.spriteSheet, config.sprites.colorVariants)

        // Initialize world
        office = Office(config)
        settingsConfig = office.setupDefaultDeskColumns()

        // Initialize rendering
        renderer = Renderer(
            config.display.width,
            config.display.height,
            spriteSheet,
            skyTrafficInterval = config.skyTraffic.spawnInterval,
            skyTrafficEnabled = config.skyTraffic.enabled,
            skyTrafficSprite = config.skyTraffic.sprite,
            skyTrafficFrameCount = config.skyTraffic.frameCount,
            ufoConfig = config.skyTraffic.ufo
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
        } else if (config.events.enabled) {
            sessionStore = AgentSessionStore(
                staleAfterMs = config.events.staleSessionMinutes.coerceAtLeast(1) * 60_000L
            )
            officeGrid = OfficeGrid(config, columns = config.events.gridColumns)
            officeGrid?.let { grid ->
                renderer.resizeGrid(
                    Gdx.graphics.width,
                    Gdx.graphics.height,
                    grid.worldWidth,
                    grid.officeWorldHeight
                )
            }
            eventReceiver = AgentEventReceiver(
                host = config.events.host,
                udpPort = config.events.udpPort,
                httpPort = config.events.httpPort
            ).also { it.start() }
            Gdx.app.log(
                "PixelOffice",
                "Agent events listening on ${config.events.host}:${config.events.udpPort}/udp " +
                    "and ${config.events.host}:${config.events.httpPort}/api/events"
            )
        }

        // Set up touch input for mobile
        setupTouchInput()

        // Desktop uses this to constrain the native window. Mobile launchers keep
        // the default no-op callback and rely on the aspect-fit viewport.
        updateWindowLayout()

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
                // Tap coords are physical pixels from the top-left; map them through
                // the aspect-fit viewport into the fixed UI coordinate space.
                val (uiX, uiY) = renderer.screenToUi(x, y) ?: return false
                val screenWidth = config.display.width
                if (uiX >= screenWidth - 16f && uiY <= 16f && !settingsOpen) {
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

        // Process structured agent events (if not demo mode)
        if (!demoMode) {
            val store = sessionStore
            var sessionsChanged = false
            eventReceiver?.drainEvents()?.forEach { event ->
                sessionsChanged = store?.applyEvent(event) == true || sessionsChanged
            }

            staleCheckTimer += dt
            if (staleCheckTimer >= STALE_CHECK_INTERVAL_SECONDS) {
                staleCheckTimer = 0f
                sessionsChanged = store?.expireStaleSessions() == true || sessionsChanged
            }

            if (sessionsChanged && store != null) syncEventOffices(store)
        } else {
            runDemo(dt)
        }

        // Update systems
        camera.update(dt)
        renderer.update(dt)
        if (demoMode || officeGrid == null) {
            office.update(dt)
        } else {
            officeGrid?.update(dt)
        }

        // Night mode auto-detection (synced with procedural sky)
        if (nightModeAutomatic) {
            renderer.nightMode = renderer.isSkyNightTime()
        }

        // Clear and draw
        renderer.clear()

        // Render scene
        val grid = officeGrid
        if (grid != null && !demoMode) {
            // Multi-office grid rendering: draw each office at its grid offset
            val entries = grid.getOfficeRenderData()
            if (entries.isNotEmpty()) {
                val officeMatrix = Matrix4().setToOrtho2D(
                    0f, grid.projectionBottom,
                    grid.worldWidth,
                    grid.officeWorldHeight
                )
                renderer.setCameraMatrix(officeMatrix)
                renderer.drawFixedSky()
                renderer.applyOfficeViewport()
                for (emptyCell in grid.getEmptyCellOffsets()) {
                    renderer.drawEmptyOfficeCell(emptyCell.offsetX, emptyCell.offsetY)
                }
                for (entry in entries) {
                    renderer.drawScene(entry.renderData, entry.offsetX, entry.offsetY, entry.projectLabel)
                }

                // Draw debug/UI overlays once after all offices
                renderer.drawOverlays()
                renderer.setCameraMatrix(null)
            } else {
                // No active project offices yet, fall back to the local office.
                val renderData = office.getRenderData()
                renderer.drawScene(renderData)
            }
        } else {
            // Single office mode (original)
            val renderData = office.getRenderData()
            renderer.drawScene(renderData)
        }

        // Draw settings overlay on top
        if (settingsOpen) {
            settingsOverlay.render()
        }
    }

    private fun syncEventOffices(store: AgentSessionStore) {
        val grid = officeGrid ?: return
        val previousWorldWidth = grid.worldWidth
        val previousWorldHeight = grid.worldHeight
        grid.syncWithSnapshots(store.snapshots())
        if (grid.worldWidth != previousWorldWidth || grid.worldHeight != previousWorldHeight) {
            updateWindowLayout()
            renderer.resizeGrid(
                Gdx.graphics.width,
                Gdx.graphics.height,
                grid.worldWidth,
                grid.officeWorldHeight
            )
        }
        camera.setWorldBounds(grid.worldWidth, grid.worldHeight)
    }

    private fun updateWindowLayout() {
        val grid = officeGrid
        val fixedSky = !demoMode && grid != null
        val worldWidth = if (!demoMode && grid != null) grid.worldWidth.toInt() else config.display.width
        val worldHeight = if (!demoMode && grid != null) grid.worldHeight.toInt() else config.display.height
        onWindowLayoutChanged(worldWidth, worldHeight, fixedSky)
    }

    override fun resize(width: Int, height: Int) {
        if (::renderer.isInitialized) {
            val grid = officeGrid
            if (!demoMode && grid != null) {
                renderer.resizeGrid(width, height, grid.worldWidth, grid.officeWorldHeight)
            } else {
                renderer.resize(
                    width,
                    height,
                    config.display.width.toFloat(),
                    config.display.height.toFloat()
                )
            }
        }
        if (::settingsOverlay.isInitialized) {
            settingsOverlay.resize(width, height)
        }
        if (::config.isInitialized) {
            val grid = officeGrid
            val fixedSky = !demoMode && grid != null
            val worldWidth = if (!demoMode && grid != null) grid.worldWidth.toInt() else config.display.width
            val worldHeight = if (!demoMode && grid != null) grid.worldHeight.toInt() else config.display.height
            onWindowResizeRequested(worldWidth, worldHeight, fixedSky, width, height)
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
            renderer.setSkyHourOverride(null)
        }

        // Spawn UFO (F6) — also force day so colors are visible
        if (Gdx.input.isKeyJustPressed(Input.Keys.F6)) {
            renderer.nightMode = false
            nightModeAutomatic = false
            renderer.setSkyHourOverride(12f)
            renderer.forceSpawnUfo()
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
            office.spawnDeveloper("demo_agent_4", colorVariant = 3)
            office.spawnDeveloper("demo_agent_5", colorVariant = 4)
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
        eventReceiver?.stop()

        settingsOverlay.dispose()
        renderer.dispose()
        Gdx.app.log("PixelOffice", "Game disposed")
    }
}
