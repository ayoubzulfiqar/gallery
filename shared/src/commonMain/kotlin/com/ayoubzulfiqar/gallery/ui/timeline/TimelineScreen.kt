package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.ui.gesture.PinchDensityState
import com.ayoubzulfiqar.gallery.ui.gesture.TimelineDensity

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onMediaClick: (mediaId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val density by viewModel.density.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    val pinchState = remember { PinchDensityState(density) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1.0f) {
                        pinchState.onPinch(zoom)
                        val target = pinchState.calculateSnapTarget()
                        if (target != density) {
                            viewModel.setDensity(target)
                            pinchState.commitSnap(target)
                        }
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Photos",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                )

                // Density Selector Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimelineDensity.values().forEach { d ->
                        FilterChip(
                            selected = density == d,
                            onClick = { viewModel.setDensity(d) },
                            label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(density.columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    uiState.groups.forEach { group ->
                        // Sticky Header per group
                        item(span = { GridItemSpan(density.columns) }) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        // Media items in group
                        items(group.items, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            MediaThumbnailItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        onMediaClick(item.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Marquee Selection Action Bar
        MarqueeSelectionBar(
            selectedCount = selectedIds.size,
            visible = isSelectionMode,
            onShare = { /* Share intent */ },
            onFavorite = { selectedIds.forEach { viewModel.toggleFavorite(it) } },
            onAddToAlbum = { /* Add to album */ },
            onMoveToVault = { /* Move to vault */ },
            onDelete = { viewModel.deleteSelected() },
            onClose = { viewModel.clearSelection() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
