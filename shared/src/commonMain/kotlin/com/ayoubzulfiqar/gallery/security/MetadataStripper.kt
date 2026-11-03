package com.ayoubzulfiqar.gallery.security

import com.ayoubzulfiqar.gallery.domain.model.MediaItem

enum class SanitizationMethod {
    /** Metadata blocks removed, pixels untouched. */
    LOSSLESS,

    /** Image decoded and re-encoded (formats without a lossless path, e.g. HEIC/WebP). */
    REENCODED,

    /** Video container rewritten without metadata atoms; streams untouched. */
    REMUXED,

    /** Nothing needed removing (e.g. GIF). */
    UNCHANGED
}

data class SanitizationSummary(
    /** URI of the sanitized file (temporary share copy, or the original when stripped in place). */
    val cleanUri: String,
    val strippedTagsCount: Int,
    val removedGps: Boolean,
    val isTempShareFile: Boolean,
    val method: SanitizationMethod
) {
    fun describe(): String = when {
        method == SanitizationMethod.UNCHANGED -> "No identifying metadata found"
        removedGps && strippedTagsCount > 0 -> "Removed location and $strippedTagsCount metadata tags"
        removedGps -> "Removed location metadata"
        strippedTagsCount > 0 -> "Removed $strippedTagsCount metadata tags"
        method == SanitizationMethod.REMUXED -> "Removed video metadata"
        else -> "Metadata removed"
    }
}

sealed interface SanitizationResult {
    data class Success(val summary: SanitizationSummary) : SanitizationResult
    data object Cancelled : SanitizationResult
    data class Failure(val message: String) : SanitizationResult
}

/** Removes identifying metadata (GPS, camera serials, timestamps, comments) from media. */
interface MetadataStripper {
    /** Writes a sanitized temporary copy suitable for sharing; the original is untouched. */
    suspend fun stripForSharing(item: MediaItem): SanitizationResult

    /** True when [item] can be sanitized in place without re-encoding (JPEG/PNG). */
    fun supportsInPlace(item: MediaItem): Boolean

    /** Rewrites the original file without metadata, asking for write consent where required. */
    suspend fun stripInPlace(item: MediaItem): SanitizationResult
}
