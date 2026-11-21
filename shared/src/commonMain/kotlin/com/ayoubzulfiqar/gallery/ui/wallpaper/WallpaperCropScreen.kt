package com.ayoubzulfiqar.gallery.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.WallpaperCropRequest
import com.ayoubzulfiqar.gallery.domain.model.WallpaperTarget
import com.ayoubzulfiqar.gallery.ui.common.GalleryBackHandler
import com.ayoubzulfiqar.gallery.ui.common.rememberMediaBitmap
import com.ayoubzulfiqar.gallery.ui.theme.GalleryColors
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons

/**
 * In-app wallpaper cropper. The image sits under a fixed screen-aspect frame; the user pans
 * and zooms to choose the exact region, picks Home / Lock / Both, and the platform applies
 * precisely that region. Replaces the system crop-and-set intent, whose OEM implementation
 * on some devices ignores the aligned position for wide images (applies the leftmost slice).
 */
@Composable
fun WallpaperCropScreen(
    item: MediaItem,
    applying: Boolean,
    onApply: (WallpaperCropRequest) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    GalleryBackHandler(enabled = true) { onBack() }
    val bitmap = rememberMediaBitmap(item.uri, maxDimension = 2048)
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf(WallpaperTarget.BOTH) }
    // Frame size in px, hoisted so the Set button can compute the exact source rect.
    var frameWpx by remember { mutableStateOf<Float?>(null) }
    var frameHpx by remember { mutableStateOf<Float?>(null) }

    Column(modifier = modifier.fillMaxSize().background(GalleryColors.DarkCanvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = GalleryIcons.ChevronBack,
                    contentDescription = "Back",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text("Set wallpaper", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Drag to position · pinch to zoom",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val windowSize = LocalWindowInfo.current.containerSize
            val screenAspect = windowSize.width.toFloat() / windowSize.height.coerceAtLeast(1)
            val availW = constraints.maxWidth.toFloat()
            val availH = constraints.maxHeight.toFloat()
            var frameW = availW
            var frameH = frameW / screenAspect
            if (frameH > availH) {
                frameH = availH
                frameW = frameH * screenAspect
            }
            val density = LocalDensity.current
            val frameWDp = with(density) { frameW.toDp() }
            val frameHDp = with(density) { frameH.toDp() }
            androidx.compose.runtime.SideEffect {
                frameWpx = frameW
                frameHpx = frameH
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(frameWDp, frameHDp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10151C))
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Wallpaper preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX
                                    translationY = panY
                                }
                        )
                    }
                    // Gesture surface: pan/zoom the image under the fixed frame, clamped so the
                    // frame never shows area outside the image.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bitmap, frameW, frameH) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    val b = bitmap ?: return@detectTransformGestures
                                    zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                    val (px, py) = WallpaperCropMath.clampPan(
                                        b.width, b.height, frameW, frameH, zoom, panX + pan.x, panY + pan.y
                                    )
                                    panX = px
                                    panY = py
                                }
                            }
                    )
                }

                // Dim everything outside the frame; hairline border + thirds guides inside.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = (size.width - frameW) / 2f
                    val top = (size.height - frameH) / 2f
                    val frame = Rect(left, top, left + frameW, top + frameH)
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRect(frame)
                    }
                    drawPath(dim, Color.Black.copy(alpha = 0.62f))
                    drawRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = frame.topLeft,
                        size = frame.size,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    val guide = Color.White.copy(alpha = 0.18f)
                    for (i in 1..2) {
                        val gx = left + frameW * i / 3f
                        val gy = top + frameH * i / 3f
                        drawLine(guide, androidx.compose.ui.geometry.Offset(gx, top), androidx.compose.ui.geometry.Offset(gx, top + frameH), 1f)
                        drawLine(guide, androidx.compose.ui.geometry.Offset(left, gy), androidx.compose.ui.geometry.Offset(left + frameW, gy), 1f)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperTarget.entries.forEach { t ->
                    val selected = t == target
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) GalleryColors.Accent else Color.White.copy(alpha = 0.08f))
                            .clickable(role = Role.RadioButton) { target = t }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = when (t) {
                                WallpaperTarget.HOME -> "Home screen"
                                WallpaperTarget.LOCK -> "Lock screen"
                                WallpaperTarget.BOTH -> "Both"
                            },
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val b = bitmap ?: return@Button
                    val fw = frameWpx ?: return@Button
                    val fh = frameHpx ?: return@Button
                    val rect = WallpaperCropMath.sourceRect(b.width, b.height, fw, fh, zoom, panX, panY)
                    onApply(WallpaperCropRequest(rect, b.width, b.height, target))
                },
                enabled = bitmap != null && !applying && frameWpx != null,
                colors = ButtonDefaults.buttonColors(containerColor = GalleryColors.Accent, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (applying) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Set wallpaper", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}