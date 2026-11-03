package com.ayoubzulfiqar.gallery.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.frostedGlass(
    blurRadius: Dp = 20.dp,
    tintColor: Color = Color.Black.copy(alpha = 0.65f),
    borderColor: Color = Color.White.copy(alpha = 0.15f),
    shape: Shape = RoundedCornerShape(24.dp)
): Modifier = this
    .clip(shape)
    .blur(blurRadius)
    .background(tintColor, shape)
    .border(1.dp, borderColor, shape)
