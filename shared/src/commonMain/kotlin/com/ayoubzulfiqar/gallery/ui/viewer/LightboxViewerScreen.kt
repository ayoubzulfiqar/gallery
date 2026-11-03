package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.formatDuration
import com.ayoubzulfiqar.gallery.ui.common.AsyncMediaImage
import com.ayoubzulfiqar.gallery.ui.common.formatDateTime
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

/** Viewer actions; a null callback hides the corresponding control (platform capability). */
data class ViewerCallbacks(
    val onBack: () -> Unit,
    val onPlayVideo: (MediaItem) -> Unit,
    val onShare: (MediaItem) -> Unit,
    val onEdit: ((MediaItem) -> Unit)? = null,
    val onShareSanitized: ((MediaItem) -> Unit)? = null,
    val onStripInPlace: ((MediaItem) -> Unit)? = null,
    val supportsInPlaceStrip: (MediaItem) -> Boolean = { false },
    val onLens: ((MediaItem) -> Unit)? = null,
    val onWallpaper: ((MediaItem) -> Unit)? = null,
    val onDelete: ((MediaItem) -> Unit)? = null,
    val onMoveToVault: ((MediaItem) -> Unit)? = null,
    val onOpenMap: ((Double, Double) -> Unit)? = null
)

@Composable
fun LightboxViewerScreen(
    viewModel: ViewerViewModel,
    callbacks: ViewerCallbacks,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isChromeVisible by viewModel.isChromeVisible.collectAsState()
    val details by viewModel.details.collectAsState()
    var showMoreSheet by remember { mutableStateOf(false) }

    val canvas = if (isDark) Color.Black else Color(0xFFF1F5F9)
    val chromeColor = if (isDark) Color.White else Color(0xFF0F172A)

    // The scope emptied (last item deleted or moved): nothing left to page through.
    LaunchedEffect(uiState.isEmpty) { if (uiState.isEmpty) callbacks.onBack() }

    Box(modifier = modifier.fillMaxSize().background(canvas)) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = GalleryColors.Accent, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val items = uiState.items
        if (items.isEmpty()) return@Box

        val pagerState = rememberPagerState(initialPage = uiState.currentIndex.coerceIn(0, items.lastIndex)) { items.size }
        var isCurrentZoomed by remember { mutableStateOf(false) }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { viewModel.onPageChanged(it) }
        }
        val requestedIndex = uiState.currentIndex
        LaunchedEffect(requestedIndex, items.size) {
            val target = requestedIndex.coerceIn(0, items.lastIndex)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentZoomed,
            beyondViewportPageCount = 1,
            key = { index -> items.getOrNull(index)?.id ?: "page-$index" }
        ) { page ->
            val pageItem = items.getOrNull(page) ?: return@HorizontalPager
            ZoomableMediaBox(
                onDismissRequest = callbacks.onBack,
                onTap = viewModel::toggleChrome,
                zoomEnabled = !pageItem.isVideo,
                onZoomChanged = { zoomed -> if (page == pagerState.currentPage) isCurrentZoomed = zoomed }
            ) {
                AsyncMediaImage(
                    uri = pageItem.uri,
                    contentDescription = pageItem.displayName,
                    modifier = Modifier.fillMaxSize(),
                    isThumbnail = false,
                    contentScale = ContentScale.Fit,
                    isVideo = pageItem.isVideo,
                    mimeType = pageItem.mimeType
                )
                if (pageItem.isVideo) {
                    VideoPlayButton(pageItem) { callbacks.onPlayVideo(pageItem) }
                }
            }
        }

        val item = uiState.currentItem
        TopChrome(
            visible = isChromeVisible,
            item = item,
            position = uiState.currentIndex + 1,
            total = items.size,
            isDark = isDark,
            chromeColor = chromeColor,
            onBack = callbacks.onBack,
            onFavorite = viewModel::toggleFavorite,
            onInfo = viewModel::showDetails,
            onMore = { showMoreSheet = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        AnimatedVisibility(
            visible = isChromeVisible && item != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (item == null) return@AnimatedVisibility
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(isDark = isDark, blurRadius = 24.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionPillButton(GalleryIcons.ShareArrow, "Share", chromeColor) { callbacks.onShare(item) }
                if (item.isVideo) {
                    ActionPillButton(GalleryIcons.Play, "Play", GalleryColors.Accent) { callbacks.onPlayVideo(item) }
                } else {
                    callbacks.onEdit?.let { edit -> ActionPillButton(GalleryIcons.EditPen, "Edit", chromeColor) { edit(item) } }
                    callbacks.onLens?.let { lens -> ActionPillButton(GalleryIcons.Lens, "Lens", GalleryColors.Accent) { lens(item) } }
                }
                callbacks.onMoveToVault?.let { vault -> ActionPillButton(GalleryIcons.Vault, "Vault", chromeColor) { vault(item) } }
                callbacks.onDelete?.let { delete -> ActionPillButton(GalleryIcons.Trash, "Delete", Color(0xFFFF453A)) { delete(item) } }
            }
        }

        if (showMoreSheet && item != null) {
            ViewerMoreSheet(actions = moreActions(item, callbacks, isDark), onDismiss = { showMoreSheet = false })
        }
        if (details != DetailsState.Hidden && item != null) {
            MediaInfoSheet(item = item, state = details, onDismiss = viewModel::hideDetails, onOpenMap = callbacks.onOpenMap)
        }
    }
}

private fun moreActions(item: MediaItem, callbacks: ViewerCallbacks, isDark: Boolean): List<MoreSheetAction> = buildList {
    callbacks.onShareSanitized?.let {
        add(MoreSheetAction(GalleryIcons.ShieldCheck, Color(0xFF2EC4B6), "Share without metadata", "Removes location, camera and date details from the shared copy") { it(item) })
    }
    if (callbacks.onStripInPlace != null && callbacks.supportsInPlaceStrip(item)) {
        add(MoreSheetAction(GalleryIcons.ShieldCheck, GalleryColors.Warning, "Remove metadata from original", "Permanently strips location and camera details from this file") { callbacks.onStripInPlace.invoke(item) })
    }
    if (!item.isVideo) {
        callbacks.onWallpaper?.let { add(MoreSheetAction(GalleryIcons.Wallpaper, Color(0xFF80D4FF), "Set as wallpaper", "Home screen, lock screen or both") { it(item) }) }
        callbacks.onLens?.let { add(MoreSheetAction(GalleryIcons.Lens, GalleryColors.Accent, "Search with Google Lens", "Identify objects, text and places") { it(item) }) }
    }
    callbacks.onMoveToVault?.let { add(MoreSheetAction(GalleryIcons.Vault, Color(0xFFE71D36), "Move to vault", "Encrypt and hide from your gallery") { it(item) }) }
}

@Composable
private fun TopChrome(
    visible: Boolean,
    item: MediaItem?,
    position: Int,
    total: Int,
    isDark: Boolean,
    chromeColor: Color,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(isDark = isDark, blurRadius = 24.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(GalleryIcons.ChevronBack, contentDescription = "Back", tint = chromeColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = item?.let { formatDateTime(it.dateTakenMillis) } ?: "",
                    color = chromeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("$position of $total", color = chromeColor.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            val isFavorite = item?.isFavorite == true
            IconButton(onClick = onFavorite, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (isFavorite) GalleryIcons.HeartFilled else GalleryIcons.HeartOutline,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) GalleryColors.Favorite else chromeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(44.dp)) {
                Icon(GalleryIcons.InfoCircle, contentDescription = "Details", tint = chromeColor, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMore, modifier = Modifier.size(44.dp)) {
                Icon(GalleryIcons.MoreHorizontal, contentDescription = "More options", tint = chromeColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun VideoPlayButton(item: MediaItem, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(role = Role.Button, onClickLabel = "Play video", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(GalleryIcons.Play, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        item.durationMs?.takeIf { it > 0 }?.let {
            Text(
                text = formatDuration(it),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ActionPillButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
    }
}
