package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType

@Composable
fun MediaThumbnailItem(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(if (isSelectionMode) 8.dp else 2.dp))
            .background(Color(0xFF1E262C))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Thumbnail placeholder background with subtle tint based on ID
        val tintHue = (item.id.hashCode() and 0xFF) / 255f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.hsv(tintHue * 360f, 0.35f, 0.4f))
        )

        // Video duration badge
        if (item.mediaType == MediaType.VIDEO && item.durationMs != null) {
            val seconds = (item.durationMs / 1000) % 60
            val minutes = (item.durationMs / 1000) / 60
            val formatted = "$minutes:${seconds.toString().padStart(2, '0')}"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "▶ $formatted",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        // Favorite heart icon
        if (item.isFavorite) {
            Text(
                text = "♥",
                color = Color(0xFFFF4D4D),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
            )
        }

        // Selection indicator checkmark
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF00B4D8) else Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isSelected) "✓" else "",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}
