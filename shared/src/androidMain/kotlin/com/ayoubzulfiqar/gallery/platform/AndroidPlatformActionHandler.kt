package com.ayoubzulfiqar.gallery.platform

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.security.MetadataStripper
import com.ayoubzulfiqar.gallery.security.SanitizationResult
import java.io.File

/** Launches system activities on behalf of the UI. Must be bound to the foreground [Activity]. */
class AndroidPlatformActionHandler(
    private val activity: Activity,
    private val metadataStripper: MetadataStripper
) : PlatformActionHandler {

    override fun share(items: List<MediaItem>): ActionResult {
        if (items.isEmpty()) return ActionResult.Cancelled
        return startShare(items.map { Uri.parse(it.uri) }, items.map { it.mimeType }, "Share")
    }

    override suspend fun shareSanitized(items: List<MediaItem>): ActionResult {
        if (items.isEmpty()) return ActionResult.Cancelled
        val uris = mutableListOf<Uri>()
        val mimes = mutableListOf<String>()
        var tags = 0
        var locations = 0
        var failures = 0
        var firstFailure: String? = null
        for (item in items) {
            when (val result = metadataStripper.stripForSharing(item)) {
                is SanitizationResult.Success -> {
                    val file = File(requireNotNull(Uri.parse(result.summary.cleanUri).path))
                    uris += FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                    mimes += mimeForFile(file, item.mimeType)
                    tags += result.summary.strippedTagsCount
                    if (result.summary.removedGps) locations++
                }
                is SanitizationResult.Failure -> {
                    failures++
                    if (firstFailure == null) firstFailure = result.message
                }
                SanitizationResult.Cancelled -> Unit
            }
        }
        if (uris.isEmpty()) return ActionResult.Failed(firstFailure ?: "Nothing could be shared")
        val launch = startShare(uris, mimes, "Share without metadata")
        if (launch is ActionResult.Failed) return launch
        val summary = buildString {
            append(if (locations > 0) "Location removed from $locations item${if (locations == 1) "" else "s"}" else "Metadata removed")
            if (tags > 0) append(" · $tags tags stripped")
            if (failures > 0) append(". $failures skipped: $firstFailure")
        }
        return ActionResult.Done(summary)
    }

    private fun startShare(uris: List<Uri>, mimes: List<String>, title: String): ActionResult {
        val type = commonMimeType(mimes)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            this.type = type
            // ClipData carries the read grant to the receiver for every URI.
            clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(Intent.createChooser(intent, title))
    }

    override fun launchGoogleLens(item: MediaItem): ActionResult {
        val uri = Uri.parse(item.uri)
        val attempts = listOf(
            Intent(Intent.ACTION_SEND).apply {
                component = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensShareEntryPointActivity")
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                setPackage("com.google.ar.lens")
            },
            Intent(Intent.ACTION_SEND).apply {
                setPackage("com.google.android.googlequicksearchbox")
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            }
        )
        for (intent in attempts) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri(null, uri)
            if (tryStart(intent)) return ActionResult.Done()
        }
        return ActionResult.Failed("Google Lens isn't installed on this device")
    }

    override fun setAsWallpaper(item: MediaItem): ActionResult {
        val uri = Uri.parse(item.uri)
        val cropIntent = runCatching { WallpaperManager.getInstance(activity).getCropAndSetWallpaperIntent(uri) }.getOrNull()
        if (cropIntent != null) {
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (tryStart(cropIntent)) return ActionResult.Done()
        }
        val attach = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, item.mimeType)
            putExtra("mimeType", item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(Intent.createChooser(attach, "Set as"))
    }

    override fun playVideo(item: MediaItem): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.uri), item.mimeType.takeIf { it.startsWith("video/") } ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (tryStart(intent)) return ActionResult.Done()
        return ActionResult.Failed("No app can play this video")
    }

    override fun openMap(latitude: Double, longitude: Double, label: String): ActionResult {
        val geo = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
        if (tryStart(Intent(Intent.ACTION_VIEW, geo))) return ActionResult.Done()
        val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        return if (tryStart(Intent(Intent.ACTION_VIEW, web))) ActionResult.Done() else ActionResult.Failed("No maps app available")
    }

    override fun openAppSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        return if (tryStart(intent)) ActionResult.Done() else ActionResult.Failed("Couldn't open settings")
    }

    private fun launch(intent: Intent): ActionResult =
        if (tryStart(intent)) ActionResult.Done() else ActionResult.Failed("No app available for this action")

    private fun tryStart(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun commonMimeType(mimes: List<String>): String {
        val distinct = mimes.distinct()
        if (distinct.size == 1) return distinct.first()
        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.first()}/*" else "*/*"
    }

    private fun mimeForFile(file: File, fallback: String): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> fallback
    }
}
