package com.ayoubzulfiqar.gallery.data.datasource

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.core.io.asByteSink
import com.ayoubzulfiqar.gallery.core.io.asByteSource
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.platform.AndroidMediaPermissionController
import com.ayoubzulfiqar.gallery.platform.MediaAccess
import com.ayoubzulfiqar.gallery.platform.MediaStoreConsentBroker
import com.ayoubzulfiqar.gallery.ui.common.AndroidBitmaps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class AndroidMediaStoreDataSource(
    private val context: Context,
    private val consentBroker: MediaStoreConsentBroker,
    private val permissions: AndroidMediaPermissionController
) : PlatformMediaDataSource {

    private val resolver: ContentResolver = context.contentResolver
    private val refreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val capabilities = MediaCapabilities(
        isDemoLibrary = false,
        canDelete = true,
        deleteMovesToTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        systemConfirmsDeletion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        canImport = true,
        canReadContent = true
    )

    private val filesUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }

    override fun refresh() {
        refreshSignals.tryEmit(Unit)
    }

    override fun observeMedia(): Flow<List<MediaItem>> = channelFlow {
        val triggers = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                triggers.trySend(Unit)
            }
        }
        resolver.registerContentObserver(filesUri, true, observer)
        launch { refreshSignals.collect { triggers.trySend(Unit) } }
        launch { permissions.access.drop(1).collect { triggers.trySend(Unit) } }
        try {
            triggers.send(Unit)
            var first = true
            for (signal in triggers) {
                val access = permissions.access.value
                if (access != MediaAccess.FULL && access != MediaAccess.PARTIAL) {
                    send(emptyList())
                } else {
                    val items = queryLibrary(onFirstPage = if (first) { page -> send(page) } else null)
                    send(items)
                }
                first = false
                // MediaStore fires bursts of change notifications during scans; coalesce them.
                delay(CHANGE_DEBOUNCE_MS)
            }
        } finally {
            resolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun queryLibrary(onFirstPage: (suspend (List<MediaItem>) -> Unit)?): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.WIDTH)
            add(MediaStore.Files.FileColumns.HEIGHT)
            add(MediaStore.Files.FileColumns.SIZE)
            add(COLUMN_DATE_TAKEN)
            add(COLUMN_DURATION)
            add(COLUMN_ORIENTATION)
            add(COLUMN_BUCKET_ID)
            add(COLUMN_BUCKET_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH) else add(COLUMN_DATA)
        }.toTypedArray()
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "$COLUMN_DATE_TAKEN DESC, ${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val items = ArrayList<MediaItem>(1024)
        try {
            resolver.query(filesUri, projection, selection, args, sortOrder)?.use { cursor ->
                val columns = Columns(cursor)
                while (cursor.moveToNext()) {
                    columns.read(cursor)?.let(items::add)
                    if (onFirstPage != null && items.size == FIRST_PAGE_SIZE) onFirstPage(items.toList())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Media permission revoked during query", e)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
        }
        return items
    }

    private inner class Columns(cursor: Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        private val name = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
        private val mime = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
        private val mediaType = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        private val modified = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
        private val added = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
        private val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        private val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        private val size = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        private val taken = cursor.getColumnIndex(COLUMN_DATE_TAKEN)
        private val duration = cursor.getColumnIndex(COLUMN_DURATION)
        private val orientation = cursor.getColumnIndex(COLUMN_ORIENTATION)
        private val bucketId = cursor.getColumnIndex(COLUMN_BUCKET_ID)
        private val bucketName = cursor.getColumnIndex(COLUMN_BUCKET_NAME)
        private val relativePath = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        private val data = cursor.getColumnIndex(COLUMN_DATA)

        fun read(c: Cursor): MediaItem? {
            val rowId = c.getLong(id)
            val isVideo = c.intOrNull(mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val mimeType = c.stringOrNull(mime) ?: if (isVideo) "video/mp4" else "image/jpeg"
            val contentUri = ContentUris.withAppendedId(
                if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                rowId
            )
            val modifiedMillis = (c.longOrNull(modified) ?: 0L) * 1000L
            val addedMillis = (c.longOrNull(added) ?: 0L) * 1000L
            val takenMillis = c.longOrNull(taken)?.takeIf { it > 0 } ?: modifiedMillis.takeIf { it > 0 } ?: addedMillis
            val path = c.stringOrNull(relativePath) ?: c.stringOrNull(data)?.let(::relativePathFromData)
            return MediaItem(
                id = rowId.toString(),
                uri = contentUri.toString(),
                displayName = c.stringOrNull(name) ?: "media_$rowId",
                mimeType = mimeType,
                mediaType = MediaType.fromMimeType(mimeType, isVideoHint = isVideo).let { if (isVideo) MediaType.VIDEO else it },
                dateTakenMillis = takenMillis,
                dateModifiedMillis = modifiedMillis,
                width = c.intOrNull(width) ?: 0,
                height = c.intOrNull(height) ?: 0,
                sizeBytes = c.longOrNull(size) ?: 0L,
                durationMs = if (isVideo) c.longOrNull(duration)?.takeIf { it > 0 } else null,
                orientation = (c.intOrNull(orientation) ?: 0).let { ((it % 360) + 360) % 360 },
                bucketId = c.stringOrNull(bucketId),
                bucketName = c.stringOrNull(bucketName),
                relativePath = path
            )
        }
    }

    override suspend fun loadDetails(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val locationAllowed = permissions.hasLocationAccess()
        val exif = try {
            if (item.isVideo) videoMetadata(item) else imageExif(item, locationAllowed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read metadata for ${item.id}", e)
            null
        }
        MediaDetails(item = item, exif = exif, locationRedacted = !locationAllowed && exif?.hasLocation != true)
    }

    private fun imageExif(item: MediaItem, locationAllowed: Boolean): ExifMetadata? {
        val stream = openOriginalStream(Uri.parse(item.uri), locationAllowed) ?: return null
        return stream.use { input ->
            val exif = ExifInterface(input)
            val latLong = exif.latLong
            ExifMetadata(
                make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf(String::isNotEmpty),
                model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf(String::isNotEmpty),
                lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf(String::isNotEmpty),
                iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
                aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 },
                shutterSpeed = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let(::formatExposure),
                focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 },
                flash = if (exif.hasAttribute(ExifInterface.TAG_FLASH)) {
                    if (exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 == 1) "Fired" else "Off"
                } else {
                    null
                },
                whiteBalance = if (exif.hasAttribute(ExifInterface.TAG_WHITE_BALANCE)) {
                    if (exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, 0) == 0) "Auto" else "Manual"
                } else {
                    null
                },
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
                altitude = if (exif.hasAttribute(ExifInterface.TAG_GPS_ALTITUDE)) exif.getAltitude(0.0) else null,
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()?.takeIf(String::isNotEmpty),
                pixelWidth = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 },
                pixelHeight = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
            )
        }
    }

    private fun videoMetadata(item: MediaItem): ExifMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(item.uri))
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let(::parseIso6709)
            ExifMetadata(
                latitude = location?.first,
                longitude = location?.second,
                pixelWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                pixelHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Opens the unredacted original (with GPS) when ACCESS_MEDIA_LOCATION is granted. */
    private fun openOriginalStream(uri: Uri, locationAllowed: Boolean): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && locationAllowed) {
            try {
                return resolver.openInputStream(MediaStore.setRequireOriginal(uri))
            } catch (_: SecurityException) {
                // Not permitted (e.g. partial access); fall back to the redacted stream.
            } catch (_: UnsupportedOperationException) {
            }
        }
        return resolver.openInputStream(uri)
    }

    override suspend fun deleteMedia(items: List<MediaItem>, mode: DeleteMode): DeleteResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext DeleteResult(emptySet())
        val uris = items.map { Uri.parse(it.uri) }
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Scoped storage: deleting media the app does not own requires user consent.
                    val request = if (mode == DeleteMode.TRASH) {
                        MediaStore.createTrashRequest(resolver, uris, true)
                    } else {
                        MediaStore.createDeleteRequest(resolver, uris)
                    }
                    if (!consentBroker.requestConsent(request.intentSender)) {
                        return@withContext DeleteResult(emptySet(), cancelled = true)
                    }
                    verifiedDeletion(items)
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> deleteOnAndroid10(items)
                else -> {
                    if (!permissions.canWriteLegacyStorage()) {
                        return@withContext DeleteResult(emptySet(), errorMessage = "Storage permission is needed to delete")
                    }
                    items.forEach { runCatching { resolver.delete(Uri.parse(it.uri), null, null) } }
                    verifiedDeletion(items)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed", e)
            verifiedDeletion(items).copy(errorMessage = e.message ?: "Delete failed")
        }
    }

    /** Android 10 has no batch consent: each foreign item raises a RecoverableSecurityException. */
    private suspend fun deleteOnAndroid10(items: List<MediaItem>): DeleteResult {
        for (item in items) {
            val uri = Uri.parse(item.uri)
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                val recoverable = e as? RecoverableSecurityException ?: throw e
                if (!consentBroker.requestConsent(recoverable.userAction.actionIntent.intentSender)) {
                    return verifiedDeletion(items).copy(cancelled = true)
                }
                resolver.delete(uri, null, null)
            }
        }
        return verifiedDeletion(items)
    }

    /** Reports exactly which items are gone (trashed items no longer appear in default queries). */
    private fun verifiedDeletion(items: List<MediaItem>): DeleteResult {
        val stillPresent = existingIds(items.map { it.id })
        return DeleteResult(items.map { it.id }.filterTo(HashSet()) { it !in stillPresent })
    }

    private fun existingIds(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val found = HashSet<String>()
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns._ID} IN ($placeholders)",
                chunk.toTypedArray(),
                null
            )?.use { c -> while (c.moveToNext()) found += c.getLong(0).toString() }
        }
        return found
    }

    override suspend fun openContent(item: MediaItem): ByteSource = withContext(Dispatchers.IO) {
        val stream = openOriginalStream(Uri.parse(item.uri), permissions.hasLocationAccess())
            ?: throw IllegalStateException("Cannot open ${item.displayName}")
        stream.buffered(256 * 1024).asByteSource()
    }

    override suspend fun createThumbnail(item: MediaItem, maxDimension: Int): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = AndroidBitmaps.thumbnail(context, Uri.parse(item.uri), item.isVideo, maxDimension, null) ?: return@withContext null
        try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun importMedia(request: MediaImportRequest, writer: suspend (ByteSink) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) importScoped(request, writer) else importLegacy(request, writer)
        }

    private suspend fun importScoped(request: MediaImportRequest, writer: suspend (ByteSink) -> Unit): ImportResult {
        val isVideo = request.mediaType == MediaType.VIDEO
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        fun values(path: String) = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, request.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, request.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (request.dateTakenMillis > 0) put(MediaStore.MediaColumns.DATE_TAKEN, request.dateTakenMillis)
        }
        val preferred = request.relativePath?.takeIf { isAllowedDirectory(it, isVideo) }
        val fallback = if (isVideo) "${Environment.DIRECTORY_MOVIES}/$RESTORE_FOLDER/" else "${Environment.DIRECTORY_PICTURES}/$RESTORE_FOLDER/"
        val uri = try {
            preferred?.let { resolver.insert(collection, values(it)) } ?: resolver.insert(collection, values(fallback))
        } catch (_: IllegalArgumentException) {
            resolver.insert(collection, values(fallback))
        } ?: return ImportResult.Failure("Couldn't create a new file in your gallery")

        return try {
            val output = resolver.openOutputStream(uri, "w") ?: throw IllegalStateException("Couldn't open the new file for writing")
            output.buffered(256 * 1024).asByteSink().use { sink -> writer(sink) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            ImportResult.Success(uri.toString())
        } catch (e: Throwable) {
            // Never leave a half-written or pending item behind.
            runCatching { resolver.delete(uri, null, null) }
            if (e is CancellationException) throw e
            ImportResult.Failure(e.message ?: "Couldn't save the file", e)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun importLegacy(request: MediaImportRequest, writer: suspend (ByteSink) -> Unit): ImportResult {
        if (!permissions.canWriteLegacyStorage()) return ImportResult.Failure("Storage permission is needed to save files")
        val isVideo = request.mediaType == MediaType.VIDEO
        val root = Environment.getExternalStorageDirectory()
        val folder = request.relativePath?.let { File(root, it) }?.takeIf { it.isDirectory || it.mkdirs() }
            ?: File(Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), RESTORE_FOLDER)
                .apply { mkdirs() }
        val target = uniqueFile(folder, request.displayName)
        return try {
            target.outputStream().buffered(256 * 1024).asByteSink().use { sink -> writer(sink) }
            if (request.dateTakenMillis > 0) target.setLastModified(request.dateTakenMillis)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(request.mimeType), null)
            ImportResult.Success(Uri.fromFile(target).toString())
        } catch (e: Throwable) {
            target.delete()
            if (e is CancellationException) throw e
            ImportResult.Failure(e.message ?: "Couldn't save the file", e)
        }
    }

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var counter = 1
        while (candidate.exists()) {
            candidate = File(folder, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun isAllowedDirectory(relativePath: String, isVideo: Boolean): Boolean {
        val top = relativePath.trimStart('/').substringBefore('/')
        val allowed = if (isVideo) {
            setOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES)
        } else {
            setOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES)
        }
        return top in allowed
    }

    companion object {
        private const val TAG = "MediaStoreDataSource"
        private const val FIRST_PAGE_SIZE = 600
        private const val CHANGE_DEBOUNCE_MS = 400L
        private const val RESTORE_FOLDER = "Restored"

        // Column names shared by MediaColumns (API 29+) and the legacy files table.
        private const val COLUMN_DATE_TAKEN = "datetaken"
        private const val COLUMN_DURATION = "duration"
        private const val COLUMN_ORIENTATION = "orientation"
        private const val COLUMN_BUCKET_ID = "bucket_id"
        private const val COLUMN_BUCKET_NAME = "bucket_display_name"
        private const val COLUMN_DATA = "_data"

        fun relativePathFromData(data: String): String? {
            val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
            if (!data.startsWith(root)) return null
            return data.removePrefix(root).substringBeforeLast('/', "").let { if (it.isEmpty()) null else "$it/" }
        }

        fun formatExposure(seconds: Double): String = when {
            seconds >= 1.0 -> "${if (seconds % 1.0 == 0.0) seconds.toLong() else seconds}s"
            else -> "1/${(1.0 / seconds).let { kotlin.math.round(it).toLong() }}"
        }

        /** Parses ISO-6709 strings such as "+37.4219-122.0840/" or "+37.42+122.08+12.5/". */
        fun parseIso6709(value: String): Pair<Double, Double>? {
            val match = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(value) ?: return null
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return lat to lon
        }
    }
}

private fun Cursor.stringOrNull(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null
private fun Cursor.longOrNull(index: Int): Long? = if (index >= 0 && !isNull(index)) getLong(index) else null
private fun Cursor.intOrNull(index: Int): Int? = if (index >= 0 && !isNull(index)) getInt(index) else null
