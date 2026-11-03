package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.ui.common.AsyncMediaImage
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import com.ayoubzulfiqar.gallery.ui.theme.frostedGlass

@Composable
fun LightboxViewerScreen(
    viewModel: ViewerViewModel,
    onBack: UnitCallback = {},
    onEdit: (mediaId: String) -> Unit = {},
    onPlayVideo: (contentUri: String) -> Unit = {},
    onShowExif: UnitCallback = {},
    onGoogleLens: (contentUri: String) -> Unit = {},
    onSetAsWallpaper: (contentUri: String) -> Unit = {},
    onSanitizeShare: (contentUri: String, mimeType: String) -> Unit = { _, _ -> },
    onStripInPlace: (contentUri: String) -> Unit = {},
    onMoveToVault: (mediaId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isChromeVisible by viewModel.isChromeVisible.collectAsState()
    val items by viewModel.items.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()

    var showMoreSheet by remember { mutableStateOf(false) }

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
                AsyncMediaImage(
                    uri = item.uri,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    isThumbnail = false,
                    contentScale = ContentScale.Fit
                )
            }

            if (item.mediaType == MediaType.VIDEO && item.durationMs != null) {
                AnimatedVisibility(
                    visible = isChromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 88.dp)
                ) {
                    VideoPlayerOverlay(
                        durationMs = item.durationMs,
                        onPlayClick = { onPlayVideo(item.uri) }
                    )
                }
            }
        }

        // Top Chrome Bar with System Status Bar Inset
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(blurRadius = 24.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = GalleryIcons.ChevronBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "${currentIndex + 1} of ${items.size}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        val isFav = item?.isFavorite == true
                        Icon(
                            imageVector = if (isFav) GalleryIcons.HeartFilled else GalleryIcons.HeartOutline,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFFFF3B30) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showMoreSheet = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = GalleryIcons.MoreHorizontal,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom Action Bar with Navigation Bar Inset
        AnimatedVisibility(
            visible = isChromeVisible && !showMoreSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(blurRadius = 24.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionPillButton(
                    icon = GalleryIcons.Lens,
                    label = "Lens",
                    tint = Color(0xFF00B4D8),
                    onClick = { item?.let { onGoogleLens(it.uri) } }
                )
                if (item?.mediaType == MediaType.VIDEO) {
                    ActionPillButton(
                        icon = GalleryIcons.Play,
                        label = "Play",
                        tint = Color(0xFF00B4D8),
                        onClick = { item?.let { onPlayVideo(it.uri) } }
                    )
                } else {
                    ActionPillButton(
                        icon = GalleryIcons.EditPen,
                        label = "Edit",
                        onClick = { item?.let { onEdit(it.id) } }
                    )
                }
                ActionPillButton(
                    icon = GalleryIcons.ShareArrow,
                    label = "Share",
                    onClick = { item?.let { onSanitizeShare(it.uri, it.mimeType) } }
                )
                ActionPillButton(
                    icon = GalleryIcons.Wallpaper,
                    label = "Wallpaper",
                    onClick = { item?.let { onSetAsWallpaper(it.uri) } }
                )
                ActionPillButton(
                    icon = GalleryIcons.Trash,
                    label = "Delete",
                    tint = Color(0xFFFF453A),
                    onClick = { viewModel.deleteCurrent { onBack() } }
                )
            }
        }

        // Contextual More Options Bottom Sheet
        if (showMoreSheet && item != null) {
            ViewerMoreSheet(
                onGoogleLens = { onGoogleLens(item.uri) },
                onSetAsWallpaper = { onSetAsWallpaper(item.uri) },
                onSanitizeShare = { onSanitizeShare(item.uri, item.mimeType) },
                onStripInPlace = { onStripInPlace(item.uri) },
                onMoveToVault = { onMoveToVault(item.id) },
                onShowExif = onShowExif,
                onClose = { showMoreSheet = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

typealias UnitCallback = () -> Unit

@Composable
private fun ActionPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = label,
            color = tint.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
