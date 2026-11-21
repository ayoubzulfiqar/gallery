package com.ayoubzulfiqar.gallery.ui.trash

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.ui.common.ConfirmDialog
import com.ayoubzulfiqar.gallery.ui.common.EmptyState
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.common.formatDateTime
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.timeline.MarqueeSelectionBar
import com.ayoubzulfiqar.gallery.ui.timeline.MediaGrid
import com.ayoubzulfiqar.gallery.ui.timeline.SelectionActions

/**
 * The system trash: items deleted from the gallery land here and can be restored or
 * permanently removed. Without this screen "Moved to trash" would be a one-way door.
 */
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode = selectedIds.isNotEmpty()
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    GalleryBackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

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
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = "Trash",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when {
                            uiState.isLoading -> "Loading…"
                            uiState.isEmpty -> "Nothing in the trash"
                            else -> buildString {
                                append("${uiState.items.size} item${if (uiState.items.size == 1) "" else "s"}")
                                uiState.nextExpiryMillis?.let { append(" · first auto-delete ${formatDateTime(it)}") }
                            }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
                if (!uiState.isEmpty && !isSelectionMode) {
                    TextButton(onClick = { confirmEmpty = true }) {
                        Text("Empty trash", color = GalleryColors.Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Text(
                text = "Deleted items stay in the system trash for 30 days. Restore them here, or delete them permanently.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Trash retention notice" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GalleryColors.Accent)
                }
                uiState.isEmpty -> EmptyState(
                    icon = GalleryIcons.Trash,
                    title = "Trash is empty",
                    message = "Items you delete from the gallery appear here so you can bring them back."
                )
                else -> MediaGrid(
                    groups = uiState.groups,
                    density = TimelineDensity.DAYS,
                    selectedIds = selectedIds,
                    onItemClick = { item -> viewModel.toggleSelection(item.id) },
                    onItemLongClick = { item -> viewModel.toggleSelection(item.id) },
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

        MarqueeSelectionBar(
            selectedCount = selectedIds.size,
            totalCount = uiState.items.size,
            visible = isSelectionMode,
            actions = SelectionActions(
                onRestore = { viewModel.restoreSelected() },
                onDelete = { pendingDelete = selectedIds },
                onSelectAll = viewModel::selectAll,
                onClose = viewModel::clearSelection
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        )
    }

    pendingDelete?.let { ids ->
        ConfirmDialog(
            title = if (ids.size == 1) "Delete permanently?" else "Delete ${ids.size} items permanently?",
            message = "This removes the only remaining copy from your device. This can't be undone.",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.deleteSelected()
            },
            onDismiss = { pendingDelete = null }
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty the trash?",
            message = "Every item in the trash will be permanently removed from your device. This can't be undone.",
            confirmLabel = "Empty trash",
            destructive = true,
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            },
            onDismiss = { confirmEmpty = false }
        )
    }
}