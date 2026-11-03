package com.ayoubzulfiqar.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.datasource.PlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.ui.editor.PhotoEditorScreen
import com.ayoubzulfiqar.gallery.ui.editor.PhotoEditorViewModel
import com.ayoubzulfiqar.gallery.ui.exif.ExifInspectorSheet
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryDestination
import com.ayoubzulfiqar.gallery.ui.navigation.rememberGalleryNavState
import com.ayoubzulfiqar.gallery.ui.theme.GalleryTheme
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass
import com.ayoubzulfiqar.gallery.ui.timeline.TimelineScreen
import com.ayoubzulfiqar.gallery.ui.timeline.TimelineViewModel
import com.ayoubzulfiqar.gallery.ui.vault.VaultScreen
import com.ayoubzulfiqar.gallery.ui.vault.VaultViewModel
import com.ayoubzulfiqar.gallery.ui.viewer.LightboxViewerScreen
import com.ayoubzulfiqar.gallery.ui.viewer.ViewerViewModel
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository

@Composable
fun App(
    platformDataSource: PlatformMediaDataSource? = null
) {
    GalleryTheme(darkTheme = true) {
        val navState = rememberGalleryNavState()

        // Shared data repositories
        val activePlatformSource = remember(platformDataSource) {
            platformDataSource ?: FakePlatformMediaDataSource().apply {
                populateWithSyntheticMedia(count = 120)
            }
        }
        val localStore = remember { LocalMetadataStore() }
        val mediaRepository = remember(activePlatformSource) {
            DefaultMediaRepository(activePlatformSource, localStore)
        }
        val vaultRepository = remember { DefaultVaultRepository() }

        val timelineViewModel = remember(mediaRepository) { TimelineViewModel(mediaRepository) }
        val vaultViewModel = remember(vaultRepository) { VaultViewModel(vaultRepository) }

        var activeExifItem by remember { mutableStateOf<ExifMetadata?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F12))
        ) {
            // Main Content Area based on Destination
            when (val dest = navState.currentDestination) {
                is GalleryDestination.Timeline -> {
                    TimelineScreen(
                        viewModel = timelineViewModel,
                        onMediaClick = { id ->
                            navState.navigateTo(GalleryDestination.Viewer(id))
                        }
                    )
                }

                is GalleryDestination.Viewer -> {
                    val viewerViewModel = remember(dest.mediaId) {
                        ViewerViewModel(mediaRepository, initialItemId = dest.mediaId)
                    }
                    LightboxViewerScreen(
                        viewModel = viewerViewModel,
                        onBack = { navState.navigateBack() },
                        onEdit = { id -> navState.navigateTo(GalleryDestination.Editor(id)) },
                        onShowExif = {
                            activeExifItem = ExifMetadata(
                                make = "Google",
                                model = "Pixel 9 Pro",
                                lensModel = "24mm f/1.68 Dual-Pixel",
                                iso = 64,
                                aperture = 1.68,
                                shutterSpeed = "1/200s",
                                focalLength = 6.9,
                                address = "San Francisco, CA"
                            )
                        }
                    )
                }

                is GalleryDestination.Editor -> {
                    val currentItem = viewerViewModelItem(mediaRepository, dest.mediaId)
                    if (currentItem != null) {
                        val editorViewModel = remember(dest.mediaId) {
                            PhotoEditorViewModel(currentItem)
                        }
                        PhotoEditorScreen(
                            viewModel = editorViewModel,
                            onSave = { navState.navigateBack() },
                            onCancel = { navState.navigateBack() }
                        )
                    }
                }

                is GalleryDestination.Vault -> {
                    VaultScreen(
                        viewModel = vaultViewModel,
                        onBack = { navState.navigateBack() }
                    )
                }

                is GalleryDestination.Albums -> {
                    TimelineScreen(
                        viewModel = timelineViewModel,
                        onMediaClick = { id -> navState.navigateTo(GalleryDestination.Viewer(id)) }
                    )
                }

                is GalleryDestination.Explore -> {
                    TimelineScreen(
                        viewModel = timelineViewModel,
                        onMediaClick = { id -> navState.navigateTo(GalleryDestination.Viewer(id)) }
                    )
                }
            }

            // EXIF Bottom Sheet Modal
            if (activeExifItem != null) {
                ExifInspectorSheet(
                    exif = activeExifItem,
                    onClose = { activeExifItem = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Floating Frosted Glass Bottom Navigation Bar (Visible on root tabs)
            val isRootTab = navState.currentDestination is GalleryDestination.Timeline ||
                    navState.currentDestination is GalleryDestination.Albums ||
                    navState.currentDestination is GalleryDestination.Vault ||
                    navState.currentDestination is GalleryDestination.Explore

            AnimatedVisibility(
                visible = isRootTab && activeExifItem == null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 32.dp, end = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(blurRadius = 24.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavPill(
                        label = "Photos",
                        icon = "\uD83D\uDDBC",
                        isSelected = navState.currentDestination is GalleryDestination.Timeline,
                        onClick = { navState.navigateTo(GalleryDestination.Timeline) }
                    )
                    NavPill(
                        label = "Albums",
                        icon = "\uD83D\uDCC1",
                        isSelected = navState.currentDestination is GalleryDestination.Albums,
                        onClick = { navState.navigateTo(GalleryDestination.Albums) }
                    )
                    NavPill(
                        label = "Vault",
                        icon = "\uD83D\uDD12",
                        isSelected = navState.currentDestination is GalleryDestination.Vault,
                        onClick = { navState.navigateTo(GalleryDestination.Vault) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isSelected) Color(0xFF00B4D8) else Color.LightGray
        )
    ) {
        Text(
            text = "$icon $label",
            fontSize = 13.sp,
            color = if (isSelected) Color(0xFF00B4D8) else Color.LightGray
        )
    }
}

private fun viewerViewModelItem(repo: DefaultMediaRepository, id: String): MediaItem? {
    return MediaItem(
        id = id,
        uri = "file://sample.jpg",
        displayName = "IMG_20261103.jpg",
        mimeType = "image/jpeg",
        mediaType = com.ayoubzulfiqar.gallery.domain.model.MediaType.IMAGE,
        dateTakenMillis = 1730000000000L,
        dateModifiedMillis = 1730000000000L,
        width = 4032,
        height = 3024,
        sizeBytes = 4200000L
    )
}
