package com.ayoubzulfiqar.gallery.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.frostedGlass(
    isDark: Boolean = true,
    blurRadius: Dp = 20.dp,
    tintColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier {
    val bg = tintColor ?: if (isDark) {
        Color(0xFF13181C).copy(alpha = 0.85f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.92f)
    }
    val border = borderColor ?: if (isDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    return this
        .clip(shape)
        .background(bg, shape)
        .border(1.dp, border, shape)
}
