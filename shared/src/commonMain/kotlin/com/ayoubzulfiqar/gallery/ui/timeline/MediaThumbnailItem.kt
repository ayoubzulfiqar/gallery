package com.ayoubzulfiqar.gallery.ui.timeline

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

@Composable
fun MediaThumbnailItem(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = if (isSelectionMode) 10.dp else 3.dp

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF161B20))
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, Color(0xFF00B4D8), RoundedCornerShape(cornerRadius))
                } else Modifier
            )
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
                .background(Color.hsv(tintHue * 360f, 0.35f, 0.38f))
        )

        // Video duration badge with custom Play vector icon
        if (item.mediaType == MediaType.VIDEO && item.durationMs != null) {
            val seconds = (item.durationMs / 1000) % 60
            val minutes = (item.durationMs / 1000) / 60
            val formatted = "$minutes:${seconds.toString().padStart(2, '0')}"
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = GalleryIcons.Play,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = " $formatted",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Favorite heart vector icon
        if (item.isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = GalleryIcons.HeartFilled,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Selection indicator checkmark circle with custom Check vector
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFF00B4D8)
                        else Color.Black.copy(alpha = 0.55f)
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = GalleryIcons.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
