package com.ayoubzulfiqar.gallery.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.platform.MediaAccess
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

@Composable
fun PermissionScreen(access: MediaAccess, onRequest: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val permanentlyDenied = access == MediaAccess.DENIED_PERMANENTLY
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(84.dp).clip(CircleShape).background(GalleryColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(GalleryIcons.Photos, contentDescription = null, tint = GalleryColors.Accent, modifier = Modifier.size(40.dp))
            }
            Text(
                text = "Your photos, on your device",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (permanentlyDenied) {
                    "Photo access is turned off. Open Settings › Permissions › Photos and videos and allow access to browse your gallery."
                } else {
                    "Gallery needs access to your photos and videos to show them. Nothing leaves your device."
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = GalleryColors.Accent, contentColor = Color.White),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (permanentlyDenied) "Open Settings" else "Allow access", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "The encrypted Vault works without photo access.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Shown on the timeline when the user granted access to selected photos only. */
@Composable
fun PartialAccessBanner(onManage: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GalleryColors.Accent.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onManage)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "You've allowed access to some photos only.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text("Manage", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GalleryColors.Accent)
    }
}
