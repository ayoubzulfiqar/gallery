package com.ayoubzulfiqar.gallery.security

data class SanitizationSummary(
    val cleanUri: String,
    val strippedTagsCount: Int,
    val removedGps: Boolean,
    val isTempShareFile: Boolean
)

interface MetadataStripper {
    suspend fun stripImageMetadata(sourceUri: String, inPlace: Boolean): SanitizationSummary
    suspend fun stripVideoMetadata(sourceUri: String, inPlace: Boolean): SanitizationSummary
}
