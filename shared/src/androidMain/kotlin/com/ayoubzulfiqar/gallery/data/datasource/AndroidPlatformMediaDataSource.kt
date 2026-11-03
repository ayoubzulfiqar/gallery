package com.ayoubzulfiqar.gallery.data.datasource

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
        MediaStore.Files.FileColumns.DATE_TAKEN,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.ORIENTATION,
        MediaStore.Video.VideoColumns.DURATION
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
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC LIMIT $limit OFFSET $offset"

        try {
            contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ORIENTATION)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val rawMediaType = cursor.getInt(mediaTypeColumn)
                    val isVideo = rawMediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val contentUri = ContentUris.withAppendedId(
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val duration = if (durationColumn != -1 && isVideo) {
                        cursor.getLong(durationColumn)
                    } else null

                    items.add(
                        MediaItem(
                            id = id.toString(),
                            uri = contentUri.toString(),
                            displayName = cursor.getString(nameColumn) ?: "media_$id",
                            mimeType = cursor.getString(mimeColumn) ?: if (isVideo) "video/mp4" else "image/jpeg",
                            mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                            dateTakenMillis = cursor.getLong(dateTakenColumn).let { if (it > 0) it else cursor.getLong(dateModifiedColumn) * 1000L },
                            dateModifiedMillis = cursor.getLong(dateModifiedColumn) * 1000L,
                            width = cursor.getInt(widthColumn),
                            height = cursor.getInt(heightColumn),
                            sizeBytes = cursor.getLong(sizeColumn),
                            durationMs = duration,
                            orientation = cursor.getInt(orientationColumn)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback for permission or cursor errors
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
