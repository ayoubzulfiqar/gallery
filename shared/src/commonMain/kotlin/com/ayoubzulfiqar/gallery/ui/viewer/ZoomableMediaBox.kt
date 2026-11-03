package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ZoomableMediaBox(
    onDismissRequest: () -> Unit,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val dragOffsetY = remember { Animatable(0f) }

    val isZoomed = scale > 1.05f

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            // Tap gestures: single tap toggles chrome, double tap zooms into specific spot
            .pointerInput(containerSize) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1.0f
                            offset = Offset.Zero
                        } else {
                            val targetScale = 2.5f
                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            val focal = tapOffset - center
                            val maxPanX = (targetScale - 1f) * (containerSize.width / 2f)
                            val maxPanY = (targetScale - 1f) * (containerSize.height / 2f)
                            scale = targetScale
                            offset = Offset(
                                (-focal.x * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX),
                                (-focal.y * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)
                            )
                        }
                    }
                )
            }
            // Multi-touch pinch zoom & pan around touch centroid
            .pointerInput(containerSize) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (pressedPointers.size >= 2) {
                            // Two-finger pinch zoom around touch centroid
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()

                            val oldScale = scale
                            val newScale = (scale * zoomChange).coerceIn(1.0f, 6.0f)

                            if (newScale > 1.0f && containerSize.width > 0 && containerSize.height > 0) {
                                val factor = newScale / oldScale
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val focal = centroid - center
                                val maxPanX = (newScale - 1f) * (containerSize.width / 2f)
                                val maxPanY = (newScale - 1f) * (containerSize.height / 2f)

                                val rawOffset = (offset - focal) * factor + focal + panChange
                                offset = Offset(
                                    rawOffset.x.coerceIn(-maxPanX, maxPanX),
                                    rawOffset.y.coerceIn(-maxPanY, maxPanY)
                                )
                            } else {
                                offset = Offset.Zero
                            }

                            scale = newScale
                            event.changes.forEach { it.consume() }
                        } else if (pressedPointers.size == 1 && scale > 1.05f) {
                            // Single finger 2D pan when zoomed in
                            val panChange = event.calculatePan()
                            val maxPanX = (scale - 1f) * (containerSize.width / 2f)
                            val maxPanY = (scale - 1f) * (containerSize.height / 2f)

                            offset = Offset(
                                (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                            )
                            event.changes.forEach { it.consume() }
                        } else if (pressedPointers.size == 1 && scale <= 1.05f) {
                            // When not zoomed, allow vertical swipe down to dismiss
                            val panChange = event.calculatePan()
                            if (panChange.y > 0 && abs(panChange.y) > abs(panChange.x) * 1.5f || dragOffsetY.value > 0f) {
                                coroutineScope.launch {
                                    val newY = (dragOffsetY.value + panChange.y).coerceAtLeast(0f)
                                    dragOffsetY.snapTo(newY)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }

                        // Check if all pointers released while dragging to dismiss
                        val allReleased = event.changes.all { !it.pressed }
                        if (allReleased && dragOffsetY.value > 0f) {
                            if (dragOffsetY.value > 160f) {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(
                                        targetValue = 1000f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                    onDismissRequest()
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .graphicsLayer {
                val dismissFraction = (dragOffsetY.value / 600f).coerceIn(0f, 0.4f)
                scaleX = scale * (1.0f - dismissFraction)
                scaleY = scale * (1.0f - dismissFraction)
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
