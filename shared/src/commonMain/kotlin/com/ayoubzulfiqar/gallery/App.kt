package com.ayoubzulfiqar.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.di.AppContainer
import com.ayoubzulfiqar.gallery.domain.model.LibraryState
import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaScope
import com.ayoubzulfiqar.gallery.platform.ActionResult
import com.ayoubzulfiqar.gallery.platform.MediaAccess
import com.ayoubzulfiqar.gallery.platform.NoOpPlatformActionHandler
import com.ayoubzulfiqar.gallery.platform.PlatformActionHandler
import com.ayoubzulfiqar.gallery.security.SanitizationResult
import com.ayoubzulfiqar.gallery.ui.albums.AlbumsScreen
import com.ayoubzulfiqar.gallery.ui.albums.AlbumsViewModel
import com.ayoubzulfiqar.gallery.ui.albums.FolderDetailScreen
import com.ayoubzulfiqar.gallery.ui.albums.FolderDetailViewModel
import com.ayoubzulfiqar.gallery.ui.common.ConfirmDialog
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.common.LocalImageLoader
import com.ayoubzulfiqar.gallery.ui.editor.PhotoEditorScreen
import com.ayoubzulfiqar.gallery.ui.editor.PhotoEditorViewModel
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryDestination
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryNavState
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryTab
import com.ayoubzulfiqar.gallery.ui.navigation.NavigationHolderViewModel
import com.ayoubzulfiqar.gallery.ui.permission.PartialAccessBanner
import com.ayoubzulfiqar.gallery.ui.permission.PermissionScreen
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.GalleryTheme
import com.ayoubzulfiqar.gallery.ui.theme.ThemeMode
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass
import com.ayoubzulfiqar.gallery.ui.timeline.MediaSelectionHandlers
import com.ayoubzulfiqar.gallery.ui.timeline.TimelineScreen
import com.ayoubzulfiqar.gallery.ui.timeline.TimelineViewModel
import com.ayoubzulfiqar.gallery.ui.vault.VaultScreen
import com.ayoubzulfiqar.gallery.ui.vault.VaultViewModel
import com.ayoubzulfiqar.gallery.ui.viewer.LightboxViewerScreen
import com.ayoubzulfiqar.gallery.ui.viewer.ViewerCallbacks
import com.ayoubzulfiqar.gallery.ui.viewer.ViewerViewModel
import kotlinx.coroutines.launch

/**
 * App shell. [container] is process-scoped; [actions] is bound to the current UI host.
 * [onSystemBarsDark] lets the host colour status/navigation bar icons for the visible screen.
 */
@Composable
fun App(
    container: AppContainer = remember { AppContainer.demo() },
    actions: PlatformActionHandler = remember { NoOpPlatformActionHandler() },
    onSystemBarsDark: (Boolean) -> Unit = {},
    onSensitiveContentVisible: (Boolean) -> Unit = {}
) {
    val themeMode by container.localStore.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val hostOwner = rememberHostViewModelStoreOwner()
    CompositionLocalProvider(
        LocalImageLoader provides container.imageLoader,
        LocalViewModelStoreOwner provides hostOwner
    ) {
        GalleryTheme(themeMode = themeMode) {
            GalleryShell(container, actions, hostOwner, themeMode, isDark, onSystemBarsDark, onSensitiveContentVisible)
        }
    }
}

@Composable
private fun GalleryShell(
    container: AppContainer,
    actions: PlatformActionHandler,
    hostOwner: ViewModelStoreOwner,
    themeMode: ThemeMode,
    isDark: Boolean,
    onSystemBarsDark: (Boolean) -> Unit,
    onSensitiveContentVisible: (Boolean) -> Unit
) {
    val navState = viewModel(hostOwner) { NavigationHolderViewModel() }.navState
    val timelineViewModel = viewModel(hostOwner) { TimelineViewModel(container.mediaRepository, container.localStore) }
    val albumsViewModel = viewModel(hostOwner) { AlbumsViewModel(container.mediaRepository) }
    val vaultViewModel = viewModel(hostOwner) { VaultViewModel(container.vaultRepository, container.vaultTransfers, container.messenger) }

    val access by container.permissions.access.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val capabilities = container.mediaRepository.capabilities
    val messenger = container.messenger
    val appScope = container.appScope

    var pendingDelete by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pendingStrip by remember { mutableStateOf<MediaItem?>(null) }

    // First launch: ask for library access right away instead of showing an empty timeline.
    LaunchedEffect(access) { if (access == MediaAccess.UNKNOWN) container.permissions.request() }

    LaunchedEffect(messenger) {
        messenger.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Foreground: pick up permission/library changes. Background: lock the vault.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    container.permissions.refresh()
                    container.mediaRepository.refresh()
                }
                Lifecycle.Event.ON_STOP -> vaultViewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun report(result: ActionResult) {
        when (result) {
            is ActionResult.Done -> result.message?.let(messenger::show)
            is ActionResult.Failed -> messenger.show(result.message)
            ActionResult.Cancelled -> Unit
        }
    }

    fun performDelete(items: List<MediaItem>) {
        appScope.launch {
            val result = container.mediaRepository.deleteMedia(items, DeleteMode.TRASH)
            val deleted = result.deletedIds.size
            when {
                result.cancelled -> Unit
                deleted == items.size -> messenger.show(
                    (if (capabilities.deleteMovesToTrash) "Moved to trash" else "Deleted") +
                        if (deleted == 1) "" else " ($deleted items)"
                )
                deleted > 0 -> messenger.show("Deleted $deleted of ${items.size}. ${result.errorMessage ?: ""}".trim())
                else -> messenger.show(result.errorMessage ?: "Couldn't delete")
            }
        }
    }

    fun requestDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        // Android 11+ shows its own consent dialog; asking twice would be noise.
        if (capabilities.systemConfirmsDeletion) performDelete(items) else pendingDelete = items
    }

    fun requestMoveToVault(items: List<MediaItem>) {
        if (items.isEmpty()) return
        vaultViewModel.queueMove(items)
        navState.switchTab(GalleryTab.VAULT)
    }

    fun shareSanitized(items: List<MediaItem>) {
        appScope.launch { report(actions.shareSanitized(items)) }
    }

    val stripper = container.metadataStripper
    val selectionHandlers = MediaSelectionHandlers(
        onShare = { items -> report(actions.share(items)) },
        onDelete = if (capabilities.canDelete) ::requestDelete else null,
        onMoveToVault = if (capabilities.canReadContent && capabilities.canDelete) ::requestMoveToVault else null
    )

    // Registered before any screen so screen-level handlers (selection, sheets) take precedence.
    GalleryBackHandler(enabled = navState.canNavigateBack) { navState.navigateBack() }

    val destination = navState.currentDestination
    LaunchedEffect(isDark, destination) {
        onSystemBarsDark(isDark || destination is GalleryDestination.Editor)
    }
    // Keep vault content out of screenshots, screen recordings and the recent-apps preview.
    val vaultUi by vaultViewModel.uiState.collectAsState()
    val vaultContentVisible = destination == GalleryDestination.Vault && vaultUi.vaultState is com.ayoubzulfiqar.gallery.domain.model.VaultState.Unlocked
    LaunchedEffect(vaultContentVisible) { onSensitiveContentVisible(vaultContentVisible) }

    val canvas = if (isDark) GalleryColors.DarkCanvas else GalleryColors.LightCanvas
    Box(modifier = Modifier.fillMaxSize().background(canvas)) {
        val saveableStateHolder = rememberSaveableStateHolder()
        val entry = navState.currentEntry
        SideEffect { navState.drainRemovedKeys().forEach(saveableStateHolder::removeState) }

        val needsLibraryAccess = destination !is GalleryDestination.Vault
        val hasAccess = access == MediaAccess.FULL || access == MediaAccess.PARTIAL

        saveableStateHolder.SaveableStateProvider(entry.key) {
            // Tab roots use the host store (state survives tab switches); pushed screens own theirs.
            val owner = if (navState.isAtTabRoot) hostOwner else entry
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                if (needsLibraryAccess && !hasAccess && access != MediaAccess.UNKNOWN) {
                    PermissionScreen(
                        access = access,
                        onRequest = container.permissions::request,
                        onOpenSettings = { report(actions.openAppSettings()) }
                    )
                } else when (destination) {
                    GalleryDestination.Timeline -> TimelineScreen(
                        viewModel = timelineViewModel,
                        onOpenItem = { item, filter -> openItem(navState, actions, ::report, item, MediaScope.Library(filter)) },
                        onFolderClick = { id, name -> navState.navigateTo(GalleryDestination.FolderDetail(id, name)) },
                        handlers = selectionHandlers,
                        themeMode = themeMode,
                        onToggleTheme = container.localStore::cycleThemeMode,
                        banner = if (access == MediaAccess.PARTIAL) {
                            { PartialAccessBanner(onManage = container.permissions::request) }
                        } else {
                            null
                        }
                    )

                    GalleryDestination.Albums -> AlbumsScreen(
                        viewModel = albumsViewModel,
                        onFolderClick = { id, name -> navState.navigateTo(GalleryDestination.FolderDetail(id, name)) }
                    )

                    GalleryDestination.Vault -> VaultScreen(viewModel = vaultViewModel)

                    is GalleryDestination.FolderDetail -> {
                        val folderViewModel = viewModel { FolderDetailViewModel(container.mediaRepository, destination.bucketId, destination.folderName) }
                        FolderDetailScreen(
                            viewModel = folderViewModel,
                            onBack = { navState.navigateBack() },
                            onOpenItem = { item, filter ->
                                openItem(navState, actions, ::report, item, MediaScope.Folder(destination.bucketId, filter))
                            },
                            handlers = selectionHandlers
                        )
                    }

                    is GalleryDestination.Viewer -> {
                        val viewerViewModel = viewModel { ViewerViewModel(container.mediaRepository, destination.mediaId, destination.scope) }
                        LightboxViewerScreen(
                            viewModel = viewerViewModel,
                            isDark = isDark,
                            callbacks = ViewerCallbacks(
                                onBack = { navState.navigateBack() },
                                onPlayVideo = { report(actions.playVideo(it)) },
                                onShare = { report(actions.share(listOf(it))) },
                                onEdit = if (container.imageExporter != null) {
                                    { item -> navState.navigateTo(GalleryDestination.Editor(item.id)) }
                                } else {
                                    null
                                },
                                onShareSanitized = if (stripper != null) ({ item -> shareSanitized(listOf(item)) }) else null,
                                onStripInPlace = if (stripper != null) ({ item -> pendingStrip = item }) else null,
                                supportsInPlaceStrip = { item -> stripper?.supportsInPlace(item) == true },
                                onLens = { report(actions.launchGoogleLens(it)) },
                                onWallpaper = { report(actions.setAsWallpaper(it)) },
                                onDelete = if (capabilities.canDelete) ({ item -> requestDelete(listOf(item)) }) else null,
                                onMoveToVault = selectionHandlers.onMoveToVault?.let { move -> { item -> move(listOf(item)) } },
                                onOpenMap = { lat, lon -> report(actions.openMap(lat, lon, "Photo location")) }
                            )
                        )
                    }

                    is GalleryDestination.Editor -> EditorRoute(container, destination.mediaId, onClose = { navState.navigateBack() })
                }
            }
        }

        val timelineSelection by timelineViewModel.selectedIds.collectAsState()
        val vaultState = vaultUi
        val showNav = navState.isAtTabRoot &&
            !(destination == GalleryDestination.Timeline && timelineSelection.isNotEmpty()) &&
            !(destination == GalleryDestination.Vault && (vaultState.isSelectionMode || vaultState.showsPinPad || vaultState.previewItemId != null))

        AnimatedVisibility(
            visible = showNav,
            enter = slideInVertically(initialOffsetY = { it * 2 }),
            exit = slideOutVertically(targetOffsetY = { it * 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp, start = 24.dp, end = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(isDark = isDark, blurRadius = 24.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GalleryTab.entries.forEach { tab ->
                    NavPill(
                        label = tab.label,
                        icon = when (tab) {
                            GalleryTab.PHOTOS -> GalleryIcons.Photos
                            GalleryTab.ALBUMS -> GalleryIcons.Albums
                            GalleryTab.VAULT -> GalleryIcons.Vault
                        },
                        isSelected = navState.currentTab == tab,
                        isDark = isDark,
                        onClick = { navState.switchTab(tab) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 84.dp, start = 12.dp, end = 12.dp)
        )
    }

    pendingDelete?.let { items ->
        ConfirmDialog(
            title = if (items.size == 1) "Delete this item?" else "Delete ${items.size} items?",
            message = if (capabilities.deleteMovesToTrash) "Items are moved to the system trash." else "This permanently removes them from your device.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                performDelete(items)
            },
            onDismiss = { pendingDelete = null }
        )
    }

    pendingStrip?.let { item ->
        ConfirmDialog(
            title = "Remove metadata from the original?",
            message = "Location, camera details, dates and embedded data (such as motion-photo video) will be permanently removed from \"${item.displayName}\". The picture itself is not changed.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                pendingStrip = null
                appScope.launch {
                    when (val result = stripper?.stripInPlace(item)) {
                        is SanitizationResult.Success -> messenger.show(result.summary.describe())
                        is SanitizationResult.Failure -> messenger.show(result.message)
                        SanitizationResult.Cancelled, null -> Unit
                    }
                }
            },
            onDismiss = { pendingStrip = null }
        )
    }
}

private fun openItem(
    navState: GalleryNavState,
    actions: PlatformActionHandler,
    report: (ActionResult) -> Unit,
    item: MediaItem,
    scope: MediaScope
) {
    // Videos play straight in the user's default player; photos open the viewer.
    if (item.isVideo) report(actions.playVideo(item)) else navState.navigateTo(GalleryDestination.Viewer(item.id, scope))
}

@Composable
private fun EditorRoute(container: AppContainer, mediaId: String, onClose: () -> Unit) {
    val library by container.mediaRepository.library.collectAsState(LibraryState())
    val item = library.items.firstOrNull { it.id == mediaId }
    // Capture plain values: reading the delegated `library` inside the effect would see a newer
    // state than the captured `item` and close the editor before it ever appeared.
    val isLoaded = library.isLoaded
    val isMissing = item == null
    LaunchedEffect(isLoaded, isMissing) {
        if (isLoaded && isMissing) {
            container.messenger.show("This photo is no longer available")
            onClose()
        }
    }
    if (item == null) return
    val editorViewModel = viewModel { PhotoEditorViewModel(item, container.imageExporter) }
    PhotoEditorScreen(viewModel = editorViewModel, onClose = onClose, onMessage = container.messenger::show)
}

/** Uses the platform's owner (Activity / UIViewController) when present, otherwise a local one (previews). */
@Composable
private fun rememberHostViewModelStoreOwner(): ViewModelStoreOwner {
    val platformOwner = LocalViewModelStoreOwner.current
    if (platformOwner != null) return platformOwner
    val fallback = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(fallback) { onDispose { fallback.viewModelStore.clear() } }
    return fallback
}

@Composable
private fun NavPill(label: String, icon: ImageVector, isSelected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val activeBg by animateColorAsState(
        if (isSelected) GalleryColors.Accent.copy(alpha = if (isDark) 0.16f else 0.12f) else Color.Transparent
    )
    val tint by animateColorAsState(
        when {
            isSelected -> GalleryColors.Accent
            isDark -> Color.White.copy(alpha = 0.6f)
            else -> Color(0xFF64748B)
        }
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(activeBg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = tint)
    }
}
