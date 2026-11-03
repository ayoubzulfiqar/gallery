package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

@Composable
fun ViewerMoreSheet(
    onGoogleLens: () -> Unit,
    onSetAsWallpaper: () -> Unit,
    onSanitizeShare: () -> Unit,
    onStripInPlace: () -> Unit,
    onMoveToVault: () -> Unit,
    onShowExif: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF13181C))
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally)
            )

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Options & Tools",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )

                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = GalleryIcons.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Options List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MoreActionRow(
                    icon = GalleryIcons.Lens,
                    iconTint = Color(0xFF00B4D8),
                    title = "Google Lens Visual Search",
                    subtitle = "Scan and search visual content online",
                    onClick = {
                        onClose()
                        onGoogleLens()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.Wallpaper,
                    iconTint = Color(0xFF80D4FF),
                    title = "Set as Wallpaper",
                    subtitle = "Apply to Home Screen, Lock Screen, or Both",
                    onClick = {
                        onClose()
                        onSetAsWallpaper()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.ShieldCheck,
                    iconTint = Color(0xFF2EC4B6),
                    title = "Sanitize & Share",
                    subtitle = "Strip GPS and camera info on-the-fly (Zero duplicates)",
                    onClick = {
                        onClose()
                        onSanitizeShare()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.ShieldCheck,
                    iconTint = Color(0xFFFFB703),
                    title = "Strip Metadata In-Place",
                    subtitle = "Permanently wipe GPS and camera identifiers from file",
                    onClick = {
                        onClose()
                        onStripInPlace()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.Vault,
                    iconTint = Color(0xFF00B4D8),
                    title = "Move to Encrypted Vault",
                    subtitle = "Encrypt securely behind AES-256-GCM PIN",
                    onClick = {
                        onClose()
                        onMoveToVault()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.InfoCircle,
                    iconTint = Color.White,
                    title = "View Technical EXIF",
                    subtitle = "Camera model, aperture, shutter, and lens specs",
                    onClick = {
                        onClose()
                        onShowExif()
                    }
                )
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
