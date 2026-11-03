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
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val sheetBg = if (isDark) Color(0xFF13181C) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)
    val dragHandleColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f)
    val exifIconTint = if (isDark) Color.White else Color(0xFF334155)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(sheetBg)
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
                    .background(dragHandleColor)
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
                        color = textPrimary,
                        fontSize = 18.sp
                    )
                )

                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = GalleryIcons.Close,
                        contentDescription = "Close",
                        tint = textPrimary.copy(alpha = 0.7f),
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
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
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
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
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
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = {
                        onClose()
                        onSanitizeShare()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.ShieldCheck,
                    iconTint = Color(0xFFFF9F1C),
                    title = "Sanitize In-Place",
                    subtitle = "Remove metadata directly from this device file",
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = {
                        onClose()
                        onStripInPlace()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.Vault,
                    iconTint = Color(0xFFE71D36),
                    title = "Move to Encrypted Vault",
                    subtitle = "Encrypt with AES-256 GCM inside secure sandbox",
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = {
                        onClose()
                        onMoveToVault()
                    }
                )

                MoreActionRow(
                    icon = GalleryIcons.InfoCircle,
                    iconTint = exifIconTint,
                    title = "View Technical EXIF",
                    subtitle = "Camera model, aperture, shutter, and lens specs",
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
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
    textPrimary: Color = Color.White,
    textSecondary: Color = Color.White.copy(alpha = 0.55f),
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
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
