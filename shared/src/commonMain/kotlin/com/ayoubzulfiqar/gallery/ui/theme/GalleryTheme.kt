package com.ayoubzulfiqar.gallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D4FF),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF004D67),
    onPrimaryContainer = Color(0xFFC2E8FF),
    secondary = Color(0xFFB5CAD7),
    onSecondary = Color(0xFF20333D),
    background = Color(0xFF0B0F12),
    onBackground = Color(0xFFE0E2E5),
    surface = Color(0xFF13181C),
    onSurface = Color(0xFFE0E2E5),
    surfaceVariant = Color(0xFF1E262C),
    onSurfaceVariant = Color(0xFFC0C8CE),
    outline = Color(0xFF8A9297)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00678A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC2E8FF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = Color(0xFF4D616C),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDFE3E7),
    onSurfaceVariant = Color(0xFF42484C),
    outline = Color(0xFF72787D)
)

@Composable
fun GalleryTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/** Brand palette shared by custom components (Material roles cover the rest). */
object GalleryColors {
    val Accent = Color(0xFF00B4D8)
    val Danger = Color(0xFFEF4444)
    val Favorite = Color(0xFFFF3B30)
    val Warning = Color(0xFFFF9F1C)
    val DarkCanvas = Color(0xFF0B0F12)
    val LightCanvas = Color(0xFFF7F9FC)
}

@Composable
fun GalleryTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
