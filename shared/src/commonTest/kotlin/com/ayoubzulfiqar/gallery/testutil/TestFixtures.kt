package com.ayoubzulfiqar.gallery.testutil

import com.ayoubzulfiqar.gallery.core.crypto.PureAesGcm
import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.InMemoryKeyValueStore
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import com.ayoubzulfiqar.gallery.vault.data.VaultConfig
import com.ayoubzulfiqar.gallery.vault.storage.InMemoryVaultStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

fun media(
    id: String,
    takenMillis: Long = 1_000_000L,
    mime: String = "image/jpeg",
    bucket: String = "Camera",
    width: Int = 4000,
    height: Int = 3000,
    orientation: Int = 0
) = MediaItem(
    id = id,
    uri = "demo://media/$id",
    displayName = "$id.${if (mime.startsWith("video")) "mp4" else "jpg"}",
    mimeType = mime,
    mediaType = MediaType.fromMimeType(mime),
    dateTakenMillis = takenMillis,
    dateModifiedMillis = takenMillis,
    width = width,
    height = height,
    sizeBytes = 2048,
    orientation = orientation,
    bucketId = "bucket_${bucket.lowercase()}",
    bucketName = bucket,
    relativePath = "DCIM/$bucket/"
)

class ViewModelTracker {
    private val tracked = mutableListOf<ViewModel>()
    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { tracked += it }
    internal fun cancelAll() = tracked.forEach { it.viewModelScope.cancel() }
}

/**
 * Runs a test with Dispatchers.Main bound to the test scheduler so viewModelScope works, and cancels
 * every tracked view model's scope before Main is reset (no work may outlive the test).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(block: suspend TestScope.(ViewModelTracker) -> Unit) = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    val tracker = ViewModelTracker()
    try {
        block(tracker)
    } finally {
        tracker.cancelAll()
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestGraph(scope: TestScope, items: List<MediaItem>) {
    val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
    val source = FakePlatformMediaDataSource().apply { setItems(items) }
    val kv = InMemoryKeyValueStore()
    val store = LocalMetadataStore(kv)
    val repository = DefaultMediaRepository(source, store, scope.backgroundScope, dispatcher)
    val vaultStorage = InMemoryVaultStorage()
    val vault = DefaultVaultRepository(
        storage = vaultStorage,
        aead = PureAesGcm(),
        config = VaultConfig(pbkdf2Iterations = 10, freeAttempts = 3, segmentSize = 256),
        clock = { 5_000_000L },
        dispatcher = dispatcher
    )
}
