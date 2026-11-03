package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

@Composable
fun LightboxViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    onEdit: (mediaId: String) -> Unit,
    onShowExif: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isChromeVisible by viewModel.isChromeVisible.collectAsState()
    val items by viewModel.items.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()

    val item = items.getOrNull(currentIndex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(items.size, currentIndex) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50f && currentIndex < items.lastIndex) {
                        viewModel.setIndex(currentIndex + 1)
                    } else if (dragAmount > 50f && currentIndex > 0) {
                        viewModel.setIndex(currentIndex - 1)
                    }
                }
            }
    ) {
        if (item != null) {
            ZoomableMediaBox(
                onDismissRequest = onBack,
                onTap = { viewModel.toggleChrome() },
                modifier = Modifier.fillMaxSize()
            ) {
                val tintHue = (item.id.hashCode() and 0xFF) / 255f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.hsv(tintHue * 360f, 0.4f, 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${item.width} × ${item.height} • ${(item.sizeBytes / 1_000_000.0).toString().take(4)} MB",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (item.mediaType == MediaType.VIDEO && item.durationMs != null) {
                AnimatedVisibility(
                    visible = isChromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                ) {
                    VideoPlayerOverlay(durationMs = item.durationMs)
                }
            }
        }

        // Top Chrome Bar
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .frostedGlass(blurRadius = 24.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("← Back", fontSize = 14.sp)
                }

                Text(
                    text = "${currentIndex + 1} of ${items.size}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.toggleFavorite() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text(if (item?.isFavorite == true) "♥" else "♡", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = onShowExif,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("ℹ Info", fontSize = 14.sp)
                    }
                }
            }
        }

        // Bottom Action Bar
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .frostedGlass(blurRadius = 24.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { item?.let { onEdit(it.id) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("✎ Edit", fontSize = 14.sp)
                }
                TextButton(
                    onClick = { /* Share */ },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("↑ Share", fontSize = 14.sp)
                }
                TextButton(
                    onClick = { viewModel.deleteCurrent(onDeleted = onBack) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B))
                ) {
                    Text("🗑 Delete", fontSize = 14.sp)
                }
            }
        }
    }
}
