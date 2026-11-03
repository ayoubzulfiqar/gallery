package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

/**
 * Detects a two-finger pinch and reports one density step per gesture. Runs in the Initial pass
 * and only consumes multi-touch events, so single-finger scrolling of the grid is untouched.
 */
fun Modifier.pinchToChangeDensity(onStep: (zoomIn: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulatedZoom = 1f
        var stepped = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } >= 2) {
                accumulatedZoom *= event.calculateZoom()
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                if (!stepped && (accumulatedZoom > 1.3f || accumulatedZoom < 0.77f)) {
                    onStep(accumulatedZoom > 1f)
                    stepped = true
                }
            }
        }
    }
}

fun columnsFor(density: TimelineDensity, width: Dp): Int = when (density) {
    TimelineDensity.YEARS -> (width / 52.dp).toInt().coerceIn(5, 14)
    TimelineDensity.MONTHS -> (width / 84.dp).toInt().coerceIn(3, 9)
    TimelineDensity.DAYS -> (width / 120.dp).toInt().coerceIn(2, 7)
    TimelineDensity.MASONRY -> (width / 170.dp).toInt().coerceIn(2, 5)
}

@Composable
fun MediaGrid(
    groups: List<MediaTimelineGroup>,
    density: TimelineDensity,
    selectedIds: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onGroupToggle: (MediaTimelineGroup) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    staggeredState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    header: (@Composable () -> Unit)? = null
) {
    val isSelectionMode = selectedIds.isNotEmpty()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = columnsFor(density, maxWidth)
        val compact = density == TimelineDensity.YEARS
        // Decode thumbnails at the real cell size: dense grids need far fewer pixels per tile.
        val thumbnailSize = with(LocalDensity.current) { thumbnailBucket((maxWidth / columns).roundToPx()) }
        if (density == TimelineDensity.MASONRY) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                state = staggeredState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                if (header != null) item(key = "header", span = StaggeredGridItemSpan.FullLine, contentType = "header") { header() }
                groups.forEach { group ->
                    group.sectionTitle?.let { title ->
                        item(key = "section-${group.key}", span = StaggeredGridItemSpan.FullLine, contentType = "section") {
                            SectionHeader(title, group.sectionSubtitle)
                        }
                    }
                    item(key = "group-${group.key}", span = StaggeredGridItemSpan.FullLine, contentType = "group") {
                        GroupHeader(group, selectedIds, isSelectionMode, onGroupToggle)
                    }
                    items(group.items, key = { it.id }, contentType = { "media" }) { item ->
                        MediaThumbnailItem(
                            item = item,
                            isSelected = item.id in selectedIds,
                            isSelectionMode = isSelectionMode,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            aspectRatio = item.aspectRatio.coerceIn(0.5f, 2f),
                            thumbnailSize = thumbnailSize
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { header() }
                groups.forEach { group ->
                    group.sectionTitle?.let { title ->
                        item(key = "section-${group.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "section") {
                            SectionHeader(title, group.sectionSubtitle)
                        }
                    }
                    item(key = "group-${group.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "group") {
                        GroupHeader(group, selectedIds, isSelectionMode, onGroupToggle)
                    }
                    items(group.items, key = { it.id }, contentType = { "media" }) { item ->
                        MediaThumbnailItem(
                            item = item,
                            isSelected = item.id in selectedIds,
                            isSelectionMode = isSelectionMode,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            compact = compact,
                            thumbnailSize = thumbnailSize
                        )
                    }
                }
            }
        }
    }
}

/** Separates major sections of the grid, e.g. the Videos block below all photos. */
@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Row(
            modifier = Modifier.padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(GalleryColors.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(GalleryIcons.Play, contentDescription = null, tint = GalleryColors.Accent, modifier = Modifier.size(14.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                subtitle?.let {
                    Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: MediaTimelineGroup,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    onToggle: (MediaTimelineGroup) -> Unit
) {
    val allSelected = isSelectionMode && group.items.all { it.id in selectedIds }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f)
        )
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Checkbox) { onToggle(group) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = GalleryIcons.SelectAll,
                    contentDescription = null,
                    tint = if (allSelected) GalleryColors.Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (allSelected) "Deselect" else "Select",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allSelected) GalleryColors.Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        } else {
            Text(
                text = "${group.items.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}
