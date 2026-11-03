package com.ayoubzulfiqar.gallery.editor

data class FilterPreset(
    val id: String,
    val name: String,
    val colorMatrix: FloatArray
)

object FilterPresets {

    val VIVID = FilterPreset(
        id = "VIVID",
        name = "Vivid",
        colorMatrix = floatArrayOf(
            1.2f, 0f, 0f, 0f, 5f,
            0f, 1.2f, 0f, 0f, 5f,
            0f, 0f, 1.2f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val FILM_NOIR = FilterPreset(
        id = "FILM_NOIR",
        name = "Film Noir",
        colorMatrix = floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, -20f,
            0.33f, 0.59f, 0.11f, 0f, -20f,
            0.33f, 0.59f, 0.11f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val WARM_SUN = FilterPreset(
        id = "WARM_SUN",
        name = "Warm Sun",
        colorMatrix = floatArrayOf(
            1.1f, 0f, 0f, 0f, 15f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val COOL_TWILIGHT = FilterPreset(
        id = "COOL_TWILIGHT",
        name = "Cool Twilight",
        colorMatrix = floatArrayOf(
            0.9f, 0f, 0f, 0f, -10f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 1.15f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val CYBERPUNK = FilterPreset(
        id = "CYBERPUNK",
        name = "Cyberpunk",
        colorMatrix = floatArrayOf(
            1.4f, 0f, 0.2f, 0f, 20f,
            0f, 0.8f, 0.2f, 0f, -10f,
            0.1f, 0.2f, 1.5f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val CHROME = FilterPreset(
        id = "CHROME",
        name = "Chrome",
        colorMatrix = floatArrayOf(
            1.3f, -0.1f, -0.1f, 0f, 10f,
            -0.1f, 1.3f, -0.1f, 0f, 10f,
            -0.1f, -0.1f, 1.3f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val VINTAGE = FilterPreset(
        id = "VINTAGE",
        name = "Vintage",
        colorMatrix = floatArrayOf(
            0.9f, 0.1f, 0.1f, 0f, 12f,
            0.1f, 0.8f, 0.1f, 0f, 8f,
            0.1f, 0.1f, 0.6f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val ALL = listOf(VIVID, FILM_NOIR, WARM_SUN, COOL_TWILIGHT, CYBERPUNK, CHROME, VINTAGE)

    fun getById(id: String): FilterPreset? = ALL.find { it.id == id }
}
