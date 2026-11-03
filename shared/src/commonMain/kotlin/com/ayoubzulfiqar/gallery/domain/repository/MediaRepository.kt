package com.ayoubzulfiqar.gallery.domain.repository

import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.data.datasource.DeleteResult
import com.ayoubzulfiqar.gallery.data.datasource.MediaCapabilities
import com.ayoubzulfiqar.gallery.domain.model.LibraryState
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    val capabilities: MediaCapabilities

    /** Single shared library stream (newest first) with favorites/tags applied and hidden items removed. */
    val library: Flow<LibraryState>

    /** Device folders derived from [library]. */
    val folders: Flow<List<MediaFolder>>

    fun refresh()

    suspend fun toggleFavorite(id: String): Boolean

    /** Favorites all [ids], or unfavorites them when [favorite] is false. */
    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean)

    suspend fun loadDetails(item: MediaItem): MediaDetails

    suspend fun deleteMedia(items: List<MediaItem>, mode: DeleteMode = DeleteMode.TRASH): DeleteResult

    suspend fun addTag(id: String, tag: String)

    suspend fun removeTag(id: String, tag: String)

    /** Temporarily hides items (e.g. while they are being moved into the vault). */
    fun hide(ids: Collection<String>)

    fun unhide(ids: Collection<String>)
}
