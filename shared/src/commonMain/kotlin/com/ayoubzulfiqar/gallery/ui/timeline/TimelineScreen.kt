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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.ui.common.AsyncMediaImage
import com.ayoubzulfiqar.gallery.ui.gesture.PinchDensityState
import com.ayoubzulfiqar.gallery.ui.gesture.TimelineDensity
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.ThemeMode

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onMediaClick: (mediaId: String) -> Unit,
    onVideoClick: (mediaId: String, uri: String) -> Unit = { _, _ -> },
    onFolderClick: (bucketId: String, folderName: String) -> Unit = { _, _ -> },
    onMoveToVault: (List<String>) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val density by viewModel.density.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val quickFolders by viewModel.quickFolders.collectAsState()
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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Photos",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    if (uiState.totalCount > 0) {
                        Text(
                            text = "${uiState.totalCount} items",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Theme Quick-Toggle Button
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        val icon = when (themeMode) {
                            ThemeMode.LIGHT -> GalleryIcons.Sun
                            ThemeMode.DARK -> GalleryIcons.Moon
                            ThemeMode.SYSTEM -> GalleryIcons.AutoTheme
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle Theme Mode",
                            tint = when (themeMode) {
                                ThemeMode.LIGHT -> Color(0xFFFFB703)
                                ThemeMode.DARK -> Color(0xFF80D4FF)
                                ThemeMode.SYSTEM -> MaterialTheme.colorScheme.onBackground
                            },
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Modern Segmented Density Selector
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TimelineDensity.values().forEach { d ->
                            val isSelected = density == d
                            val bgColor by animateColorAsState(
                                if (isSelected) Color(0xFF00B4D8) else Color.Transparent
                            )
                            val textColor by animateColorAsState(
                                if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
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
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimelineCategoryFilter.values().forEach { cat ->
                    val isSelected = categoryFilter == cat
                    val bg = if (isSelected) Color(0xFF00B4D8) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                    val textCol = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .clickable { viewModel.setCategoryFilter(cat) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = textCol,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
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
                    // Google Gallery Go Quick-Jump Folder Carousel
                    if (quickFolders.isNotEmpty() && categoryFilter == TimelineCategoryFilter.ALL) {
                        item(span = { GridItemSpan(responsiveColumns) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                quickFolders.take(4).forEach { folder ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(82.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onFolderClick(folder.id, folder.name) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF1B222C))
                                        ) {
                                            AsyncMediaImage(
                                                uri = folder.coverUri,
                                                contentDescription = folder.name,
                                                modifier = Modifier.fillMaxSize(),
                                                isThumbnail = true,
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            ),
                                            maxLines = 1,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    uiState.groups.forEach { group ->
                        // Header per timeline group (e.g., "November 2026")
                        item(span = { GridItemSpan(responsiveColumns) }) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
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
                                    } else if (item.mediaType == MediaType.VIDEO) {
                                        onVideoClick(item.id, item.uri)
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
            onMoveToVault = {
                onMoveToVault(selectedIds.toList())
                viewModel.clearSelection()
            },
            onDelete = { viewModel.deleteSelected() },
            onClose = { viewModel.clearSelection() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        )
    }
}
