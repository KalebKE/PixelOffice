package com.pixeloffice.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.ui.List as UiList
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

fun createSettingsSkin(): Skin {
    val skin = Skin()

    // Create a 1x1 white pixel texture
    val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
    pixmap.setColor(Color.WHITE)
    pixmap.fill()
    val whiteTex = Texture(pixmap)
    pixmap.dispose()
    skin.add("white", whiteTex)

    val whiteDrawable = TextureRegionDrawable(TextureRegion(whiteTex))

    fun tint(color: Color): Drawable = whiteDrawable.tint(color)

    // Colors
    val bgDark = Color(0.15f, 0.15f, 0.2f, 0.95f)
    val bgMedium = Color(0.22f, 0.22f, 0.28f, 1f)
    val bgLight = Color(0.3f, 0.3f, 0.36f, 1f)
    val accent = Color(0.3f, 0.5f, 0.9f, 1f)
    val accentHover = Color(0.4f, 0.6f, 1f, 1f)
    val applyGreen = Color(0.2f, 0.6f, 0.3f, 1f)
    val applyGreenHover = Color(0.3f, 0.7f, 0.4f, 1f)
    val closeRed = Color(0.6f, 0.2f, 0.2f, 1f)
    val closeRedHover = Color(0.7f, 0.3f, 0.3f, 1f)
    val textColor = Color(0.9f, 0.9f, 0.9f, 1f)
    val headerColor = Color(1f, 0.85f, 0.4f, 1f)
    val selectionColor = Color(0.3f, 0.4f, 0.7f, 1f)

    // Font
    val font = BitmapFont(Gdx.files.internal("com/badlogic/gdx/utils/lsans-15.fnt"), Gdx.files.internal("com/badlogic/gdx/utils/lsans-15.png"), false)
    skin.add("default-font", font)

    // Label styles
    skin.add("default", Label.LabelStyle(font, textColor))
    skin.add("header", Label.LabelStyle(font, headerColor))

    // TextButton - default
    val tbStyle = TextButton.TextButtonStyle()
    tbStyle.font = font
    tbStyle.fontColor = textColor
    tbStyle.up = tint(bgLight)
    tbStyle.down = tint(accent)
    tbStyle.over = tint(accentHover)
    skin.add("default", tbStyle)

    // TextButton - apply
    val applyStyle = TextButton.TextButtonStyle()
    applyStyle.font = font
    applyStyle.fontColor = Color.WHITE
    applyStyle.up = tint(applyGreen)
    applyStyle.down = tint(applyGreenHover)
    applyStyle.over = tint(applyGreenHover)
    skin.add("apply", applyStyle)

    // TextButton - close
    val closeStyle = TextButton.TextButtonStyle()
    closeStyle.font = font
    closeStyle.fontColor = Color.WHITE
    closeStyle.up = tint(closeRed)
    closeStyle.down = tint(closeRedHover)
    closeStyle.over = tint(closeRedHover)
    skin.add("close", closeStyle)

    // SelectBox style
    val sbStyle = SelectBox.SelectBoxStyle()
    sbStyle.font = font
    sbStyle.fontColor = textColor
    sbStyle.background = tint(bgMedium)
    sbStyle.backgroundOpen = tint(bgLight)
    sbStyle.backgroundOver = tint(bgLight)
    sbStyle.scrollStyle = ScrollPane.ScrollPaneStyle()
    sbStyle.scrollStyle.background = tint(bgMedium)
    sbStyle.listStyle = UiList.ListStyle()
    sbStyle.listStyle.font = font
    sbStyle.listStyle.fontColorSelected = Color.WHITE
    sbStyle.listStyle.fontColorUnselected = textColor
    sbStyle.listStyle.selection = tint(selectionColor)
    sbStyle.listStyle.background = tint(bgMedium)
    skin.add("default", sbStyle)

    // List style
    val listStyle = UiList.ListStyle()
    listStyle.font = font
    listStyle.fontColorSelected = Color.WHITE
    listStyle.fontColorUnselected = textColor
    listStyle.selection = tint(selectionColor)
    listStyle.background = tint(bgMedium)
    skin.add("default", listStyle)

    // ScrollPane style
    val spStyle = ScrollPane.ScrollPaneStyle()
    spStyle.background = tint(bgDark)
    skin.add("default", spStyle)

    // CheckBox style
    val cbStyle = CheckBox.CheckBoxStyle()
    cbStyle.font = font
    cbStyle.fontColor = textColor
    cbStyle.checkboxOff = tint(bgLight)
    cbStyle.checkboxOn = tint(accent)
    cbStyle.checkboxOver = tint(accentHover)
    skin.add("default", cbStyle)

    // TextField style
    val tfStyle = TextField.TextFieldStyle()
    tfStyle.font = font
    tfStyle.fontColor = textColor
    tfStyle.background = tint(bgMedium)
    tfStyle.cursor = tint(textColor)
    tfStyle.selection = tint(selectionColor)
    tfStyle.focusedBackground = tint(bgLight)
    skin.add("default", tfStyle)

    // Window style
    val windowStyle = Window.WindowStyle()
    windowStyle.titleFont = font
    windowStyle.titleFontColor = headerColor
    windowStyle.background = tint(bgDark)
    skin.add("default", windowStyle)

    return skin
}
