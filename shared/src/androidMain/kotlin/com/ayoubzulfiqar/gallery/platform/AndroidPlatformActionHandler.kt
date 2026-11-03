package com.ayoubzulfiqar.gallery.platform

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ayoubzulfiqar.gallery.security.AndroidMetadataStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AndroidPlatformActionHandler(
    private val context: Context,
    private val metadataStripper: AndroidMetadataStripper = AndroidMetadataStripper(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : PlatformActionHandler {

    override fun launchGoogleLens(contentUri: String): Boolean {
        val parsedUri = Uri.parse(contentUri)

        // Attempt 1: Native Google Lens standalone package
        try {
            val lensIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(parsedUri, "image/*")
                setPackage("com.google.ar.lens")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (lensIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(lensIntent)
                return true
            }
        } catch (_: Exception) {}

        // Attempt 2: Google Search app Lens Activity
        try {
            val searchLensIntent = Intent().apply {
                component = ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.search.lens.LensActivity"
                )
                setDataAndType(parsedUri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchLensIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(searchLensIntent)
                return true
            }
        } catch (_: Exception) {}

        // Attempt 3: General visual search / share intent
        return try {
            val genericIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(genericIntent, "Scan with Google Lens").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun setAsWallpaper(contentUri: String) {
        val parsedUri = Uri.parse(contentUri)
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val intent = wallpaperManager.getCropAndSetWallpaperIntent(parsedUri).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to standard ACTION_ATTACH_DATA picker
            try {
                val attachIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(parsedUri, "image/*")
                    putExtra("mimeType", "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(attachIntent, "Set as Wallpaper").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                // Graceful fallback
            }
        }
    }

    override fun shareSanitized(contentUri: String, mimeType: String) {
        scope.launch {
            val summary = if (mimeType.startsWith("video")) {
                metadataStripper.stripVideoMetadata(contentUri, inPlace = false)
            } else {
                metadataStripper.stripImageMetadata(contentUri, inPlace = false)
            }

            val cleanFile = File(Uri.parse(summary.cleanUri).path ?: "")
            val shareUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cleanFile
                )
            } catch (_: Exception) {
                Uri.parse(contentUri)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Securely (Metadata Stripped)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
