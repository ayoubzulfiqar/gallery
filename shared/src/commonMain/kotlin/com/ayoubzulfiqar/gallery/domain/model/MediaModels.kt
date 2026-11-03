package com.ayoubzulfiqar.gallery.domain.model

enum class MediaType {
    IMAGE,
    VIDEO,
    RAW,
    GIF,
    BURST;

    val isVideo: Boolean get() = this == VIDEO

    companion object {
        private val rawMimeTypes = setOf(
            "image/x-adobe-dng", "image/dng", "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef",
            "image/x-sony-arw", "image/x-panasonic-rw2", "image/x-olympus-orf", "image/x-fuji-raf"
        )

        fun fromMimeType(mimeType: String?, isVideoHint: Boolean = false): MediaType {
            val mime = mimeType?.lowercase().orEmpty()
            return when {
                mime.startsWith("video/") || (mime.isEmpty() && isVideoHint) -> VIDEO
                mime == "image/gif" -> GIF
                mime in rawMimeTypes -> RAW
                else -> IMAGE
            }
        }
    }
}

data class MediaItem(
    val id: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    /** Capture time when known, otherwise the best available file timestamp. Drives timeline ordering. */
    val dateTakenMillis: Long,
    val dateModifiedMillis: Long,
    /** Stored (pre-rotation) pixel dimensions; 0 when unknown. */
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    /** Clockwise rotation in degrees (0, 90, 180, 270) that must be applied for display. */
    val orientation: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFavorite: Boolean = false,
    val tags: Set<String> = emptySet(),
    val bucketId: String? = null,
    val bucketName: String? = null,
    /** Folder path relative to the storage volume root, e.g. "DCIM/Camera/". */
    val relativePath: String? = null
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO

    private val isRotatedSideways: Boolean get() = orientation == 90 || orientation == 270

    /** Width as displayed after applying [orientation]. */
    val displayWidth: Int get() = if (isRotatedSideways) height else width

    /** Height as displayed after applying [orientation]. */
    val displayHeight: Int get() = if (isRotatedSideways) width else height

    /** Display aspect ratio (width / height); 1 when dimensions are unknown. */
    val aspectRatio: Float
        get() = if (displayWidth > 0 && displayHeight > 0) displayWidth.toFloat() / displayHeight else 1f

    /** Stable key for folder grouping. */
    val folderKey: String get() = bucketId ?: bucketName ?: UNKNOWN_FOLDER_KEY

    companion object {
        const val UNKNOWN_FOLDER_KEY = "__unknown__"
    }
}

data class MediaFolder(
    val id: String,
    val name: String,
    val coverUri: String,
    val itemCount: Int,
    val items: List<MediaItem>
) {
    val photoCount: Int get() = items.count { !it.isVideo }
    val videoCount: Int get() = items.count { it.isVideo }
}

data class MediaTimelineGroup(
    val key: String,
    val title: String,
    val dateRange: ClosedRange<Long>,
    val items: List<MediaItem>,
    /** When set, a prominent section header (e.g. "Videos") is shown above this group. */
    val sectionTitle: String? = null,
    val sectionSubtitle: String? = null
)

/** Timeline grouping granularity. Lives in the domain because grouping is domain logic. */
enum class TimelineDensity(val label: String) {
    YEARS("Years"),
    MONTHS("Months"),
    DAYS("Days"),
    MASONRY("Masonry")
}

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
    val address: String? = null,
    val dateTimeOriginal: String? = null,
    val software: String? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val hasCameraInfo: Boolean
        get() = make != null || model != null || lensModel != null || iso != null ||
            aperture != null || shutterSpeed != null || focalLength != null

    val cameraName: String?
        get() {
            val m = make?.trim().orEmpty()
            val mo = model?.trim().orEmpty()
            return when {
                m.isEmpty() && mo.isEmpty() -> null
                mo.startsWith(m, ignoreCase = true) -> mo
                m.isEmpty() -> mo
                mo.isEmpty() -> m
                else -> "$m $mo"
            }
        }
}

/** Everything the info sheet shows about one item. */
data class MediaDetails(
    val item: MediaItem,
    val exif: ExifMetadata?,
    /** True when location may be hidden because the platform redacted it (missing permission). */
    val locationRedacted: Boolean = false
)

/** Snapshot of the device library as seen by the repository. */
data class LibraryState(
    val items: List<MediaItem> = emptyList(),
    val isLoaded: Boolean = false
)

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val rounded = if (value >= 100) kotlin.math.round(value).toLong().toString() else (kotlin.math.round(value * 10) / 10.0).toString()
    return "$rounded ${units[unit]}"
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0) + 500) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
