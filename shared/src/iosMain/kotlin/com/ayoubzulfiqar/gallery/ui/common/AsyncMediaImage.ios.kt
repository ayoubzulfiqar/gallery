package com.ayoubzulfiqar.gallery.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun AsyncMediaImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier,
    isThumbnail: Boolean,
    contentScale: ContentScale
) {
    val tintHue = (uri.hashCode() and 0xFF) / 255f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.hsv(tintHue * 360f, 0.35f, 0.40f))
    )
}
