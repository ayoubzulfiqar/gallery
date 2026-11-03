package com.ayoubzulfiqar.gallery.data.datasource

import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

/** What the platform library supports, so the UI never offers an action that cannot work. */
data class MediaCapabilities(
    /** True when showing generated sample media instead of the device library. */
    val isDemoLibrary: Boolean = false,
    val canDelete: Boolean = true,
    /** Deleted items go to a recoverable system trash (Android 11+). */
    val deleteMovesToTrash: Boolean = false,
    /** The OS shows its own confirmation for deletions, so the app must not ask twice. */
    val systemConfirmsDeletion: Boolean = false,
    /** New media (vault restores, edited copies) can be written to the library. */
    val canImport: Boolean = true,
    /** Original bytes can be read (needed to move items into the vault). */
    val canReadContent: Boolean = true
)

enum class DeleteMode {
    /** Recoverable when the platform supports a trash. */
    TRASH,

    /** Irrecoverable; used after content was safely encrypted into the vault. */
    PERMANENT
}

data class DeleteResult(
    val deletedIds: Set<String>,
    val cancelled: Boolean = false,
    val errorMessage: String? = null
) {
    fun isCompleteFor(ids: Collection<String>): Boolean = ids.all { it in deletedIds }
}

data class MediaImportRequest(
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val dateTakenMillis: Long,
    /** Preferred folder, e.g. "DCIM/Camera/"; platforms fall back to a default folder. */
    val relativePath: String?
)

sealed interface ImportResult {
    data class Success(val uri: String) : ImportResult
    data class Failure(val message: String, val cause: Throwable? = null) : ImportResult
}

interface PlatformMediaDataSource {
    val capabilities: MediaCapabilities

    /** Hot or cold stream of the whole library, newest first. Re-emits whenever the library changes. */
    fun observeMedia(): Flow<List<MediaItem>>

    /** Requests a re-scan (e.g. after permissions changed or the app returned to the foreground). */
    fun refresh()

    suspend fun loadDetails(item: MediaItem): MediaDetails

    /** Deletes items, asking the user for consent where the platform requires it. */
    suspend fun deleteMedia(items: List<MediaItem>, mode: DeleteMode): DeleteResult

    /** Opens the original bytes for streaming. Caller closes. */
    suspend fun openContent(item: MediaItem): ByteSource

    /** Encoded (JPEG) thumbnail bytes, or null when none can be produced. */
    suspend fun createThumbnail(item: MediaItem, maxDimension: Int): ByteArray?

    /**
     * Creates a new library item and lets [writer] stream its bytes. The item only becomes visible
     * when [writer] completes successfully; on failure nothing is left behind.
     */
    suspend fun importMedia(request: MediaImportRequest, writer: suspend (ByteSink) -> Unit): ImportResult
}
