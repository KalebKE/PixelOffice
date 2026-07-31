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
import com.pixeloffice.core.EventBus
import com.pixeloffice.network.AoClient
import com.pixeloffice.network.DiscoveryBroadcaster
import com.pixeloffice.network.HeartbeatReceiver
import com.pixeloffice.network.TmuxReceiver
import com.pixeloffice.world.OfficeGrid
import com.pixeloffice.parsing.ActivityType
import com.pixeloffice.parsing.DetectedActivity
import com.pixeloffice.parsing.Patterns
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

        private val BROADCAST_TYPES = setOf(
            ActivityType.TEST_FAILURE,
            ActivityType.BUILD_FAILURE,
            ActivityType.TEST_SUCCESS,
            ActivityType.BUILD_SUCCESS
        )
    }

    // Configuration
    private lateinit var config: Config

    // Core systems
    private lateinit var eventBus: EventBus

    // Per-connection parsers and agent mappings
    private val streamParsers = mutableMapOf<String, StreamParser>()
    private val connectionToAgent = mutableMapOf<String, String>()
    private val pendingConnections = mutableSetOf<String>()

    // Subagent tracking: connectionId → list of spawned subagent agentIds
    private val connectionSubagents = mutableMapOf<String, MutableList<String>>()
    private val roundRobinCounters = mutableMapOf<String, Int>()

    // Network
    private lateinit var receiver: TmuxReceiver
    private lateinit var discoveryBroadcaster: DiscoveryBroadcaster
    private lateinit var heartbeatReceiver: HeartbeatReceiver

    // Terminal → Agent ID mapping (from heartbeat system)
    private val terminalToAgent = mutableMapOf<String, String>()
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

    // AO integration (optional, config-driven)
    private var aoClient: AoClient? = null
    private var officeGrid: OfficeGrid? = null

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

        // Discovery broadcaster (for LAN auto-discovery)
        discoveryBroadcaster = DiscoveryBroadcaster(tcpPort = config.network.port)

        // Heartbeat receiver for terminal detection
        heartbeatReceiver = HeartbeatReceiver(
            port = config.heartbeat.port,
            onNewTerminal = { terminalId ->
                // Post to GL thread since this callback runs on heartbeat thread
                Gdx.app.postRunnable {
                    handleNewTerminal(terminalId)
                }
            }
        )

        // Agent Orchestrator integration (if configured)
        if (config.ao.enabled) {
            aoClient = AoClient(config.ao.url).also { it.start() }
            officeGrid = OfficeGrid(config, columns = config.ao.gridColumns)
            Gdx.app.log("PixelOffice", "AO integration enabled, listening at: ${config.ao.url}")
            // Camera bounds will be updated dynamically when offices are created
        }

        // Demo mode
        demoMode = config.demo.enabled
        if (demoMode) {
            renderer.setDemoMode(true)
        } else {
            // Start network receiver, discovery broadcaster, and heartbeat receiver
            receiver.start()
            discoveryBroadcaster.start()
            heartbeatReceiver.start()
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

        // Idle subagent developers/managers and clean up tracking
        connectionSubagents.remove(connectionId)?.forEach { subId ->
            when (subId) {
                "pm" -> office.getProjectManager()?.handleEvent("idle")
                "po" -> office.getProductOwner()?.handleEvent("idle")
                else -> office.getDeveloper(subId)?.handleEvent("idle")
            }
        }
        roundRobinCounters.remove(connectionId)

        renderer.setConnectionCount(receiver.getConnectionCount())
        if (wasPending) {
            Gdx.app.log("PixelOffice", "Probe disconnected (no developer spawned): $connectionId")
        } else {
            Gdx.app.log("PixelOffice", "Disconnected: $connectionId (agent $agentId)")
        }
    }

    /**
     * Handle a new terminal detected via heartbeat.
     * Spawns a developer immediately for instant visual feedback.
     */
    private fun handleNewTerminal(terminalId: String) {
        val agentId = "terminal_$terminalId"
        val developer = office.spawnDeveloper(agentId)
        if (developer != null) {
            terminalToAgent[terminalId] = agentId
            Gdx.app.log("PixelOffice", "Developer spawned for terminal heartbeat: $terminalId → $agentId")
        } else {
            Gdx.app.log("PixelOffice", "Failed to spawn developer for terminal: $terminalId (no desk available)")
        }
    }

    /**
     * Check for stale terminals (no heartbeat for timeout period) and remove their developers.
     */
    private fun checkStaleTerminals() {
        val timeoutMs = config.heartbeat.timeoutMinutes * 60 * 1000L
        val staleTerminals = heartbeatReceiver.getStaleTerminals(timeoutMs)

        for (terminalId in staleTerminals) {
            val agentId = terminalToAgent.remove(terminalId)
            if (agentId != null) {
                office.removeDeveloper(agentId)
                Gdx.app.log("PixelOffice", "Removed stale developer: $agentId (terminal: $terminalId, no heartbeat for ${config.heartbeat.timeoutMinutes} minutes)")
            }
            heartbeatReceiver.removeTerminal(terminalId)
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
                val description = (activity.details?.get("description") as? String) ?: ""
                val managerType = Patterns.managerTypeForDescription(description)

                if (managerType != null) {
                    // Route to PM or PO instead of spawning a developer
                    val connId = connectionToAgent.entries.find { it.value == parentId }?.key
                    if (connId != null) {
                        connectionSubagents.getOrPut(connId) { mutableListOf() }.add(managerType)
                    }
                    Gdx.app.log("PixelOffice", "Manager subagent routed: $managerType (parent: $parentId, desc: $description)")
                } else {
                    val spawnId = "${parentId}_sub_${office.getAllDevelopers().size}"
                    val dev = office.spawnDeveloper(spawnId)
                    if (dev != null) {
                        val connId = connectionToAgent.entries.find { it.value == parentId }?.key
                        if (connId != null) {
                            connectionSubagents.getOrPut(connId) { mutableListOf() }.add(spawnId)
                        }
                        Gdx.app.log("PixelOffice", "Subagent spawned: $spawnId (parent: $parentId)")
                    } else {
                        Gdx.app.log("PixelOffice", "Subagent spawn FAILED (no desk): $spawnId")
                    }
                }
            }
            ActivityType.THINKING -> {
                dispatchEvent(activity, "thinking_started")
            }
            ActivityType.PLANNING -> {
                dispatchEvent(activity, "planning_started")
            }
            ActivityType.FILE_READ, ActivityType.WEB_SEARCH -> {
                dispatchEvent(activity, "researching_started")
            }
            ActivityType.CODE_WRITING, ActivityType.CODE_EDITING -> {
                dispatchEvent(activity, "code_writing_started")
            }
            ActivityType.TEST_EXECUTION, ActivityType.BUILD_EXECUTION,
            ActivityType.BASH_EXECUTION, ActivityType.COMMITTING,
            ActivityType.INSTALLING_DEPS -> {
                dispatchEvent(activity, "command_started")
            }
            ActivityType.TEST_FAILURE, ActivityType.BUILD_FAILURE -> {
                dispatchEvent(activity, "tests_failed")
                camera.shake(3f)
            }
            ActivityType.TEST_SUCCESS, ActivityType.BUILD_SUCCESS -> {
                dispatchEvent(activity, "command_succeeded")
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
     * Dispatch an event to the correct entity (developer, PM, or PO) based on the activity's agentId.
     */
    private fun dispatchEvent(activity: DetectedActivity, event: String) {
        val id = activity.agentId
        when (id) {
            "pm" -> office.getProjectManager()?.handleEvent(event)
            "po" -> office.getProductOwner()?.handleEvent(event)
            else -> {
                val dev = if (id != null) office.getDeveloper(id) else office.getAllDevelopers().lastOrNull()
                dev?.handleEvent(event)
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
            // AO mode: drain SSE snapshots and sync offices
            val aoSnapshots = aoClient?.drainSnapshots()
            if (aoSnapshots != null) {
                val prevOfficeCount = officeGrid?.getAllOffices()?.size ?: 0
                officeGrid?.syncWithSnapshots(aoSnapshots)
                // Only resize window when the number of offices changes
                officeGrid?.let { grid ->
                    val newOfficeCount = grid.getAllOffices().size
                    if (newOfficeCount != prevOfficeCount) {
                        val newWidth = grid.worldWidth.toInt().coerceAtLeast(config.display.width)
                        val newHeight = grid.worldHeight.toInt().coerceAtLeast(config.display.height)
                        Gdx.graphics.setWindowedMode(newWidth, newHeight)
                    }
                    camera.setWorldBounds(grid.worldWidth, grid.worldHeight)
                }
            }

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
                val subagents = connectionSubagents[connectionId]
                for (activity in activities) {
                    if (activity.type == ActivityType.AGENT_SPAWN) {
                        // AGENT_SPAWN always goes to parent (it creates the subagent)
                        activity.agentId = activity.agentId ?: agentId
                        handleActivity(activity)
                    } else if (!subagents.isNullOrEmpty() && activity.type in BROADCAST_TYPES) {
                        // Broadcast: send to all developers (parent + subagents)
                        val allAgents = listOf(agentId) + subagents
                        for (targetId in allAgents) {
                            val copy = activity.copy(agentId = targetId)
                            handleActivity(copy)
                        }
                    } else if (!subagents.isNullOrEmpty()) {
                        // Round-robin: distribute across parent + subagents
                        val allAgents = listOf(agentId) + subagents
                        val counter = roundRobinCounters.getOrPut(connectionId) { 0 }
                        activity.agentId = allAgents[counter % allAgents.size]
                        roundRobinCounters[connectionId] = counter + 1
                        handleActivity(activity)
                    } else {
                        // No subagents: original behavior
                        activity.agentId = activity.agentId ?: agentId
                        handleActivity(activity)
                    }
                }
            }
        } else {
            runDemo(dt)
        }

        // Check for stale terminals periodically (if not demo mode)
        if (!demoMode) {
            staleCheckTimer += dt
            if (staleCheckTimer >= config.heartbeat.checkIntervalSeconds) {
                staleCheckTimer = 0f
                checkStaleTerminals()
            }
        }

        // Update systems
        camera.update(dt)
        renderer.update(dt)
        office.update(dt)
        officeGrid?.update(dt)

        // Night mode auto-detection (synced with procedural sky)
        if (nightModeAutomatic) {
            renderer.nightMode = renderer.isSkyNightTime()
        }

        // Clear and draw
        renderer.clear()

        // Render scene
        val grid = officeGrid
        if (grid != null && config.ao.enabled) {
            // Multi-office grid rendering: draw each office at its grid offset
            val entries = grid.getOfficeRenderData()
            if (entries.isNotEmpty()) {
                val gridMatrix = Matrix4().setToOrtho2D(
                    0f, 0f,
                    grid.worldWidth,
                    grid.worldHeight
                )
                renderer.setCameraMatrix(gridMatrix)
                // Draw sky once spanning all offices
                renderer.drawSky(grid.worldWidth.toInt())
                for (entry in entries) {
                    renderer.drawScene(entry.renderData, entry.offsetX, entry.offsetY, entry.projectId)
                }

                // Clear camera for overlays (render in screen space)
                renderer.setCameraMatrix(null)
                // Draw debug/UI overlays once after all offices
                renderer.drawOverlays()
            } else {
                // No AO offices yet, fall back to local office
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

            // PM and PO are already spawned as permanent managers by Office.setupDefaultDeskColumns()
            // Just adjust their sit durations for demo pacing
            office.getProjectManager()?.sitDuration = 15f
            office.getProductOwner()?.let { po ->
                po.sitDuration = 15f
                po.setSitTimerStart(-7.5f)
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
        // Stop AO client
        aoClient?.stop()

        // Stop network receiver, discovery broadcaster, and heartbeat receiver
        receiver.stop()
        discoveryBroadcaster.stop()
        heartbeatReceiver.stop()

        settingsOverlay.dispose()
        renderer.dispose()
        eventBus.clear()
        Gdx.app.log("PixelOffice", "Game disposed")
    }
}
