package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

@Composable
fun MarqueeSelectionBar(
    selectedCount: Int,
    visible: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onAddToAlbum: () -> Unit,
    onMoveToVault: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .frostedGlass(blurRadius = 24.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, color = Color.White)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onShare, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Share", fontSize = 13.sp)
                }
                TextButton(onClick = onMoveToVault, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF80D4FF))) {
                    Text("Vault", fontSize = 13.sp)
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B))) {
                    Text("Delete", fontSize = 13.sp)
                }
                TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)) {
                    Text("✕", fontSize = 14.sp)
                }
            }
        }
    }
}
