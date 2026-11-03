package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

data class SelectionActions(
    val onShare: (() -> Unit)? = null,
    val onFavorite: (() -> Unit)? = null,
    val onMoveToVault: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onSelectAll: (() -> Unit)? = null,
    val onRestore: (() -> Unit)? = null,
    val onClose: () -> Unit
)

@Composable
fun MarqueeSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    visible: Boolean,
    actions: SelectionActions,
    allFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it * 2 }),
        exit = slideOutVertically(targetOffsetY = { it * 2 }),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .frostedGlass(isDark = true, blurRadius = 24.dp)
                .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BarButton(GalleryIcons.Close, "Clear selection", Color.White.copy(alpha = 0.8f), actions.onClose)
                Box(
                    modifier = Modifier.size(26.dp).background(GalleryColors.Accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedCount > 999) "999+" else "$selectedCount",
                        color = Color.White,
                        fontSize = if (selectedCount > 99) 9.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                actions.onSelectAll?.takeIf { selectedCount < totalCount }?.let {
                    BarButton(GalleryIcons.SelectAll, "Select all", Color.White, it)
                }
                actions.onRestore?.let { BarButton(GalleryIcons.Restore, "Restore to gallery", GalleryColors.Accent, it) }
                actions.onShare?.let { BarButton(GalleryIcons.ShareArrow, "Share", Color.White, it) }
                actions.onFavorite?.let {
                    BarButton(
                        if (allFavorite) GalleryIcons.HeartFilled else GalleryIcons.HeartOutline,
                        if (allFavorite) "Remove from favorites" else "Add to favorites",
                        if (allFavorite) GalleryColors.Favorite else Color.White,
                        it
                    )
                }
                actions.onMoveToVault?.let { BarButton(GalleryIcons.Vault, "Move to vault", GalleryColors.Accent, it) }
                actions.onDelete?.let { BarButton(GalleryIcons.Trash, "Delete", Color(0xFFFF453A), it) }
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
