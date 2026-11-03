package com.ayoubzulfiqar.gallery.ui.common

import androidx.compose.runtime.Composable

@Composable
actual fun GalleryBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS; back gestures are managed by native navigation
}
