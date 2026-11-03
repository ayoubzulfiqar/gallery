package com.ayoubzulfiqar.gallery.data.datasource

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AndroidPlatformMediaDataSource(
    private val contentResolver: ContentResolver
) : PlatformMediaDataSource {

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
        MediaStore.Files.FileColumns.SIZE
    )

    override fun observeMedia(batchSize: Int): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(queryMediaWindowSync(offset = 0, limit = batchSize))
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        // Initial emission
        trySend(queryMediaWindowSync(offset = 0, limit = batchSize))

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun queryMediaWindow(offset: Int, limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        queryMediaWindowSync(offset, limit)
    }

    private fun queryMediaWindowSync(offset: Int, limit: Int): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                    putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit OFFSET $offset"
                contentResolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
            }

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val widthColumn = c.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightColumn = c.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val sizeColumn = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val rawMediaType = c.getInt(mediaTypeColumn)
                    val isVideo = rawMediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val contentUri = ContentUris.withAppendedId(
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val modSeconds = if (dateModifiedColumn != -1 && !c.isNull(dateModifiedColumn)) {
                        c.getLong(dateModifiedColumn)
                    } else 0L
                    val dateMillis = if (modSeconds > 0L) modSeconds * 1000L else System.currentTimeMillis()

                    val width = if (widthColumn != -1 && !c.isNull(widthColumn)) c.getInt(widthColumn) else 1920
                    val height = if (heightColumn != -1 && !c.isNull(heightColumn)) c.getInt(heightColumn) else 1080
                    val sizeBytes = if (sizeColumn != -1 && !c.isNull(sizeColumn)) c.getLong(sizeColumn) else 0L

                    items.add(
                        MediaItem(
                            id = id.toString(),
                            uri = contentUri.toString(),
                            displayName = (if (!c.isNull(nameColumn)) c.getString(nameColumn) else null) ?: "media_$id",
                            mimeType = (if (!c.isNull(mimeColumn)) c.getString(mimeColumn) else null) ?: if (isVideo) "video/mp4" else "image/jpeg",
                            mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                            dateTakenMillis = dateMillis,
                            dateModifiedMillis = dateMillis,
                            width = width,
                            height = height,
                            sizeBytes = sizeBytes,
                            durationMs = if (isVideo) 60_000L else null,
                            orientation = 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AndroidPlatformMediaDataSource", "Query failed", e)
        }
        return items
    }

    override suspend fun deleteMedia(ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        for (idStr in ids) {
            val id = idStr.toLongOrNull() ?: continue
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            try {
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted <= 0) {
                    val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    contentResolver.delete(videoUri, null, null)
                }
            } catch (_: Exception) {
                allSuccess = false
            }
        }
        allSuccess
    }

    override suspend fun fetchExif(id: String): ExifMetadata? = withContext(Dispatchers.IO) {
        null
    }
}
