package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

@Composable
fun VideoPlayerOverlay(
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0.35f) }
    var isMuted by remember { mutableStateOf(false) }

    val currentSeconds = ((durationMs * currentProgress) / 1000).toLong()
    val totalSeconds = durationMs / 1000

    val formattedCurrent = "${currentSeconds / 60}:${(currentSeconds % 60).toString().padStart(2, '0')}"
    val formattedTotal = "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .frostedGlass(blurRadius = 24.dp, tintColor = Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) GalleryIcons.Pause else GalleryIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "$formattedCurrent / $formattedTotal",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Slider(
                value = currentProgress,
                onValueChange = { currentProgress = it },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF00B4D8),
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                )
            )

            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) GalleryIcons.VolumeMute else GalleryIcons.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
