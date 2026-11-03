package com.ayoubzulfiqar.gallery.ui.common

import androidx.compose.runtime.Composable

@Composable
expect fun GalleryBackHandler(enabled: Boolean = true, onBack: () -> Unit)
