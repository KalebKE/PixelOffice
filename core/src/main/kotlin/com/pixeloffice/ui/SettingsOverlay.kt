package com.pixeloffice.ui

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.pixeloffice.world.*

class SettingsOverlay(
    private val onApply: (SettingsConfig) -> Unit,
    private val onClose: () -> Unit
) {
    private val stage = Stage(ScreenViewport())
    private val skin = createSettingsSkin()
    private var config: SettingsConfig = SettingsConfig.fromDefaults()
    private var isOpen = false

    fun open(config: SettingsConfig) {
        this.config = config.deepCopy()
        isOpen = true
        buildUI()
    }

    fun close() {
        isOpen = false
        stage.clear()
    }

    fun isOpen(): Boolean = isOpen

    fun render() {
        if (!isOpen) return
        stage.act()
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        if (isOpen) {
            stage.clear()
            buildUI()
        }
    }

    fun getInputProcessor(): InputProcessor = stage

    fun dispose() {
        stage.dispose()
        skin.dispose()
    }

    private fun buildUI() {
        stage.clear()

        val stageW = stage.viewport.worldWidth
        val stageH = stage.viewport.worldHeight
        val winW = (stageW * 0.9f).coerceAtMost(580f)
        val winH = (stageH * 0.88f).coerceAtMost(420f)

        val window = Window("Settings", skin)
        window.isModal = true
        window.isMovable = true
        window.setSize(winW, winH)
        window.setPosition((stageW - winW) / 2f, (stageH - winH) / 2f)

        // Content table inside a scroll pane
        val content = Table(skin)
        content.pad(8f)
        content.defaults().left().padBottom(4f)

        // --- Desk Column 1 ---
        buildColumnSection(content, config.column1, "Desk Column 1 (Left)")

        // --- Desk Column 2 ---
        buildColumnSection(content, config.column2, "Desk Column 2 (Right)")

        // --- Characters ---
        content.add(Label("Characters", skin, "header")).colspan(6).padTop(10f).row()

        val devTable = Table(skin)
        rebuildDeveloperTable(devTable)
        content.add(devTable).colspan(6).fillX().row()

        // --- PM / PO ---
        content.add(Label("PM / PO", skin, "header")).colspan(6).padTop(10f).row()

        val pmCheck = CheckBox(" Spawn PM", skin)
        pmCheck.isChecked = config.spawnPM
        pmCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                config.spawnPM = pmCheck.isChecked
            }
        })
        content.add(pmCheck).colspan(2)

        val pmDeskBox = SelectBox<String>(skin)
        val pmDeskItems = GdxArray<String>()
        pmDeskItems.add("Patrol")
        for (id in config.getAllDeskIds()) pmDeskItems.add(id)
        pmDeskBox.items = pmDeskItems
        pmDeskBox.selected = config.pmDeskId ?: "Patrol"
        pmDeskBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                config.pmDeskId = if (pmDeskBox.selected == "Patrol") null else pmDeskBox.selected
            }
        })
        content.add(Label("Desk:", skin)).padLeft(8f)
        content.add(pmDeskBox).width(160f).row()

        val poCheck = CheckBox(" Spawn PO", skin)
        poCheck.isChecked = config.spawnPO
        poCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                config.spawnPO = poCheck.isChecked
            }
        })
        content.add(poCheck).colspan(2)

        val poDeskBox = SelectBox<String>(skin)
        val poDeskItems = GdxArray<String>()
        poDeskItems.add("Patrol")
        for (id in config.getAllDeskIds()) poDeskItems.add(id)
        poDeskBox.items = poDeskItems
        poDeskBox.selected = config.poDeskId ?: "Patrol"
        poDeskBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                config.poDeskId = if (poDeskBox.selected == "Patrol") null else poDeskBox.selected
            }
        })
        content.add(Label("Desk:", skin)).padLeft(8f)
        content.add(poDeskBox).width(160f).row()

        // --- Debug ---
        content.add(Label("Debug", skin, "header")).colspan(6).padTop(10f).row()

        val debugCheck = CheckBox(" Debug Mode (F1)", skin)
        debugCheck.isChecked = config.debugMode
        debugCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                config.debugMode = debugCheck.isChecked
            }
        })
        content.add(debugCheck).colspan(6).row()

        // Scroll pane wrapping content
        val scrollPane = ScrollPane(content, skin)
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)

        // Layout: scroll pane fills the window, button bar at bottom
        val windowContent = Table(skin)
        windowContent.add(scrollPane).expand().fill().row()

        // Button bar
        val buttonBar = Table(skin)
        buttonBar.pad(6f)

        val applyBtn = TextButton("Apply", skin, "apply")
        applyBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                onApply(config)
                close()
                onClose()
            }
        })

        val resetBtn = TextButton("Reset Defaults", skin)
        resetBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                config = SettingsConfig.fromDefaults()
                stage.clear()
                buildUI()
            }
        })

        val closeBtn = TextButton("Close", skin, "close")
        closeBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                close()
                onClose()
            }
        })

        buttonBar.add(applyBtn).width(80f).padRight(8f)
        buttonBar.add(resetBtn).width(120f).padRight(8f)
        buttonBar.add(closeBtn).width(80f)

        windowContent.add(buttonBar).padTop(4f).row()

        window.add(windowContent).expand().fill()

        stage.addActor(window)
    }

    private fun buildColumnSection(content: Table, column: ColumnSettings, title: String) {
        content.add(Label(title, skin, "header")).colspan(6).padTop(10f).row()

        // Header row
        content.add(Label("Row", skin)).width(40f)
        content.add(Label("Side", skin)).width(50f)
        content.add(Label("Enabled", skin)).width(70f)
        content.add(Label("Equipment", skin)).width(100f)
        content.add(Label("Chair", skin)).width(90f)
        content.add(Label("Wall Decor", skin)).width(120f)
        content.row()

        for ((rowIndex, row) in column.rows.withIndex()) {
            val rowLabel = "R${rowIndex + 1}"

            // West desk
            if (row.westDesk != null) {
                val desk = row.westDesk!!
                buildDeskRow(content, rowLabel, "W", desk)
            }

            // East desk
            if (row.eastDesk != null) {
                val desk = row.eastDesk!!
                buildDeskRow(content, rowLabel, "E", desk)
            }
        }
    }

    /**
     * Create a SelectBox bound to an enum value.
     */
    private inline fun <reified T : Enum<T>> enumSelectBox(
        current: T,
        crossinline onChange: (T) -> Unit
    ): SelectBox<String> {
        val box = SelectBox<String>(skin)
        val items = GdxArray<String>()
        for (value in enumValues<T>()) items.add(value.name)
        box.items = items
        box.selected = current.name
        box.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                onChange(enumValueOf(box.selected))
            }
        })
        return box
    }

    private fun buildDeskRow(content: Table, rowLabel: String, side: String, desk: DeskSettings) {
        content.add(Label(rowLabel, skin)).padRight(4f)
        content.add(Label(side, skin)).padRight(4f)

        val enableCheck = CheckBox("", skin)
        enableCheck.isChecked = desk.enabled
        enableCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                desk.enabled = enableCheck.isChecked
            }
        })
        content.add(enableCheck)

        content.add(enumSelectBox(desk.equipment) { desk.equipment = it }).width(100f)
        content.add(enumSelectBox(desk.chairColor) { desk.chairColor = it }).width(90f)
        content.add(enumSelectBox(desk.wallDecor) { desk.wallDecor = it }).width(120f)

        content.row()
    }

    private fun rebuildDeveloperTable(devTable: Table) {
        devTable.clear()

        // Header
        devTable.add(Label("Agent ID", skin)).width(120f)
        devTable.add(Label("Color", skin)).width(50f)
        devTable.add(Label("Desk", skin)).width(160f)
        devTable.add(Label("", skin)).width(60f)
        devTable.row()

        for ((index, dev) in config.developers.withIndex()) {
            val agentField = TextField(dev.agentId, skin)
            agentField.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    dev.agentId = agentField.text
                }
            })
            devTable.add(agentField).width(120f).padRight(4f)

            val variantBox = SelectBox<String>(skin)
            val variantItems = GdxArray<String>()
            variantItems.addAll("0", "1", "2")
            variantBox.items = variantItems
            variantBox.selected = dev.colorVariant.toString()
            variantBox.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    dev.colorVariant = variantBox.selected.toIntOrNull() ?: 0
                }
            })
            devTable.add(variantBox).width(50f).padRight(4f)

            val deskAssignBox = SelectBox<String>(skin)
            val deskItems = GdxArray<String>()
            deskItems.add("Auto")
            for (id in config.getAllDeskIds()) deskItems.add(id)
            deskAssignBox.items = deskItems
            val currentAssignment = if (dev.assignedColumnId != null && dev.assignedDeskNumber != null) {
                "${dev.assignedColumnId}_desk${dev.assignedDeskNumber}"
            } else "Auto"
            if (deskItems.contains(currentAssignment, false)) {
                deskAssignBox.selected = currentAssignment
            }
            deskAssignBox.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    if (deskAssignBox.selected == "Auto") {
                        dev.assignedColumnId = null
                        dev.assignedDeskNumber = null
                    } else {
                        val parts = deskAssignBox.selected.split("_desk")
                        if (parts.size == 2) {
                            dev.assignedColumnId = parts[0]
                            dev.assignedDeskNumber = parts[1].toIntOrNull()
                        }
                    }
                }
            })
            devTable.add(deskAssignBox).width(160f).padRight(4f)

            val removeBtn = TextButton("Remove", skin, "close")
            val capturedIndex = index
            removeBtn.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    config.developers.removeAt(capturedIndex)
                    rebuildDeveloperTable(devTable)
                }
            })
            devTable.add(removeBtn).width(60f)
            devTable.row()
        }

        // Add developer button
        val addBtn = TextButton("+ Add Developer", skin)
        addBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val nextId = config.developers.size + 1
                config.developers.add(DeveloperSettings("demo_agent_$nextId", config.developers.size % 3))
                rebuildDeveloperTable(devTable)
            }
        })
        devTable.add(addBtn).colspan(4).padTop(4f).left().row()
    }
}
