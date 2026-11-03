package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ZoomableMediaBox(
    onDismissRequest: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val dragOffsetY = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        scale = if (scale > 1.2f) 1.0f else 2.5f
                        offset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (scale > 1.0f || zoom > 1.0f) {
                        scale = (scale * zoom).coerceIn(1.0f, 5.0f)
                        val maxPanX = (scale - 1.0f) * 500f
                        val maxPanY = (scale - 1.0f) * 800f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    } else if (pan.y > 0 || dragOffsetY.value > 0) {
                        // Vertical swipe-to-dismiss gesture when not zoomed in
                        coroutineScope.launch {
                            val newY = (dragOffsetY.value + pan.y).coerceAtLeast(0f)
                            dragOffsetY.snapTo(newY)
                            if (newY > 300f) {
                                onDismissRequest()
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
