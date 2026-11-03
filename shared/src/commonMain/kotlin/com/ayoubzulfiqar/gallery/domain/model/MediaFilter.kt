package com.ayoubzulfiqar.gallery.domain.model

enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FAVORITES("Favorites");

    fun matches(item: MediaItem): Boolean = when (this) {
        ALL -> true
        PHOTOS -> !item.isVideo
        VIDEOS -> item.isVideo
        FAVORITES -> item.isFavorite
    }
}

/** Which slice of the library the viewer pages through, so swiping stays within the screen it was opened from. */
sealed interface MediaScope {
    data class Library(val filter: MediaFilter = MediaFilter.ALL) : MediaScope
    data class Folder(val folderId: String, val filter: MediaFilter = MediaFilter.ALL) : MediaScope

    fun includes(item: MediaItem): Boolean = when (this) {
        is Library -> filter.matches(item)
        is Folder -> item.folderKey == folderId && filter.matches(item)
    }

    /**
     * Items of this scope in display order. The main "All" timeline shows every photo first and
     * every video after them; grids and the viewer share this rule so paging matches the grid.
     */
    fun arrange(library: List<MediaItem>): List<MediaItem> {
        val inScope = if (this is Library && filter == MediaFilter.ALL) library else library.filter(::includes)
        return if (this is Library && filter == MediaFilter.ALL) MediaOrdering.photosThenVideos(inScope) else inScope
    }
}

object MediaOrdering {
    /** Stable single-pass partition: photos (newest first) followed by videos (newest first). */
    fun photosThenVideos(items: List<MediaItem>): List<MediaItem> {
        if (items.isEmpty()) return items
        var videoCount = 0
        for (item in items) if (item.isVideo) videoCount++
        if (videoCount == 0 || videoCount == items.size) return items
        val ordered = ArrayList<MediaItem>(items.size)
        for (item in items) if (!item.isVideo) ordered += item
        for (item in items) if (item.isVideo) ordered += item
        return ordered
    }
}
