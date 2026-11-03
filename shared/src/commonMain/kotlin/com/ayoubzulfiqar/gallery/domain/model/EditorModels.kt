package com.ayoubzulfiqar.gallery.domain.model

data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) {
            "CropRect coordinates must be normalized between 0.0 and 1.0"
        }
        require(left < right && top < bottom) {
            "CropRect left must be < right and top must be < bottom"
        }
    }
}

data class ColorAdjustments(
    val brightness: Float = 0f,      // [-1.0f .. 1.0f]
    val contrast: Float = 1f,        // [0.0f .. 2.0f]
    val saturation: Float = 1f,      // [0.0f .. 2.0f]
    val warmth: Float = 0f,          // [-1.0f .. 1.0f]
    val highlights: Float = 0f,      // [-1.0f .. 1.0f]
    val shadows: Float = 0f,         // [-1.0f .. 1.0f]
    val vignette: Float = 0f         // [0.0f .. 1.0f]
)

data class EditRecipe(
    val cropRect: CropRect? = null,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val filterPresetId: String? = null
)
