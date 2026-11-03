package com.ayoubzulfiqar.gallery.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
expect fun AsyncMediaImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop
)
