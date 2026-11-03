package com.ayoubzulfiqar.gallery.ui.albums

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.ui.common.ChoiceChipRow
import com.ayoubzulfiqar.gallery.ui.common.EmptyState
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.timeline.MarqueeSelectionBar
import com.ayoubzulfiqar.gallery.ui.timeline.MediaGrid
import com.ayoubzulfiqar.gallery.ui.timeline.MediaSelectionHandlers
import com.ayoubzulfiqar.gallery.ui.timeline.SelectionActions

@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onBack: () -> Unit,
    onOpenItem: (MediaItem, MediaFilter) -> Unit,
    handlers: MediaSelectionHandlers,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode = selectedIds.isNotEmpty()

    GalleryBackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    // Everything in the folder was deleted or moved: there is nothing left to show.
    LaunchedEffect(uiState.isGone) { if (uiState.isGone) onBack() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = GalleryIcons.ChevronBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = uiState.folderName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (filter == MediaFilter.ALL) "${uiState.folderItemCount} items" else "${uiState.items.size} of ${uiState.folderItemCount} items",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }

            ChoiceChipRow(
                options = MediaFilter.entries,
                selected = filter,
                label = { it.label },
                onSelect = viewModel::setFilter
            )

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GalleryColors.Accent)
                }
                uiState.items.isEmpty() -> EmptyState(
                    icon = GalleryIcons.Photos,
                    title = "Nothing here",
                    message = "No ${filter.label.lowercase()} in this folder.",
                    actionLabel = "Show all",
                    onAction = { viewModel.setFilter(MediaFilter.ALL) }
                )
                else -> MediaGrid(
                    groups = uiState.groups,
                    density = TimelineDensity.DAYS,
                    selectedIds = selectedIds,
                    onItemClick = { item ->
                        if (isSelectionMode) viewModel.toggleSelection(item.id) else onOpenItem(item, filter)
                    },
                    onItemLongClick = { viewModel.toggleSelection(it.id) },
                    onGroupToggle = viewModel::toggleGroupSelection,
                    contentPadding = PaddingValues(
                        start = 3.dp,
                        end = 3.dp,
                        top = 6.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
                    )
                )
            }
        }

        val selectedItems = if (isSelectionMode) uiState.items.filter { it.id in selectedIds } else emptyList()
        MarqueeSelectionBar(
            selectedCount = selectedIds.size,
            totalCount = uiState.items.size,
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
