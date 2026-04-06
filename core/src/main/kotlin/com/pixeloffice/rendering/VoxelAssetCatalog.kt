package com.pixeloffice.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.utils.Disposable

/**
 * Loads and caches 3D models from the Voxel Office Pack.
 * Each OBJ has its own PNG palette texture (unlike the factory's shared colormap).
 */
class VoxelAssetCatalog : Disposable {

    private val models = mutableMapOf<String, Model>()
    private val textures = mutableMapOf<String, Texture>()
    private val objLoader = ObjLoader()

    companion object {
        private const val BASE_PATH = "3D Voxel Office Pack - Obj File/"
        private const val TAG = "VoxelAssetCatalog"

        // Asset categories and their subdirectories
        val CUBICLE_MODELS = listOf(
            "Cubicles/Office_Cubicle_White_01",
            "Cubicles/Office_Cubicle_White_02",
            "Cubicles/Office_Cubicle_White_03",
            "Cubicles/Office_Cubicle_White_04",
            "Cubicles/Office_Cubicle_White_05",
            "Cubicles/Office_Cubicle_Light_01",
            "Cubicles/Office_Cubicle_Light_02",
            "Cubicles/Office_Cubicle_Light_03",
            "Cubicles/Office_Cubicle_Regular_01",
            "Cubicles/Office_Cubicle_Regular_02",
            "Cubicles/Office_Cubicle_Regular_03"
        )

        val CHAIR_MODELS = listOf(
            "Chairs/Office_Chair_Black_01",
            "Chairs/Office_Chair_Black_02",
            "Chairs/Office_Chair_Brown_01",
            "Chairs/Office_Chair_Brown_02",
            "Chairs/Office_Chair_Brown_03",
            "Chairs/Office_Chair_Brown_04",
            "Chairs/Office_Chair_White_01",
            "Chairs/Office_Chair_White_02",
            "Chairs/Office_Chair_White_03",
            "Chairs/Office_Chair_White_04",
            "Chairs/Office_Couch_Brown_01",
            "Chairs/Office_Couch_Brown_02",
            "Chairs/Office_Couch_Black_01",
            "Chairs/Office_Couch_White_01"
        )

        val TABLE_MODELS = listOf(
            "Tables/Office_Table_White_1x1_01",
            "Tables/Office_Table_White_1x1_02",
            "Tables/Office_Table_White_2x1_01",
            "Tables/Office_Table_Brown_1x1_01",
            "Tables/Office_Table_Brown_1x1_02",
            "Tables/Office_Table_Brown_2x1_01",
            "Tables/Office_Table_Brown_2x2_01",
            "Tables/Office_Table_Coffee_01_Black",
            "Tables/Office_Table_Coffee_03_Brown"
        )

        val ELECTRONICS_MODELS = listOf(
            "Misc/Electronics/Office_Misc_PC_01",
            "Misc/Electronics/Office_Misc_PC_02",
            "Misc/Electronics/Office_Misc_Phone",
            "Misc/Electronics/Office_Misc_Printer",
            "Misc/Electronics/Office_Misc_Fax",
            "Misc/Electronics/Office_Misc_Tablet",
            "Misc/Electronics/Office_Misc_TV_Wall_01",
            "Misc/Electronics/Office_Misc_TV_Stand_02",
            "Misc/Electronics/Office_Misc_Console_Swotch_01"
        )

        val COFFEE_MODELS = listOf(
            "Misc/Coffee/Office_Misc_Coffee_Machine_01",
            "Misc/Coffee/Office_Misc_Coffee_Machine_02",
            "Misc/Coffee/Office_Misc_Coffee_Mug",
            "Misc/Coffee/Office_Misc_Coffee_Pot_01"
        )

        val MISC_MODELS = listOf(
            "Misc/Office_Misc_Cabinet_01",
            "Misc/Office_Misc_Cabinet_02",
            "Misc/Office_Misc_Door_01",
            "Misc/Office_Misc_Door_02",
            "Misc/Office_Misc_Plant_01",
            "Misc/Office_Misc_Plant_02",
            "Misc/Office_Misc_Plant_03",
            "Misc/Office_Misc_Wall_Clock_01",
            "Misc/Office_Misc_Whiteboard_01",
            "Misc/Office_Misc_Whiteboard_02",
            "Misc/Office_Misc_Wall_Corkboard_01",
            "Misc/Office_Misc_Wall_Graph",
            "Misc/Office_Misc_PictureFrame_01",
            "Misc/Office_Misc_PictureFrame_02",
            "Misc/Office_Misc_Wall_Corkboard_02",
            "Misc/Office_Misc_Notebook",
            "Misc/Office_Misc_Notepad",
            "Misc/Office_Misc_Organizer",
            "Misc/Office_Misc_Papers"
        )

        val TRASHCAN_MODELS = listOf(
            "Misc/Trashcans/Office_Misc_Traschcan_Recycle_Blue",
            "Misc/Trashcans/Office_Misc_Traschcan_Recycle_Green",
            "Misc/Trashcans/Office_Misc_Traschcan_Recycle_Red",
            "Misc/Trashcans/Office_Misc_Trashcan_Small_01",
            "Misc/Trashcans/Office_Misc_Trashcan_Big_01"
        )

        val ALL_MODELS = CUBICLE_MODELS + CHAIR_MODELS + TABLE_MODELS +
            ELECTRONICS_MODELS + COFFEE_MODELS + MISC_MODELS + TRASHCAN_MODELS
    }

    fun loadAll() {
        for (name in ALL_MODELS) {
            loadModel(name)
        }
        val textureCount = textures.size
        Gdx.app?.log(TAG, "Loaded ${models.size} / ${ALL_MODELS.size} models, $textureCount textures")
        if (models.isEmpty()) {
            Gdx.app?.log(TAG, "WARNING: No models loaded! Check asset paths.")
        }
    }

    private fun loadModel(relativePath: String) {
        try {
            val objPath = "$BASE_PATH$relativePath.obj"
            val pngPath = "$BASE_PATH$relativePath.png"
            val objHandle = Gdx.files.internal(objPath)
            val pngHandle = Gdx.files.internal(pngPath)

            if (!objHandle.exists()) {
                Gdx.app?.log(TAG, "OBJ not found: $objPath")
                return
            }

            // Load OBJ with flipV (OBJ UV V=0 is bottom, LibGDX Y=0 is top)
            val model = objLoader.loadModel(objHandle, true)

            // Load per-model texture if available
            val texture = if (pngHandle.exists()) {
                Texture(pngHandle).also {
                    it.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                    it.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                    textures[relativePath] = it
                }
            } else null

            // Override materials — all models get their texture (cubicles need it for dark trim)
            for (material in model.materials) {
                material.clear()
                if (texture != null) {
                    material.set(TextureAttribute.createDiffuse(texture))
                }
                material.set(ColorAttribute.createDiffuse(Color.WHITE))
                material.set(IntAttribute.createCullFace(GL20.GL_BACK))
            }

            models[relativePath] = model
        } catch (e: Exception) {
            Gdx.app?.log(TAG, "Failed to load: $relativePath - ${e.message}")
        }
    }

    /** Create a new ModelInstance for the given asset path. Returns null if model not loaded. */
    fun createInstance(relativePath: String): ModelInstance? {
        val model = models[relativePath] ?: return null
        return ModelInstance(model)
    }

    /** Get the underlying Model for custom instance creation. */
    fun getModel(relativePath: String): Model? = models[relativePath]

    override fun dispose() {
        for (model in models.values) model.dispose()
        for (texture in textures.values) texture.dispose()
        models.clear()
        textures.clear()
    }
}
