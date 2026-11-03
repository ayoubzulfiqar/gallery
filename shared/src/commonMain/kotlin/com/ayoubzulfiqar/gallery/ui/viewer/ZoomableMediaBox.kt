package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ZOOMED_THRESHOLD = 1.02f

/**
 * Pinch/double-tap zoom with panning, plus swipe-down-to-dismiss when not zoomed.
 *
 * Horizontal swipes are left untouched when not zoomed so the surrounding pager can page; the
 * dismiss drag only starts after a clearly vertical movement beyond the touch slop.
 */
@Composable
fun ZoomableMediaBox(
    onDismissRequest: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = true,
    onZoomChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val dismissOffset = remember { Animatable(0f) }

    val isZoomed = scale > ZOOMED_THRESHOLD
    LaunchedEffect(isZoomed) { onZoomChanged(isZoomed) }

    fun clampOffset(raw: Offset, forScale: Float): Offset {
        val maxX = (forScale - 1f) * containerSize.width / 2f
        val maxY = (forScale - 1f) * containerSize.height / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    fun animateZoomTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        scope.launch {
            animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                scale = startScale + (targetScale - startScale) * fraction
                offset = startOffset + (targetOffset - startOffset) * fraction
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(zoomEnabled) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = if (!zoomEnabled) null else { tap ->
                        if (scale > ZOOMED_THRESHOLD) {
                            animateZoomTo(1f, Offset.Zero)
                        } else {
                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            val focal = tap - center
                            animateZoomTo(DOUBLE_TAP_SCALE, clampOffset(-focal * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE))
                        }
                    }
                )
            }
            .pointerInput(zoomEnabled) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dragY = 0f
                    var dragX = 0f
                    var dismissing = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break

                        if (pressed >= 2 && zoomEnabled) {
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val newScale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
                            if (newScale > 1f && containerSize.width > 0) {
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val focal = centroid - center
                                val factor = newScale / scale
                                offset = clampOffset((offset - focal) * factor + focal + pan, newScale)
                            } else {
                                offset = Offset.Zero
                            }
                            scale = newScale
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && scale > ZOOMED_THRESHOLD) {
                            val pan = event.calculatePan()
                            val proposed = offset + pan
                            val clamped = clampOffset(proposed, scale)
                            offset = clamped
                            // At a horizontal edge, let the pager take over so swiping still navigates.
                            val atEdge = abs(clamped.x - proposed.x) > 0.5f && abs(pan.x) > abs(pan.y)
                            if (!atEdge) event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && !dismissing) {
                            val change = event.changes.first { it.pressed }.positionChange()
                            dragX += change.x
                            dragY += change.y
                            if (dragY > touchSlop && abs(dragY) > abs(dragX) * 1.5f) dismissing = true
                        } else if (pressed == 1 && dismissing) {
                            val change = event.changes.first { it.pressed }.positionChange()
                            scope.launch { dismissOffset.snapTo((dismissOffset.value + change.y).coerceAtLeast(0f)) }
                            event.changes.forEach { it.consume() }
                        }
                    }
                    if (dismissing) {
                        val threshold = containerSize.height * 0.18f
                        scope.launch {
                            if (dismissOffset.value > threshold) {
                                dismissOffset.animateTo(containerSize.height.toFloat(), spring(stiffness = Spring.StiffnessMediumLow))
                                onDismissRequest()
                            } else {
                                dismissOffset.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                            }
                        }
                    }
                    if (scale < ZOOMED_THRESHOLD && scale != 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
            .offset { IntOffset(0, dismissOffset.value.roundToInt()) }
            .graphicsLayer {
                val dismissFraction = if (containerSize.height > 0) (dismissOffset.value / containerSize.height).coerceIn(0f, 0.35f) else 0f
                scaleX = scale * (1f - dismissFraction)
                scaleY = scale * (1f - dismissFraction)
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
