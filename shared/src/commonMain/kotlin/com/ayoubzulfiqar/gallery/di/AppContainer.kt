package com.ayoubzulfiqar.gallery.di

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.datasource.PlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.InMemoryKeyValueStore
import com.ayoubzulfiqar.gallery.data.local.KeyValueStore
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.data.datasource.ImportResult
import com.ayoubzulfiqar.gallery.domain.usecase.VaultTransferCoordinator
import com.ayoubzulfiqar.gallery.platform.AlwaysGrantedPermissionController
import com.ayoubzulfiqar.gallery.platform.MediaPermissionController
import com.ayoubzulfiqar.gallery.security.MetadataStripper
import com.ayoubzulfiqar.gallery.ui.common.NoOpImageLoader
import com.ayoubzulfiqar.gallery.ui.common.PlatformImageLoader
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import com.ayoubzulfiqar.gallery.vault.data.VaultConfig
import com.ayoubzulfiqar.gallery.vault.storage.DeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.storage.InMemoryVaultStorage
import com.ayoubzulfiqar.gallery.vault.storage.PassthroughDeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.storage.VaultStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Saves an edited copy of an image into the library. */
interface EditedImageExporter {
    suspend fun export(item: MediaItem, recipe: EditRecipe): ImportResult
}

/** Fire-and-forget user notifications rendered as snackbars by the app shell. */
class UserMessenger {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

/**
 * Application-scoped object graph. Lives for the whole process (Android Application / iOS app), so
 * repositories, caches and the unlocked vault session survive configuration changes.
 */
class AppContainer(
    val mediaSource: PlatformMediaDataSource,
    val permissions: MediaPermissionController,
    keyValueStore: KeyValueStore,
    vaultStorage: VaultStorage,
    deviceKeyProtector: DeviceKeyProtector,
    /** Null when the platform cannot sanitize media. */
    val metadataStripper: MetadataStripper?,
    /** Null when the platform cannot save edited images. */
    val imageExporter: EditedImageExporter?,
    val imageLoader: PlatformImageLoader,
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    vaultConfig: VaultConfig = VaultConfig()
) {
    val localStore = LocalMetadataStore(keyValueStore)
    val mediaRepository = DefaultMediaRepository(mediaSource, localStore, appScope)
    val vaultRepository = DefaultVaultRepository(
        storage = vaultStorage,
        deviceKeyProtector = deviceKeyProtector,
        config = vaultConfig,
        dispatcher = ioDispatcher
    )
    val vaultTransfers = VaultTransferCoordinator(mediaSource, mediaRepository, vaultRepository)
    val messenger = UserMessenger()

    companion object {
        /** Self-contained graph with sample media; used for previews and platforms without a media store. */
        fun demo(
            keyValueStore: KeyValueStore = InMemoryKeyValueStore(),
            vaultStorage: VaultStorage = InMemoryVaultStorage(),
            deviceKeyProtector: DeviceKeyProtector = PassthroughDeviceKeyProtector(),
            imageLoader: PlatformImageLoader = NoOpImageLoader
        ): AppContainer = AppContainer(
            mediaSource = FakePlatformMediaDataSource().apply { populateWithSyntheticMedia(count = 120) },
            permissions = AlwaysGrantedPermissionController(),
            keyValueStore = keyValueStore,
            vaultStorage = vaultStorage,
            deviceKeyProtector = deviceKeyProtector,
            metadataStripper = null,
            imageExporter = null,
            imageLoader = imageLoader
        )
    }
}
