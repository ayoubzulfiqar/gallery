package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.formatFileSize
import com.ayoubzulfiqar.gallery.ui.common.formatCoordinate
import com.ayoubzulfiqar.gallery.ui.common.formatDateTime
import com.ayoubzulfiqar.gallery.ui.common.formatDecimal
import com.ayoubzulfiqar.gallery.ui.common.megapixels
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

data class MoreSheetAction(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerMoreSheet(actions: List<MoreSheetAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Options",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(action.tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(action.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            action.subtitle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    item: MediaItem,
    state: DetailsState,
    onDismiss: () -> Unit,
    onOpenMap: ((Double, Double) -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(GalleryIcons.InfoCircle, contentDescription = null, tint = GalleryColors.Accent, modifier = Modifier.size(20.dp))
                Text(
                    "Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val details = (state as? DetailsState.Loaded)?.details
            val shown = details?.item ?: item

            InfoCard {
                InfoRow("Name", shown.displayName)
                InfoRow("Taken", formatDateTime(shown.dateTakenMillis))
                val resolution = if (shown.displayWidth > 0 && shown.displayHeight > 0) {
                    "${shown.displayWidth} × ${shown.displayHeight}" + (megapixels(shown.width, shown.height)?.let { " · $it" } ?: "")
                } else {
                    null
                }
                resolution?.let { InfoRow("Resolution", it) }
                if (shown.sizeBytes > 0) InfoRow("Size", formatFileSize(shown.sizeBytes))
                InfoRow("Type", shown.mimeType)
                (shown.relativePath ?: shown.bucketName)?.let { InfoRow("Folder", it) }
            }

            when (state) {
                DetailsState.Loading, DetailsState.Hidden -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GalleryColors.Accent, modifier = Modifier.size(28.dp))
                }
                is DetailsState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                is DetailsState.Loaded -> DetailsContent(details!!, onOpenMap)
            }
        }
    }
}

@Composable
private fun DetailsContent(details: MediaDetails, onOpenMap: ((Double, Double) -> Unit)?) {
    val exif = details.exif
    if (exif != null && exif.hasCameraInfo) {
        InfoCard {
            exif.cameraName?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            exif.lensModel?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val pills = listOfNotNull(
                exif.iso?.let { "ISO" to it.toString() },
                exif.aperture?.let { "Aperture" to "ƒ/${formatDecimal(it)}" },
                exif.shutterSpeed?.let { "Shutter" to it },
                exif.focalLength?.let { "Focal" to "${formatDecimal(it)} mm" }
            )
            if (pills.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    pills.forEach { (label, value) -> ExifPill(label, value) }
                }
            }
            listOfNotNull(
                exif.flash?.let { "Flash" to it },
                exif.whiteBalance?.let { "White balance" to it },
                exif.software?.let { "Software" to it }
            ).forEach { (label, value) -> InfoRow(label, value) }
        }
    }

    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(GalleryIcons.Location, contentDescription = null, tint = GalleryColors.Accent, modifier = Modifier.size(16.dp))
            Text("Location", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        val lat = exif?.latitude
        val lon = exif?.longitude
        if (lat != null && lon != null) {
            Text(formatCoordinate(lat, lon), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            exif.altitude?.let {
                Text("Altitude ${formatDecimal(it)} m", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            if (onOpenMap != null) {
                TextButton(onClick = { onOpenMap(lat, lon) }) { Text("Open in Maps", color = GalleryColors.Accent) }
            }
        } else {
            Text(
                text = if (details.locationRedacted) {
                    "Location is hidden because the app wasn't allowed to read photo locations."
                } else {
                    "No location saved with this item."
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun ExifPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
