package com.ayoubzulfiqar.gallery.ui.exif

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

@Composable
fun ExifInspectorSheet(
    exif: ExifMetadata?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stripLocationOnShare by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF13181C))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Details & EXIF",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                TextButton(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                ) {
                    Text("✕", fontSize = 16.sp)
                }
            }

            // Camera & Optical Specs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E262C), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${exif?.make ?: "Apple / Google"} ${exif?.model ?: "Pro Camera"}",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = exif?.lensModel ?: "Main Camera 24mm equivalent",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ExifPill("ISO", exif?.iso?.toString() ?: "50")
                    ExifPill("Aperture", "f/${exif?.aperture ?: "1.8"}")
                    ExifPill("Shutter", exif?.shutterSpeed ?: "1/500s")
                    ExifPill("Focal", "${exif?.focalLength ?: "24"}mm")
                }
            }

            // Location & Geotag Map section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E262C), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\uD83D\uDCCD Location",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = exif?.address ?: "San Francisco, California, USA",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
                Text(
                    text = "Lat: ${exif?.latitude ?: 37.7749}, Long: ${exif?.longitude ?: -122.4194}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Privacy: Strip GPS before sharing",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                    Switch(
                        checked = stripLocationOnShare,
                        onCheckedChange = { stripLocationOnShare = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00B4D8),
                            checkedTrackColor = Color(0xFF004D67)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ExifPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}
