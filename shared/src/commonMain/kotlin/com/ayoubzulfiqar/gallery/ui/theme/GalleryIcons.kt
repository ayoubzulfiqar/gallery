package com.ayoubzulfiqar.gallery.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bespoke, modern, resolution-independent vector icons for the Gallery app.
 * Crafted with crisp 24x24 geometry and consistent 2px stroke weight.
 */
object GalleryIcons {

    // Photos Navigation: Landscape frame with mountain peaks and sun
    val Photos: ImageVector by lazy {
        ImageVector.Builder(
            name = "Photos",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer rounded rectangle
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 4f)
                lineTo(19f, 4f)
                curveTo(20.1f, 4f, 21f, 4.9f, 21f, 6f)
                lineTo(21f, 18f)
                curveTo(21f, 19.1f, 20.1f, 20f, 19f, 20f)
                lineTo(5f, 20f)
                curveTo(3.9f, 20f, 3f, 19.1f, 3f, 18f)
                lineTo(3f, 6f)
                curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f)
                close()
            }
            // Sun circle
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(8.5f, 8.5f)
                curveTo(9.33f, 8.5f, 10f, 7.83f, 10f, 7f)
                curveTo(10f, 6.17f, 9.33f, 5.5f, 8.5f, 5.5f)
                curveTo(7.67f, 5.5f, 7f, 6.17f, 7f, 7f)
                curveTo(7f, 7.83f, 7.67f, 8.5f, 8.5f, 8.5f)
                close()
            }
            // Mountain peaks
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 17f)
                lineTo(9f, 12f)
                lineTo(13f, 16f)
                lineTo(16f, 13f)
                lineTo(20f, 17f)
            }
        }.build()
    }

    // Albums Navigation: Overlapping collection cards
    val Albums: ImageVector by lazy {
        ImageVector.Builder(
            name = "Albums",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Front card
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 7f)
                lineTo(19f, 7f)
                curveTo(20.1f, 7f, 21f, 7.9f, 21f, 9f)
                lineTo(21f, 19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                lineTo(3f, 9f)
                curveTo(3f, 7.9f, 3.9f, 7f, 5f, 7f)
                close()
            }
            // Back card layered offset
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7f, 3.5f)
                lineTo(17f, 3.5f)
                curveTo(18.1f, 3.5f, 19f, 4.4f, 19f, 5.5f)
            }
            // Inner grid glyph
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(8f, 12f)
                lineTo(16f, 12f)
                moveTo(8f, 16f)
                lineTo(13f, 16f)
            }
        }.build()
    }

    // Vault Navigation: High-security padlock
    val Vault: ImageVector by lazy {
        ImageVector.Builder(
            name = "Vault",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Shackle
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7.5f, 10f)
                lineTo(7.5f, 6.5f)
                curveTo(7.5f, 4.01f, 9.51f, 2f, 12f, 2f)
                curveTo(14.49f, 2f, 16.5f, 4.01f, 16.5f, 6.5f)
                lineTo(16.5f, 10f)
            }
            // Body
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 10f)
                lineTo(19f, 10f)
                curveTo(20.1f, 10f, 21f, 10.9f, 21f, 12f)
                lineTo(21f, 19.5f)
                curveTo(21f, 20.6f, 20.1f, 21.5f, 19f, 21.5f)
                lineTo(5f, 21.5f)
                curveTo(3.9f, 21.5f, 3f, 20.6f, 3f, 19.5f)
                lineTo(3f, 12f)
                curveTo(3f, 10.9f, 3.9f, 10f, 5f, 10f)
                close()
            }
            // Keyhole center
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(12f, 14f)
                curveTo(11.17f, 14f, 10.5f, 14.67f, 10.5f, 15.5f)
                curveTo(10.5f, 16.1f, 10.86f, 16.62f, 11.38f, 16.86f)
                lineTo(11f, 18.5f)
                lineTo(13f, 18.5f)
                lineTo(12.62f, 16.86f)
                curveTo(13.14f, 16.62f, 13.5f, 16.1f, 13.5f, 15.5f)
                curveTo(13.5f, 14.67f, 12.83f, 14f, 12f, 14f)
                close()
            }
        }.build()
    }

    // Chevron Back: Minimalist left chevron
    val ChevronBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 18f)
                lineTo(9f, 12f)
                lineTo(15f, 6f)
            }
        }.build()
    }

    // Close / Cancel: Clean modern cross
    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }.build()
    }

    // Heart Outline: Smooth vector heart outline
    val HeartOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeartOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20.35f)
                curveTo(7.1f, 16.03f, 3.5f, 12.83f, 3.5f, 8.87f)
                curveTo(3.5f, 5.71f, 5.96f, 3.25f, 9.12f, 3.25f)
                curveTo(10.91f, 3.25f, 12.62f, 4.09f, 13.7f, 5.41f)
                curveTo(14.78f, 4.09f, 16.49f, 3.25f, 18.28f, 3.25f)
                curveTo(21.44f, 3.25f, 23.9f, 5.71f, 23.9f, 8.87f)
                curveTo(23.9f, 12.83f, 20.3f, 16.03f, 15.4f, 20.35f)
                lineTo(12f, 23.4f)
                close()
            }
        }.build()
    }

    // Heart Filled: Vibrant filled heart
    val HeartFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeartFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color(0xFFFF3B30))
            ) {
                moveTo(12f, 21.35f)
                curveTo(6.6f, 16.48f, 2.5f, 12.9f, 2.5f, 8.5f)
                curveTo(2.5f, 5.42f, 4.92f, 3f, 8f, 3f)
                curveTo(9.74f, 3f, 11.41f, 3.81f, 12.5f, 5.09f)
                curveTo(13.59f, 3.81f, 15.26f, 3f, 17f, 3f)
                curveTo(20.08f, 3f, 22.5f, 5.42f, 22.5f, 8.5f)
                curveTo(22.5f, 12.9f, 18.4f, 16.48f, 13f, 21.35f)
                lineTo(12f, 22.25f)
                close()
            }
        }.build()
    }

    // Info Circle: Minimalist round info badge
    val InfoCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "InfoCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Circle
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 2f)
                curveTo(17.52f, 2f, 22f, 6.48f, 22f, 12f)
                curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
                curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
                curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
                close()
            }
            // Dot
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 8f)
                curveTo(12.55f, 8f, 13f, 7.55f, 13f, 7f)
                curveTo(13f, 6.45f, 12.55f, 6f, 12f, 6f)
                curveTo(11.45f, 6f, 11f, 6.45f, 11f, 7f)
                curveTo(11f, 7.55f, 11.45f, 8f, 12f, 8f)
                close()
            }
            // Vertical line
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 11f)
                lineTo(12f, 17f)
            }
        }.build()
    }

    // Edit Pen: Sleek modern editing stylus
    val EditPen: ImageVector by lazy {
        ImageVector.Builder(
            name = "EditPen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16.5f, 3.5f)
                curveTo(17.33f, 2.67f, 18.67f, 2.67f, 19.5f, 3.5f)
                curveTo(20.33f, 4.33f, 20.33f, 5.67f, 19.5f, 6.5f)
                lineTo(7.5f, 18.5f)
                lineTo(3f, 20f)
                lineTo(4.5f, 15.5f)
                lineTo(16.5f, 3.5f)
                close()
            }
        }.build()
    }

    // Share Arrow: Sleek export / share glyph
    val ShareArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShareArrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Upward arrow
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(12f, 15f)
                moveTo(7f, 8f)
                lineTo(12f, 3f)
                lineTo(17f, 8f)
            }
            // Base tray
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 12f)
                lineTo(4f, 19f)
                curveTo(4f, 20.1f, 4.9f, 21f, 6f, 21f)
                lineTo(18f, 21f)
                curveTo(19.1f, 21f, 20f, 20.1f, 20f, 19f)
                lineTo(20f, 12f)
            }
        }.build()
    }

    // Trash: Outline trash bin with lid
    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Bin
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 7f)
                lineTo(6f, 19f)
                curveTo(6.1f, 20.1f, 7f, 21f, 8.1f, 21f)
                lineTo(15.9f, 21f)
                curveTo(17f, 21f, 17.9f, 20.1f, 18f, 19f)
                lineTo(19f, 7f)
            }
            // Lid bar
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3f, 7f)
                lineTo(21f, 7f)
                moveTo(9f, 4f)
                lineTo(15f, 4f)
            }
            // Inner ribs
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(10f, 11f)
                lineTo(10f, 17f)
                moveTo(14f, 11f)
                lineTo(14f, 17f)
            }
        }.build()
    }

    // Undo: Circular counter-clockwise arc with arrow
    val Undo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Undo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 10f)
                lineTo(8f, 6f)
                moveTo(4f, 10f)
                lineTo(8f, 14f)
                moveTo(5f, 10f)
                lineTo(14f, 10f)
                curveTo(17.87f, 10f, 21f, 13.13f, 21f, 17f)
            }
        }.build()
    }

    // Redo: Circular clockwise arc with arrow
    val Redo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Redo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 10f)
                lineTo(16f, 6f)
                moveTo(20f, 10f)
                lineTo(16f, 14f)
                moveTo(19f, 10f)
                lineTo(10f, 10f)
                curveTo(6.13f, 10f, 3f, 13.13f, 3f, 17f)
            }
        }.build()
    }

    // Check: Bold crisp checkmark
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 12f)
                lineTo(9.5f, 17.5f)
                lineTo(20f, 6.5f)
            }
        }.build()
    }

    // Sparkles / Filters: Modern magic sparkles
    val Sparkles: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sparkles",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Main 4-point star
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3f)
                curveTo(12f, 7f, 14f, 9f, 18f, 9f)
                curveTo(14f, 9f, 12f, 11f, 12f, 15f)
                curveTo(12f, 11f, 10f, 9f, 6f, 9f)
                curveTo(10f, 9f, 12f, 7f, 12f, 3f)
                close()
            }
            // Secondary small star
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 14f)
                curveTo(19f, 16.5f, 20f, 17.5f, 22.5f, 17.5f)
                curveTo(20f, 17.5f, 19f, 18.5f, 19f, 21f)
                curveTo(19f, 18.5f, 18f, 17.5f, 15.5f, 17.5f)
                curveTo(18f, 17.5f, 19f, 16.5f, 19f, 14f)
                close()
            }
        }.build()
    }

    // Sliders / Adjustments: Dual tuning sliders
    val Sliders: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sliders",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                // Top track & knob
                moveTo(3f, 8f)
                lineTo(7f, 8f)
                moveTo(13f, 8f)
                lineTo(21f, 8f)
                moveTo(10f, 5f)
                lineTo(10f, 11f)

                // Bottom track & knob
                moveTo(3f, 16f)
                lineTo(12f, 16f)
                moveTo(18f, 16f)
                lineTo(21f, 16f)
                moveTo(15f, 13f)
                lineTo(15f, 19f)
            }
        }.build()
    }

    // Crop: Interlocking crop marks
    val Crop: ImageVector by lazy {
        ImageVector.Builder(
            name = "Crop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 2f)
                lineTo(6f, 18f)
                lineTo(22f, 18f)
                moveTo(18f, 22f)
                lineTo(18f, 6f)
                lineTo(2f, 6f)
            }
        }.build()
    }

    // Rotate: 90-degree curved arrow
    val Rotate: ImageVector by lazy {
        ImageVector.Builder(
            name = "Rotate",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(15.22f, 3f, 18.06f, 4.69f, 19.67f, 7.23f)
                moveTo(15f, 7f)
                lineTo(20f, 7f)
                lineTo(20f, 2f)
            }
        }.build()
    }

    // Backspace: Keypad erase chevron with x
    val Backspace: ImageVector by lazy {
        ImageVector.Builder(
            name = "Backspace",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 5f)
                lineTo(8f, 5f)
                lineTo(2.5f, 12f)
                lineTo(8f, 19f)
                lineTo(20f, 19f)
                curveTo(21.1f, 19f, 22f, 18.1f, 22f, 17f)
                lineTo(22f, 7f)
                curveTo(22f, 5.9f, 21.1f, 5f, 20f, 5f)
                close()
                moveTo(12f, 9f)
                lineTo(16f, 15f)
                moveTo(16f, 9f)
                lineTo(12f, 15f)
            }
        }.build()
    }

    // Play: Smooth video play triangle
    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(8f, 5.14f)
                curveTo(8f, 4.35f, 8.87f, 3.86f, 9.54f, 4.28f)
                lineTo(19.2f, 10.42f)
                curveTo(19.83f, 10.82f, 19.83f, 11.74f, 19.2f, 12.14f)
                lineTo(9.54f, 18.28f)
                curveTo(8.87f, 18.7f, 8f, 18.21f, 8f, 17.42f)
                close()
            }
        }.build()
    }

    // Pause: Dual vertical bars
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(8f, 5f)
                lineTo(8f, 19f)
                moveTo(16f, 5f)
                lineTo(16f, 19f)
            }
        }.build()
    }

    // Volume Up: Speaker with audio waves
    val VolumeUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "VolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11f, 5f)
                lineTo(6f, 9f)
                lineTo(2f, 9f)
                lineTo(2f, 15f)
                lineTo(6f, 15f)
                lineTo(11f, 19f)
                close()
                moveTo(15.5f, 8.5f)
                curveTo(16.5f, 9.5f, 17f, 10.7f, 17f, 12f)
                curveTo(17f, 13.3f, 16.5f, 14.5f, 15.5f, 15.5f)
            }
        }.build()
    }

    // Volume Mute: Speaker with cross
    val VolumeMute: ImageVector by lazy {
        ImageVector.Builder(
            name = "VolumeMute",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11f, 5f)
                lineTo(6f, 9f)
                lineTo(2f, 9f)
                lineTo(2f, 15f)
                lineTo(6f, 15f)
                lineTo(11f, 19f)
                close()
                moveTo(21f, 9f)
                lineTo(15f, 15f)
                moveTo(15f, 9f)
                lineTo(21f, 15f)
            }
        }.build()
    }

    // Location: Minimalist map pin
    val Location: ImageVector by lazy {
        ImageVector.Builder(
            name = "Location",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21.5f)
                curveTo(12f, 21.5f, 19f, 14.5f, 19f, 9.5f)
                curveTo(19f, 5.5f, 15.87f, 2.5f, 12f, 2.5f)
                curveTo(8.13f, 2.5f, 5f, 5.5f, 5f, 9.5f)
                curveTo(5f, 14.5f, 12f, 21.5f, 12f, 21.5f)
                close()
                moveTo(12f, 7.5f)
                curveTo(13.1f, 7.5f, 14f, 8.4f, 14f, 9.5f)
                curveTo(14f, 10.6f, 13.1f, 11.5f, 12f, 11.5f)
                curveTo(10.9f, 11.5f, 10f, 10.6f, 10f, 9.5f)
                curveTo(10f, 8.4f, 10.9f, 7.5f, 12f, 7.5f)
                close()
            }
        }.build()
    }

    // Google Lens visual search icon
    val Lens: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lens",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Optical brackets
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top-Left bracket
                moveTo(7f, 4f)
                lineTo(5f, 4f)
                curveTo(4.45f, 4f, 4f, 4.45f, 4f, 5f)
                lineTo(4f, 7f)

                // Top-Right bracket
                moveTo(17f, 4f)
                lineTo(19f, 4f)
                curveTo(19.55f, 4f, 20f, 4.45f, 20f, 5f)
                lineTo(20f, 7f)

                // Bottom-Left bracket
                moveTo(4f, 17f)
                lineTo(4f, 19f)
                curveTo(4f, 19.55f, 4.45f, 20f, 5f, 20f)
                lineTo(7f, 20f)

                // Bottom-Right bracket
                moveTo(20f, 17f)
                lineTo(20f, 19f)
                curveTo(20f, 19.55f, 19.55f, 20f, 19f, 20f)
                lineTo(17f, 20f)
            }
            // Center lens ring
            path(
                stroke = SolidColor(Color(0xFF00B4D8)),
                strokeLineWidth = 1.8f
            ) {
                moveTo(12f, 7.5f)
                curveTo(14.49f, 7.5f, 16.5f, 9.51f, 16.5f, 12f)
                curveTo(16.5f, 14.49f, 14.49f, 16.5f, 12f, 16.5f)
                curveTo(9.51f, 16.5f, 7.5f, 14.49f, 7.5f, 12f)
                curveTo(7.5f, 9.51f, 9.51f, 7.5f, 12f, 7.5f)
                close()
            }
            // Inner optic dot
            path(fill = SolidColor(Color(0xFF7928CA))) {
                moveTo(12f, 10.5f)
                curveTo(12.83f, 10.5f, 13.5f, 11.17f, 13.5f, 12f)
                curveTo(13.5f, 12.83f, 12.83f, 13.5f, 12f, 13.5f)
                curveTo(11.17f, 13.5f, 10.5f, 12.83f, 10.5f, 12f)
                curveTo(10.5f, 11.17f, 11.17f, 10.5f, 12f, 10.5f)
                close()
            }
        }.build()
    }

    // Wallpaper device framing icon
    val Wallpaper: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wallpaper",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 2f)
                lineTo(17f, 2f)
                curveTo(18.1f, 2f, 19f, 2.9f, 19f, 4f)
                lineTo(19f, 20f)
                curveTo(19f, 21.1f, 18.1f, 22f, 17f, 22f)
                lineTo(7f, 22f)
                curveTo(5.9f, 22f, 5f, 21.1f, 5f, 20f)
                lineTo(5f, 4f)
                curveTo(5f, 2.9f, 5.9f, 2f, 7f, 2f)
                close()
            }
            // Screen interior horizon
            path(
                stroke = SolidColor(Color(0xFF00B4D8)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 16f)
                lineTo(10.5f, 12.5f)
                lineTo(13f, 15f)
                lineTo(15f, 13f)
                lineTo(17f, 15f)
            }
        }.build()
    }

    // Shield Check (Privacy & Sanitization)
    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2.5f)
                lineTo(4f, 6f)
                lineTo(4f, 11.5f)
                curveTo(4f, 16.5f, 7.5f, 20.8f, 12f, 22f)
                curveTo(16.5f, 20.8f, 20f, 16.5f, 20f, 11.5f)
                lineTo(20f, 6f)
                lineTo(12f, 2.5f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF00B4D8)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 12f)
                lineTo(11f, 14f)
                lineTo(15f, 10f)
            }
        }.build()
    }

    // Sun (Light Mode)
    val Sun: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(stroke = SolidColor(Color(0xFFFFB703)), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                // Sun disk
                moveTo(12f, 7f)
                curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
                curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
                curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
                curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
                close()
                // Rays
                moveTo(12f, 2f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 22f)
                moveTo(2f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(22f, 12f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(4.93f, 19.07f); lineTo(6.34f, 17.66f)
                moveTo(17.66f, 6.34f); lineTo(19.07f, 4.93f)
            }
        }.build()
    }

    // Moon (Dark Mode)
    val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "Moon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF80D4FF)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 12.79f)
                curveTo(20.84f, 17.61f, 16.9f, 21.5f, 12.06f, 21.5f)
                curveTo(7.06f, 21.5f, 3f, 17.44f, 3f, 12.44f)
                curveTo(3f, 7.6f, 6.89f, 3.66f, 11.71f, 3.5f)
                curveTo(11.17f, 4.79f, 10.87f, 6.2f, 10.87f, 7.69f)
                curveTo(10.87f, 12.56f, 14.81f, 16.5f, 19.69f, 16.5f)
                curveTo(20.14f, 16.5f, 20.58f, 16.47f, 21f, 16.4f)
                close()
            }
        }.build()
    }

    // Auto Theme (Split circle)
    val AutoTheme: ImageVector by lazy {
        ImageVector.Builder(
            name = "AutoTheme",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outline circle
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
                moveTo(12f, 2f)
                curveTo(17.52f, 2f, 22f, 6.48f, 22f, 12f)
                curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
                curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
                curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
                close()
            }
            // Filled right half
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                lineTo(12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
            }
        }.build()
    }

    // More Horizontal (Three dots)
    val MoreHorizontal: ImageVector by lazy {
        ImageVector.Builder(
            name = "MoreHorizontal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(5f, 12f); curveTo(5.55f, 12f, 6f, 11.55f, 6f, 11f); curveTo(6f, 10.45f, 5.55f, 10f, 5f, 10f); curveTo(4.45f, 10f, 4f, 10.45f, 4f, 11f); curveTo(4f, 11.55f, 4.45f, 12f, 5f, 12f); close()
                moveTo(12f, 12f); curveTo(12.55f, 12f, 13f, 11.55f, 13f, 11f); curveTo(13f, 10.45f, 12.55f, 10f, 12f, 10f); curveTo(11.45f, 10f, 11f, 10.45f, 11f, 11f); curveTo(11f, 11.55f, 11.45f, 12f, 12f, 12f); close()
                moveTo(19f, 12f); curveTo(19.55f, 12f, 20f, 11.55f, 20f, 11f); curveTo(20f, 10.45f, 19.55f, 10f, 19f, 10f); curveTo(18.45f, 10f, 18f, 10.45f, 18f, 11f); curveTo(18f, 11.55f, 18.45f, 12f, 19f, 12f); close()
            }
        }.build()
    }

    private fun strokeIcon(name: String, width: Float = 2f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()

    // Broken image: picture frame split by a jagged tear
    val BrokenImage: ImageVector by lazy {
        strokeIcon("BrokenImage") {
            moveTo(4f, 13f); lineTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 11f)
            moveTo(20f, 15f); lineTo(20f, 19f); lineTo(4f, 19f); lineTo(4f, 17f)
            moveTo(4f, 13f); lineTo(8f, 11f); lineTo(11f, 14f); lineTo(15f, 10f); lineTo(20f, 13f)
            moveTo(4f, 17f); lineTo(9f, 15f); lineTo(12f, 18f); lineTo(16f, 14f); lineTo(20f, 15f)
        }
    }

    // Select all: rounded square with a check mark
    val SelectAll: ImageVector by lazy {
        strokeIcon("SelectAll") {
            moveTo(7f, 4f); lineTo(17f, 4f); curveTo(18.66f, 4f, 20f, 5.34f, 20f, 7f); lineTo(20f, 17f)
            curveTo(20f, 18.66f, 18.66f, 20f, 17f, 20f); lineTo(7f, 20f); curveTo(5.34f, 20f, 4f, 18.66f, 4f, 17f)
            lineTo(4f, 7f); curveTo(4f, 5.34f, 5.34f, 4f, 7f, 4f); close()
            moveTo(8.5f, 12.2f); lineTo(11f, 14.7f); lineTo(15.8f, 9.5f)
        }
    }

    // Key: used for "Change PIN"
    val Key: ImageVector by lazy {
        strokeIcon("Key") {
            moveTo(10.5f, 13.5f); curveTo(9.8f, 15.5f, 7.6f, 16.6f, 5.6f, 15.9f); curveTo(3.6f, 15.2f, 2.5f, 13f, 3.2f, 11f)
            curveTo(3.9f, 9f, 6.1f, 7.9f, 8.1f, 8.6f); curveTo(9.5f, 9.1f, 10.4f, 10.3f, 10.6f, 11.6f)
            lineTo(21f, 11.6f); lineTo(21f, 14.6f)
            moveTo(17.5f, 11.6f); lineTo(17.5f, 14f)
        }
    }

    // Padlock
    val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
            moveTo(8.5f, 11f); lineTo(8.5f, 8f); curveTo(8.5f, 6.07f, 10.07f, 4.5f, 12f, 4.5f)
            curveTo(13.93f, 4.5f, 15.5f, 6.07f, 15.5f, 8f); lineTo(15.5f, 11f)
            moveTo(12f, 14.5f); lineTo(12f, 16.5f)
        }
    }

    // Horizontal flip: mirrored triangles around a dashed axis
    val FlipHorizontal: ImageVector by lazy {
        strokeIcon("FlipHorizontal") {
            moveTo(12f, 3f); lineTo(12f, 5f); moveTo(12f, 8f); lineTo(12f, 10f)
            moveTo(12f, 13f); lineTo(12f, 15f); moveTo(12f, 18f); lineTo(12f, 21f)
            moveTo(9f, 6f); lineTo(3f, 18f); lineTo(9f, 18f); close()
            moveTo(15f, 6f); lineTo(21f, 18f); lineTo(15f, 18f); close()
        }
    }

    // Vertical flip
    val FlipVertical: ImageVector by lazy {
        strokeIcon("FlipVertical") {
            moveTo(3f, 12f); lineTo(5f, 12f); moveTo(8f, 12f); lineTo(10f, 12f)
            moveTo(13f, 12f); lineTo(15f, 12f); moveTo(18f, 12f); lineTo(21f, 12f)
            moveTo(6f, 9f); lineTo(18f, 3f); lineTo(18f, 9f); close()
            moveTo(6f, 15f); lineTo(18f, 21f); lineTo(18f, 15f); close()
        }
    }

    // Restore: arrow rising out of a tray
    val Restore: ImageVector by lazy {
        strokeIcon("Restore") {
            moveTo(12f, 15f); lineTo(12f, 4f)
            moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
            moveTo(4f, 14f); lineTo(4f, 19f); lineTo(20f, 19f); lineTo(20f, 14f)
        }
    }

    // Warning triangle
    val Warning: ImageVector by lazy {
        strokeIcon("Warning") {
            moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
            moveTo(12f, 10f); lineTo(12f, 14f)
            moveTo(12f, 17f); lineTo(12f, 17.2f)
        }
    }
}
