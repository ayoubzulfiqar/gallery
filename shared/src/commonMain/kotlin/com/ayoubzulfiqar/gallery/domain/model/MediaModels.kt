package com.ayoubzulfiqar.gallery.domain.model

enum class MediaType {
    IMAGE,
    VIDEO,
    RAW,
    GIF,
    BURST
}

data class MediaItem(
    val id: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val dateTakenMillis: Long,
    val dateModifiedMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val orientation: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isVaulted: Boolean = false,
    val albumIds: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val bucketId: String? = null,
    val bucketName: String? = null
)

data class MediaFolder(
    val id: String,
    val name: String,
    val coverUri: String,
    val itemCount: Int,
    val items: List<MediaItem>
)

data class MediaTimelineGroup(
    val title: String,
    val dateRange: ClosedRange<Long>,
    val items: List<MediaItem>
)

data class ExifMetadata(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val iso: Int? = null,
    val aperture: Double? = null,
    val shutterSpeed: String? = null,
    val focalLength: Double? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val colorSpace: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val address: String? = null
)
