package com.ayoubzulfiqar.gallery.security

class FakeMetadataStripper : MetadataStripper {

    var lastStrippedUri: String? = null
    var lastInPlace: Boolean = false

    override suspend fun stripImageMetadata(sourceUri: String, inPlace: Boolean): SanitizationSummary {
        lastStrippedUri = sourceUri
        lastInPlace = inPlace
        return SanitizationSummary(
            cleanUri = if (inPlace) sourceUri else "file://cache/sanitized_image.jpg",
            strippedTagsCount = 8,
            removedGps = true,
            isTempShareFile = !inPlace
        )
    }

    override suspend fun stripVideoMetadata(sourceUri: String, inPlace: Boolean): SanitizationSummary {
        lastStrippedUri = sourceUri
        lastInPlace = inPlace
        return SanitizationSummary(
            cleanUri = if (inPlace) sourceUri else "file://cache/sanitized_video.mp4",
            strippedTagsCount = 4,
            removedGps = true,
            isTempShareFile = !inPlace
        )
    }
}
