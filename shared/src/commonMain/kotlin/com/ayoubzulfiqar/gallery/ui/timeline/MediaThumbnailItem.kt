package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.formatDuration
import com.ayoubzulfiqar.gallery.ui.common.AsyncMediaImage
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

private val BadgeBackground = Color.Black.copy(alpha = 0.72f)
private val CompactShape = RoundedCornerShape(4.dp)
private val RegularShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(6.dp)

/**
 * Grid tile tuned for fast scrolling through thousands of items: the image decodes at the cell's
 * real pixel size ([thumbnailSize]), animations are applied in the draw phase via graphicsLayer
 * (no recomposition per frame) and selection chrome is only composed in selection mode.
 */
@Composable
fun MediaThumbnailItem(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    compact: Boolean = false,
    thumbnailSize: Int? = null
) {
    val shape = if (compact) CompactShape else RegularShape
    // The press-in animation only exists in selection mode: no per-tile animation/coroutine while browsing.
    val inset = if (isSelectionMode) animateFloatAsState(if (isSelected) 0.88f else 1f) else null
    val description = remember(item.id, item.isFavorite, item.isVideo) {
        (if (item.isVideo) "Video " else "Photo ") + item.displayName + if (item.isFavorite) ", favorite" else ""
    }
    val durationLabel = remember(item.durationMs) { item.durationMs?.takeIf { it > 0 }?.let(::formatDuration) }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .padding(if (compact) 0.75.dp else 1.5.dp)
            .semantics {
                contentDescription = description
                if (isSelectionMode) selected = isSelected
            }
            .combinedClickable(
                role = if (isSelectionMode) Role.Checkbox else Role.Image,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = inset?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                    this.shape = shape
                    clip = true
                }
                // No tile background: the loading placeholder covers empty tiles and a loaded photo
                // covers the whole cell, so painting a background underneath is pure overdraw.
                .then(if (isSelected) Modifier.border(2.dp, GalleryColors.Accent, shape) else Modifier)
        ) {
            AsyncMediaImage(
                uri = item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                isThumbnail = true,
                contentScale = ContentScale.Crop,
                isVideo = item.isVideo,
                mimeType = item.mimeType,
                requestedSize = thumbnailSize
            )

            if (item.isVideo) {
                if (compact) {
                    Icon(
                        imageVector = GalleryIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(8.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(BadgeBackground, BadgeShape)
                            .padding(horizontal = 5.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = GalleryIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                        if (durationLabel != null) {
                            Text(text = " $durationLabel", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (!compact && item.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(imageVector = GalleryIcons.HeartFilled, contentDescription = null, tint = GalleryColors.Favorite, modifier = Modifier.size(11.dp))
                }
            }
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (compact) 2.dp else 6.dp)
                    .size(if (compact) 14.dp else 22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) GalleryColors.Accent else Color.Black.copy(alpha = 0.45f))
                    .border(1.5.dp, Color.White.copy(alpha = if (isSelected) 1f else 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = GalleryIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 9.dp else 14.dp)
                    )
                }
            }
        }
    }
}

/** Rounds a cell's pixel size up to a 64 px bucket so neighbouring densities share cache entries. */
fun thumbnailBucket(cellPx: Int): Int = (((cellPx + 63) / 64) * 64).coerceIn(128, 512)
