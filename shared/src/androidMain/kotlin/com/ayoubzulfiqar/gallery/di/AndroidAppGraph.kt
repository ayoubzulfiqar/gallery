package com.ayoubzulfiqar.gallery.di

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import com.ayoubzulfiqar.gallery.data.datasource.AndroidMediaStoreDataSource
import com.ayoubzulfiqar.gallery.editor.AndroidEditedImageExporter
import com.ayoubzulfiqar.gallery.platform.AndroidMediaPermissionController
import com.ayoubzulfiqar.gallery.platform.MediaStoreConsentBroker
import com.ayoubzulfiqar.gallery.security.AndroidMetadataStripper
import com.ayoubzulfiqar.gallery.ui.common.AndroidImageLoader
import com.ayoubzulfiqar.gallery.vault.FileVaultStorage
import com.ayoubzulfiqar.gallery.vault.KeystoreDeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.SharedPreferencesKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Process-wide Android object graph. Create once in [Application.onCreate]. */
class AndroidAppGraph(application: Application) {
    private val appContext: Context = application.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = appContext.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)

    val consentBroker = MediaStoreConsentBroker()
    val permissionController = AndroidMediaPermissionController(appContext, prefs)
    private val mediaSource = AndroidMediaStoreDataSource(appContext, consentBroker, permissionController)
    val metadataStripper = AndroidMetadataStripper(appContext, consentBroker, permissionController)

    val container = AppContainer(
        mediaSource = mediaSource,
        permissions = permissionController,
        keyValueStore = SharedPreferencesKeyValueStore(prefs),
        vaultStorage = FileVaultStorage(File(appContext.noBackupFilesDir, "vault")),
        deviceKeyProtector = KeystoreDeviceKeyProtector(),
        metadataStripper = metadataStripper,
        imageExporter = AndroidEditedImageExporter(appContext, mediaSource),
        imageLoader = AndroidImageLoader(appContext),
        appScope = appScope,
        ioDispatcher = Dispatchers.IO
    )

    init {
        // Sanitized share copies older than a day are never needed again.
        appScope.launch(Dispatchers.IO) { metadataStripper.purgeOldShareFiles(maxAgeMillis = 24 * 60 * 60 * 1000L) }
    }

    /** Registers activity-result launchers; call from Activity.onCreate before it is started. */
    fun attach(activity: ComponentActivity) {
        consentBroker.attach(activity)
        permissionController.attach(activity)
    }
}
