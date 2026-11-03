package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    BoxWithConstraints(
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
        val screenWidth = maxWidth

        // Screen-size responsive column calculation
        val responsiveColumns = when (density) {
            TimelineDensity.YEARS -> (screenWidth / 52.dp).toInt().coerceIn(5, 12)
            TimelineDensity.MONTHS -> (screenWidth / 78.dp).toInt().coerceIn(3, 8)
            TimelineDensity.DAYS -> (screenWidth / 115.dp).toInt().coerceIn(2, 6)
            TimelineDensity.MASONRY -> (screenWidth / 170.dp).toInt().coerceIn(2, 4)
        }

        val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Column(modifier = Modifier.fillMaxSize()) {
            // Status bar aware Top Chrome Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Photos",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            letterSpacing = (-0.5).sp,
                            color = Color.White
                        )
                    )
                    if (uiState.totalCount > 0) {
                        Text(
                            text = "${uiState.totalCount} items",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Modern Segmented Density Selector
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TimelineDensity.values().forEach { d ->
                        val isSelected = density == d
                        val bgColor by animateColorAsState(
                            if (isSelected) Color(0xFF00B4D8) else Color.Transparent
                        )
                        val textColor by animateColorAsState(
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .clickable { viewModel.setDensity(d) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColor
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00B4D8))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(responsiveColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        end = 4.dp,
                        top = 4.dp,
                        bottom = navBarBottomPadding + 92.dp
                    )
                ) {
                    uiState.groups.forEach { group ->
                        // Header per timeline group
                        item(span = { GridItemSpan(responsiveColumns) }) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }

                        // Media items
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

        // Floating Selection Bar
        MarqueeSelectionBar(
            selectedCount = selectedIds.size,
            visible = isSelectionMode,
            onShare = { /* Share */ },
            onFavorite = { selectedIds.forEach { viewModel.toggleFavorite(it) } },
            onAddToAlbum = { /* Add to album */ },
            onMoveToVault = { /* Move to vault */ },
            onDelete = { viewModel.deleteSelected() },
            onClose = { viewModel.clearSelection() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        )
    }
}
