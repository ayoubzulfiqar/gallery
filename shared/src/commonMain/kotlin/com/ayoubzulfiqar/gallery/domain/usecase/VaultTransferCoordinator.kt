package com.ayoubzulfiqar.gallery.domain.usecase

import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.data.datasource.ImportResult
import com.ayoubzulfiqar.gallery.data.datasource.MediaImportRequest
import com.ayoubzulfiqar.gallery.data.datasource.PlatformMediaDataSource
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.VaultError
import com.ayoubzulfiqar.gallery.domain.model.VaultItem
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import com.ayoubzulfiqar.gallery.domain.repository.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class TransferProgress(val completed: Int, val total: Int)

data class TransferReport(
    val succeeded: Int,
    val failed: Int,
    /** Items that were encrypted but put back because the user declined deleting the original. */
    val keptInGallery: Int = 0,
    val cancelled: Boolean = false,
    val firstError: String? = null
) {
    val total: Int get() = succeeded + failed + keptInGallery
}

/**
 * Moves media between the device library and the vault without any step that can lose data:
 *
 * Move: encrypt → verify committed → delete original (with user consent) → roll back the vault
 * copy of anything whose original could not be deleted.
 *
 * Restore: decrypt into a pending library item → publish only after authentication succeeded →
 * remove from vault.
 */
class VaultTransferCoordinator(
    private val mediaSource: PlatformMediaDataSource,
    private val mediaRepository: MediaRepository,
    private val vault: VaultRepository
) {

    suspend fun moveToVault(
        items: List<MediaItem>,
        onProgress: (TransferProgress) -> Unit = {}
    ): TransferReport {
        if (items.isEmpty()) return TransferReport(0, 0)
        val ids = items.map { it.id }
        mediaRepository.hide(ids) // instant isolation in every gallery screen
        val imported = mutableListOf<Pair<MediaItem, VaultItem>>()
        var deletionStarted = false
        var failed = 0
        var firstError: String? = null
        try {
            items.forEachIndexed { index, item ->
                try {
                    val thumbnail = runCatching { mediaSource.createThumbnail(item, THUMBNAIL_SIZE) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    val vaultItem = mediaSource.openContent(item).use { source -> vault.add(item, source, thumbnail) }
                    imported += item to vaultItem
                } catch (e: CancellationException) {
                    throw e
                } catch (e: VaultError.VaultLocked) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    if (firstError == null) firstError = "${item.displayName}: ${e.message ?: "could not be encrypted"}"
                }
                onProgress(TransferProgress(index + 1, items.size))
            }

            if (imported.isEmpty()) {
                return TransferReport(0, failed, firstError = firstError)
            }

            // Consent dialog + bookkeeping must not be torn apart by cancellation: once the user has
            // confirmed, the system may already have deleted the originals.
            deletionStarted = true
            val deletion = withContext(NonCancellable) {
                mediaRepository.deleteMedia(imported.map { it.first }, DeleteMode.PERMANENT)
            }
            val notDeleted = imported.filter { (original, _) -> original.id !in deletion.deletedIds }
            if (notDeleted.isNotEmpty()) {
                withContext(NonCancellable) { vault.remove(notDeleted.map { it.second.id }) }
            }
            return TransferReport(
                succeeded = imported.size - notDeleted.size,
                failed = failed,
                keptInGallery = notDeleted.size,
                cancelled = deletion.cancelled,
                firstError = firstError ?: deletion.errorMessage
            )
        } catch (e: Throwable) {
            // Failed before any original was touched: drop the vault copies. If deletion had already
            // started we cannot know what the system removed, so the encrypted copies are kept
            // (a duplicate is recoverable, a loss is not).
            if (imported.isNotEmpty() && !deletionStarted) {
                withContext(NonCancellable) { runCatching { vault.remove(imported.map { it.second.id }) } }
            }
            throw e
        } finally {
            mediaRepository.unhide(ids)
        }
    }

    suspend fun restore(
        items: List<VaultItem>,
        onProgress: (TransferProgress) -> Unit = {}
    ): TransferReport {
        var succeeded = 0
        var failed = 0
        var firstError: String? = null
        items.forEachIndexed { index, item ->
            val result = mediaSource.importMedia(
                MediaImportRequest(
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    mediaType = item.mediaType,
                    dateTakenMillis = item.dateTakenMillis,
                    relativePath = item.relativePath
                )
            ) { sink -> vault.export(item.id, sink) }
            when (result) {
                is ImportResult.Success -> {
                    withContext(NonCancellable) { vault.remove(listOf(item.id)) }
                    succeeded++
                }
                is ImportResult.Failure -> {
                    failed++
                    if (firstError == null) {
                        firstError = if (result.cause is VaultError.IntegrityCompromised) {
                            "${item.displayName} is damaged and was not restored"
                        } else {
                            "${item.displayName}: ${result.message}"
                        }
                    }
                }
            }
            onProgress(TransferProgress(index + 1, items.size))
        }
        return TransferReport(succeeded, failed, firstError = firstError)
    }

    companion object {
        const val THUMBNAIL_SIZE = 320
    }
}
