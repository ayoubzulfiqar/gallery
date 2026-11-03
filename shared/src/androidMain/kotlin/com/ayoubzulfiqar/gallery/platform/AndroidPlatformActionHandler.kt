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

        // Attempt 1: Direct Google Search Image / Lens Share Entry Point (bypasses any share dialog)
        try {
            val lensShareIntent = Intent(Intent.ACTION_SEND).apply {
                component = ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.search.lens.LensShareEntryPointActivity"
                )
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (lensShareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(lensShareIntent)
                return true
            }
        } catch (_: Exception) {}

        // Attempt 2: Standalone Google Lens app (com.google.ar.lens)
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

        // Attempt 3: Direct Google Search app package target (without chooser)
        try {
            val directSearchIntent = Intent(Intent.ACTION_SEND).apply {
                setPackage("com.google.android.googlequicksearchbox")
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (directSearchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(directSearchIntent)
                return true
            }
        } catch (_: Exception) {}

        // Attempt 4: Fallback to system chooser
        return try {
            val genericIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(genericIntent, "Search with Google Lens").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun playVideo(contentUri: String): Boolean {
        android.util.Log.d("GalleryAction", "playVideo invoked for URI: $contentUri")
        val parsedUri = Uri.parse(contentUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(parsedUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Direct default player launch (e.g. MX Player Pro, VLC, or System Player)
        try {
            context.startActivity(intent)
            android.util.Log.d("GalleryAction", "playVideo directly launched default video player")
            return true
        } catch (e: Exception) {
            android.util.Log.w("GalleryAction", "Direct launch failed, falling back to chooser", e)
        }

        // Fallback to system chooser if no default player is assigned
        return try {
            val chooser = Intent.createChooser(intent, "Play Video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            android.util.Log.e("GalleryAction", "playVideo failed completely", e)
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
