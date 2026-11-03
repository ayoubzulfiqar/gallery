package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.ui.common.AsyncMediaImage
import com.ayoubzulfiqar.gallery.ui.common.ChoiceChipRow
import com.ayoubzulfiqar.gallery.ui.common.EmptyState
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.ThemeMode

/** Callbacks for actions that need the app shell (dialogs, platform intents, navigation). */
data class MediaSelectionHandlers(
    val onShare: ((List<MediaItem>) -> Unit)?,
    val onDelete: ((List<MediaItem>) -> Unit)?,
    val onMoveToVault: ((List<MediaItem>) -> Unit)?
)

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onOpenItem: (MediaItem, MediaFilter) -> Unit,
    onFolderClick: (bucketId: String, folderName: String) -> Unit,
    handlers: MediaSelectionHandlers,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val density by viewModel.density.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val quickFolders by viewModel.quickFolders.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode = selectedIds.isNotEmpty()

    GalleryBackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TimelineHeader(
                totalCount = uiState.totalCount,
                density = density,
                themeMode = themeMode,
                onDensityChange = viewModel::setDensity,
                onToggleTheme = onToggleTheme
            )
            banner?.invoke()
            ChoiceChipRow(
                options = MediaFilter.entries,
                selected = filter,
                label = { it.label },
                onSelect = viewModel::setFilter
            )

            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GalleryColors.Accent)
                }
                uiState.libraryIsEmpty -> EmptyState(
                    icon = GalleryIcons.Photos,
                    title = "No photos or videos yet",
                    message = "Pictures you take or download will appear here."
                )
                uiState.items.isEmpty() -> EmptyState(
                    icon = if (filter == MediaFilter.FAVORITES) GalleryIcons.HeartOutline else GalleryIcons.Photos,
                    title = when (filter) {
                        MediaFilter.FAVORITES -> "No favorites yet"
                        MediaFilter.VIDEOS -> "No videos"
                        else -> "No photos"
                    },
                    message = if (filter == MediaFilter.FAVORITES) "Tap the heart on any photo to keep it here." else "Try another filter.",
                    actionLabel = "Show all",
                    onAction = { viewModel.setFilter(MediaFilter.ALL) }
                )
                else -> MediaGrid(
                    groups = uiState.groups,
                    density = density,
                    selectedIds = selectedIds,
                    onItemClick = { item ->
                        if (isSelectionMode) viewModel.toggleSelection(item.id) else onOpenItem(item, filter)
                    },
                    onItemLongClick = { item -> viewModel.toggleSelection(item.id) },
                    onGroupToggle = viewModel::toggleGroupSelection,
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = navBarBottom + 96.dp),
                    modifier = Modifier.pinchToChangeDensity(viewModel::stepDensity),
                    header = if (quickFolders.isNotEmpty() && filter == MediaFilter.ALL && !isSelectionMode) {
                        { QuickFolderRow(quickFolders, onFolderClick) }
                    } else {
                        null
                    }
                )
            }
        }

        val selectedItems = if (isSelectionMode) uiState.items.filter { it.id in selectedIds } else emptyList()
        MarqueeSelectionBar(
            selectedCount = selectedIds.size,
            totalCount = uiState.totalCount,
            visible = isSelectionMode,
            allFavorite = selectedItems.isNotEmpty() && selectedItems.all { it.isFavorite },
            actions = SelectionActions(
                onShare = handlers.onShare?.let { share -> { share(viewModel.selectedItems()) } },
                onFavorite = { viewModel.toggleFavoriteForSelection() },
                onMoveToVault = handlers.onMoveToVault?.let { move ->
                    {
                        move(viewModel.selectedItems())
                        viewModel.clearSelection()
                    }
                },
                onDelete = handlers.onDelete?.let { delete ->
                    {
                        delete(viewModel.selectedItems())
                        viewModel.clearSelection()
                    }
                },
                onSelectAll = viewModel::selectAll,
                onClose = viewModel::clearSelection
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun TimelineHeader(
    totalCount: Int,
    density: TimelineDensity,
    themeMode: ThemeMode,
    onDensityChange: (TimelineDensity) -> Unit,
    onToggleTheme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Photos",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (totalCount > 0) {
                Text(
                    text = if (totalCount == 1) "1 item" else "$totalCount items",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
        }

        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
        ) {
            Icon(
                imageVector = when (themeMode) {
                    ThemeMode.LIGHT -> GalleryIcons.Sun
                    ThemeMode.DARK -> GalleryIcons.Moon
                    ThemeMode.SYSTEM -> GalleryIcons.AutoTheme
                },
                contentDescription = "Theme: ${themeMode.name.lowercase()}. Tap to change.",
                tint = when (themeMode) {
                    ThemeMode.LIGHT -> Color(0xFFFFB703)
                    ThemeMode.DARK -> Color(0xFF80D4FF)
                    ThemeMode.SYSTEM -> MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.size(19.dp)
            )
        }

        Row(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TimelineDensity.entries.forEach { d ->
                val isSelected = density == d
                val bgColor by animateColorAsState(if (isSelected) GalleryColors.Accent else Color.Transparent)
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable(role = Role.RadioButton) { onDensityChange(d) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = d.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFolderRow(folders: List<MediaFolder>, onFolderClick: (String, String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(folders, key = { it.id }) { folder ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onFolderClick(folder.id, folder.name) }
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B222C))
                ) {
                    val cover = folder.items.first()
                    AsyncMediaImage(
                        uri = cover.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        isThumbnail = true,
                        contentScale = ContentScale.Crop,
                        isVideo = cover.isVideo,
                        mimeType = cover.mimeType
                    )
                }
                Text(
                    text = folder.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
