package com.ayoubzulfiqar.gallery.security

import android.app.RecoverableSecurityException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.ayoubzulfiqar.gallery.core.io.asByteSink
import com.ayoubzulfiqar.gallery.core.io.asByteSource
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.platform.AndroidMediaPermissionController
import com.ayoubzulfiqar.gallery.platform.MediaStoreConsentBroker
import com.ayoubzulfiqar.gallery.ui.common.AndroidBitmaps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class AndroidMetadataStripper(
    private val context: Context,
    private val consentBroker: MediaStoreConsentBroker,
    private val permissions: AndroidMediaPermissionController
) : MetadataStripper {

    private val resolver = context.contentResolver
    private val shareDir: File get() = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
    private val workDir: File get() = File(context.cacheDir, WORK_DIR).apply { mkdirs() }

    override fun supportsInPlace(item: MediaItem): Boolean =
        !item.isVideo && item.mimeType.lowercase() in setOf("image/jpeg", "image/jpg", "image/png")

    override suspend fun stripForSharing(item: MediaItem): SanitizationResult = withContext(Dispatchers.IO) {
        purgeOldShareFiles(maxAgeMillis = SHARE_FILE_TTL_MS)
        val uri = Uri.parse(item.uri)
        try {
            val summary = if (item.isVideo) remuxVideo(item, uri) else sanitizeImageCopy(item, uri)
            SanitizationResult.Success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sanitize failed for ${item.displayName}", e)
            SanitizationResult.Failure(
                if (item.isVideo) "Couldn't remove metadata from ${item.displayName}; it wasn't shared"
                else "Couldn't sanitize ${item.displayName}; it wasn't shared"
            )
        }
    }

    private fun sanitizeImageCopy(item: MediaItem, uri: Uri): SanitizationSummary {
        val head = resolver.openInputStream(uri)?.use { input -> ByteArray(16).also { input.read(it) } }
            ?: throw IllegalStateException("Cannot open image")
        val baseName = item.displayName.substringBeforeLast('.').sanitizeFileName()
        return when (ImageContainerSniffer.sniff(head)) {
            ImageContainer.JPEG, ImageContainer.PNG -> {
                val container = ImageContainerSniffer.sniff(head)
                val out = uniqueShareFile(baseName, if (container == ImageContainer.PNG) "png" else "jpg")
                val report = stripLossless(uri, out)
                SanitizationSummary(
                    cleanUri = Uri.fromFile(out).toString(),
                    strippedTagsCount = report.removedExifTags,
                    removedGps = report.removedLocation,
                    isTempShareFile = true,
                    method = if (report.removedBlocks == 0) SanitizationMethod.UNCHANGED else SanitizationMethod.LOSSLESS
                )
            }
            ImageContainer.OTHER -> {
                if (item.mimeType.equals("image/gif", ignoreCase = true)) {
                    // GIF has no EXIF/GPS; re-encoding would destroy the animation.
                    val out = uniqueShareFile(baseName, "gif")
                    resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                    return SanitizationSummary(Uri.fromFile(out).toString(), 0, false, true, SanitizationMethod.UNCHANGED)
                }
                // HEIC/WebP/RAW: decode (orientation applied) and re-encode without any metadata.
                val bitmap = AndroidBitmaps.decodeImage(resolver, uri, MAX_REENCODE_DIMENSION, software = true)
                    ?: throw IllegalStateException("Unsupported image format")
                val out = uniqueShareFile(baseName, "jpg")
                try {
                    out.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                } finally {
                    bitmap.recycle()
                }
                SanitizationSummary(Uri.fromFile(out).toString(), 0, removedGps = true, isTempShareFile = true, method = SanitizationMethod.REENCODED)
            }
        }
    }

    private fun stripLossless(uri: Uri, out: File): StripReport {
        val head = ByteArray(16)
        resolver.openInputStream(uri)?.use { it.read(head) } ?: throw IllegalStateException("Cannot open image")
        val container = ImageContainerSniffer.sniff(head)
        val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open image")
        return input.buffered(256 * 1024).asByteSource().use { source ->
            out.outputStream().buffered(256 * 1024).asByteSink().use { sink ->
                when (container) {
                    ImageContainer.JPEG -> JpegMetadataStripper.strip(source, sink)
                    ImageContainer.PNG -> PngMetadataStripper.strip(source, sink)
                    ImageContainer.OTHER -> throw UnsupportedImageFormatException("Not a JPEG or PNG file")
                }
            }
        }
    }

    private fun countBytes(uri: Uri): Long {
        val buffer = ByteArray(256 * 1024)
        var total = 0L
        resolver.openInputStream(uri)?.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
            }
        } ?: return -1L
        return total
    }

    /** Rewrites the container without metadata atoms (GPS, maker data); audio/video samples are copied untouched. */
    private fun remuxVideo(item: MediaItem, uri: Uri): SanitizationSummary {
        val isWebm = item.mimeType.contains("webm", ignoreCase = true)
        val out = uniqueShareFile(item.displayName.substringBeforeLast('.').sanitizeFileName(), if (isWebm) "webm" else "mp4")
        val hadLocation = runCatching {
            MediaMetadataRetriever().let { r ->
                try {
                    r.setDataSource(context, uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) != null
                } finally {
                    r.release()
                }
            }
        }.getOrDefault(false)

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            muxer = MediaMuxer(out.absolutePath, if (isWebm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = HashMap<Int, Int>()
            var bufferSize = 1024 * 1024
            var rotation = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue // drop metadata/text tracks
                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                trackMap[i] = muxer.addTrack(format)
                extractor.selectTrack(i)
            }
            if (trackMap.isEmpty()) throw IllegalStateException("No audio or video tracks")
            if (rotation == 0) {
                rotation = runCatching {
                    MediaMetadataRetriever().let { r ->
                        try {
                            r.setDataSource(context, uri)
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        } finally {
                            r.release()
                        }
                    }
                }.getOrDefault(0)
            }
            if (rotation != 0) muxer.setOrientationHint(rotation)
            muxer.start()
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val target = trackMap[track]
                if (target != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(target, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
        return SanitizationSummary(Uri.fromFile(out).toString(), 0, removedGps = hadLocation, isTempShareFile = true, method = SanitizationMethod.REMUXED)
    }

    override suspend fun stripInPlace(item: MediaItem): SanitizationResult = withContext(Dispatchers.IO) {
        if (!supportsInPlace(item)) return@withContext SanitizationResult.Failure("Only JPEG and PNG photos can be cleaned in place")
        val uri = Uri.parse(item.uri)
        val stripped = File(workDir, "stripped_${System.nanoTime()}")
        val backup = File(workDir, "backup_${System.nanoTime()}")
        try {
            val report = stripLossless(uri, stripped)
            if (report.removedBlocks == 0) {
                return@withContext SanitizationResult.Success(
                    SanitizationSummary(item.uri, 0, false, isTempShareFile = false, method = SanitizationMethod.UNCHANGED)
                )
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(stripped.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@withContext SanitizationResult.Failure("Safety check failed; the original was not modified")

            if (!ensureWriteAccess(uri)) return@withContext SanitizationResult.Cancelled

            resolver.openInputStream(uri)!!.use { input -> backup.outputStream().use { input.copyTo(it) } }
            try {
                writeInto(uri, stripped)
                check(countBytes(uri) == stripped.length()) { "Size mismatch after write" }
            } catch (e: Exception) {
                Log.e(TAG, "In-place write failed, restoring backup", e)
                runCatching { writeInto(uri, backup) }
                return@withContext SanitizationResult.Failure("Couldn't update the file; it was left unchanged")
            }
            SanitizationResult.Success(
                SanitizationSummary(item.uri, report.removedExifTags, report.removedLocation, isTempShareFile = false, method = SanitizationMethod.LOSSLESS)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            SanitizationResult.Failure("Gallery isn't allowed to modify this file")
        } catch (e: Exception) {
            Log.w(TAG, "In-place strip failed", e)
            SanitizationResult.Failure(e.message ?: "Couldn't remove metadata")
        } finally {
            stripped.delete()
            backup.delete()
        }
    }

    private fun writeInto(uri: Uri, from: File) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Cannot open file for writing")
        output.use { out -> from.inputStream().use { it.copyTo(out, 256 * 1024) } }
    }

    private suspend fun ensureWriteAccess(uri: Uri): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                consentBroker.requestConsent(MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender)
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> try {
                resolver.openFileDescriptor(uri, "rw")?.close()
                true
            } catch (e: RecoverableSecurityException) {
                consentBroker.requestConsent(e.userAction.actionIntent.intentSender)
            }
            else -> permissions.canWriteLegacyStorage()
        }
    }

    private fun uniqueShareFile(base: String, extension: String): File {
        val dir = File(shareDir, System.nanoTime().toString()).apply { mkdirs() }
        return File(dir, "${base.ifEmpty { "shared" }}.$extension")
    }

    /** Share copies must outlive the share sheet briefly (receivers read lazily), then go away. */
    fun purgeOldShareFiles(maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        shareDir.listFiles()?.forEach { dir -> if (dir.lastModified() < cutoff) dir.deleteRecursively() }
        workDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)

    companion object {
        private const val TAG = "MetadataStripper"
        const val SHARE_DIR = "sanitized"
        private const val WORK_DIR = "sanitize_work"
        private const val SHARE_FILE_TTL_MS = 60 * 60 * 1000L
        private const val MAX_REENCODE_DIMENSION = 8192
    }
}
