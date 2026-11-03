package com.ayoubzulfiqar.gallery.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Bridges app-scoped code (data source, sanitizer) to the system consent dialogs that MediaStore
 * requires on Android 10+ for deleting, trashing or writing media the app does not own.
 *
 * The broker outlives activities; each activity attaches its own launcher. Requests are serialized
 * and a request pending while the activity finishes resolves as "declined" instead of hanging.
 */
class MediaStoreConsentBroker {
    private val mutex = Mutex()
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            pending?.complete(result.resultCode == Activity.RESULT_OK)
            pending = null
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (activity.isFinishing) {
                    pending?.complete(false)
                    pending = null
                }
                launcher = null
            }
        })
    }

    /** Shows the system consent UI. Returns true only if the user approved. */
    suspend fun requestConsent(intentSender: IntentSender): Boolean = mutex.withLock {
        withContext(Dispatchers.Main) {
            val activeLauncher = launcher ?: return@withContext false
            val deferred = CompletableDeferred<Boolean>()
            pending = deferred
            try {
                activeLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (_: Exception) {
                pending = null
                return@withContext false
            }
            deferred.await()
        }
    }
}

/**
 * Runtime media permissions across API levels, including Android 14+ partial ("selected photos")
 * access and reliable detection of "don't ask again" (two denials on Android 11+).
 */
class AndroidMediaPermissionController(
    private val context: Context,
    private val prefs: SharedPreferences
) : MediaPermissionController {

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var activityRef: WeakReference<Activity>? = null
    private val _access = MutableStateFlow(compute())
    override val access: StateFlow<MediaAccess> = _access.asStateFlow()

    fun attach(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            prefs.edit().putInt(KEY_REQUEST_COUNT, prefs.getInt(KEY_REQUEST_COUNT, 0) + 1).apply()
            refresh()
        }
        refresh()
    }

    override fun refresh() {
        _access.value = compute()
    }

    override fun request() {
        launcher?.launch(requestedPermissions())
    }

    fun hasLocationAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_MEDIA_LOCATION)

    fun canWriteLegacyStorage(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private fun compute(): MediaAccess {
        val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (full) return MediaAccess.FULL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            return MediaAccess.PARTIAL
        }
        val requests = prefs.getInt(KEY_REQUEST_COUNT, 0)
        if (requests == 0) return MediaAccess.UNKNOWN
        val activity = activityRef?.get()
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(primary) ?: true
        return if (requests >= 2 && !canAskAgain) MediaAccess.DENIED_PERMANENTLY else MediaAccess.DENIED
    }

    private fun requestedPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val KEY_REQUEST_COUNT = "media_permission_request_count"
    }
}
