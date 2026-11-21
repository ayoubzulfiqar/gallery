package com.ayoubzulfiqar.gallery.ui.wallpaper

import com.ayoubzulfiqar.gallery.domain.model.WallpaperCropRect
import kotlin.math.roundToInt

/**
 * Pure crop-window math for the in-app wallpaper cropper.
 *
 * Model: the image is shown at "cover" scale inside a fixed frame (the screen aspect), so at
 * zoom = 1 the frame already shows the largest centered region of the image that fits. The
 * user pans (view pixels) and zooms (multiplier >= 1) to choose the exact region. Dragging the
 * image right moves the visible window left, matching the gesture.
 *
 * This replaces the system crop-and-set intent, whose OEM implementation on some devices
 * discards the aligned position for wide images and applies the leftmost slice (crop hint
 * Rect(0, 0, ...)). Here the default is the CENTER and every pan/zoom maps to an exact,
 * clamped source rect — verified by WallpaperCropMathTest.
 */
object WallpaperCropMath {

    /** Scale at which the image exactly covers the frame (ContentScale.Crop semantics). */
    fun coverScale(imgW: Int, imgH: Int, frameW: Float, frameH: Float): Float =
        maxOf(frameW / imgW, frameH / imgH)

    /** Clamps a raw pan so the frame never shows area outside the image. */
    fun clampPan(
        imgW: Int,
        imgH: Int,
        frameW: Float,
        frameH: Float,
        zoom: Float,
        panX: Float,
        panY: Float
    ): Pair<Float, Float> {
        val scale = coverScale(imgW, imgH, frameW, frameH) * zoom.coerceAtLeast(1f)
        val slackX = ((imgW * scale - frameW) / 2f).coerceAtLeast(0f)
        val slackY = ((imgH * scale - frameH) / 2f).coerceAtLeast(0f)
        return panX.coerceIn(-slackX, slackX) to panY.coerceIn(-slackY, slackY)
    }

    /** The exact source region visible through the frame for the given zoom/pan. */
    fun sourceRect(
        imgW: Int,
        imgH: Int,
        frameW: Float,
        frameH: Float,
        zoom: Float,
        panX: Float,
        panY: Float
    ): WallpaperCropRect {
        val z = zoom.coerceAtLeast(1f)
        val scale = coverScale(imgW, imgH, frameW, frameH) * z
        val (px, py) = clampPan(imgW, imgH, frameW, frameH, z, panX, panY)
        val visW = frameW / scale
        val visH = frameH / scale
        val cx = imgW / 2f - px / scale
        val cy = imgH / 2f - py / scale
        val w = visW.roundToInt().coerceIn(1, imgW)
        val h = visH.roundToInt().coerceIn(1, imgH)
        // Rounding must never push the rect outside the image.
        val x = (cx - visW / 2f).roundToInt().coerceIn(0, imgW - w)
        val y = (cy - visH / 2f).roundToInt().coerceIn(0, imgH - h)
        return WallpaperCropRect(x, y, w, h)
    }
}