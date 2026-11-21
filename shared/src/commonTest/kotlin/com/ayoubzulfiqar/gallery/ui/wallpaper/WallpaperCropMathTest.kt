package com.ayoubzulfiqar.gallery.ui.wallpaper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression suite for the wallpaper crop window. The OEM crop-and-set activity on some
 * devices discards the user's alignment for wide images and applies Rect(0, 0, ...) — the
 * leftmost slice. The in-app cropper must default to the CENTER and honor pan/zoom exactly.
 * Fixture numbers are the real device case: a 4500x2250 panorama, 1266x2250 wallpaper frame.
 */
class WallpaperCropMathTest {
    private val imgW = 4500
    private val imgH = 2250
    private val frameW = 1266f
    private val frameH = 2250f

    @Test
    fun wideImageDefaultsToTheCenterNotTheLeftEdge() {
        val rect = WallpaperCropMath.sourceRect(imgW, imgH, frameW, frameH, zoom = 1f, panX = 0f, panY = 0f)
        assertEquals(1617, rect.x) // (4500 - 1266) / 2 — the broken OEM path applied x = 0
        assertEquals(0, rect.y)
        assertEquals(1266, rect.width)
        assertEquals(2250, rect.height)
    }

    @Test
    fun draggingRightMovesTheWindowLeft() {
        val rect = WallpaperCropMath.sourceRect(imgW, imgH, frameW, frameH, 1f, panX = 500f, panY = 0f)
        assertEquals(1117, rect.x)
    }

    @Test
    fun panClampsAtBothEdges() {
        val left = WallpaperCropMath.sourceRect(imgW, imgH, frameW, frameH, 1f, panX = 100000f, panY = 0f)
        val right = WallpaperCropMath.sourceRect(imgW, imgH, frameW, frameH, 1f, panX = -100000f, panY = 0f)
        assertEquals(0, left.x)
        assertEquals(imgW - 1266, right.x)
    }

    @Test
    fun zoomShrinksTheVisibleWindow() {
        val rect = WallpaperCropMath.sourceRect(imgW, imgH, frameW, frameH, zoom = 2f, panX = 0f, panY = 0f)
        assertEquals(633, rect.width)
        assertEquals(1125, rect.height)
        assertTrue(rect.y > 0)
        assertTrue(rect.x + rect.width <= imgW)
    }

    @Test
    fun tallImageClampsVertically() {
        val top = WallpaperCropMath.sourceRect(1000, 4000, 1000f, 2000f, 1f, 0f, 100000f)
        assertEquals(0, top.y)
        assertEquals(2000, top.height)
        val bottom = WallpaperCropMath.sourceRect(1000, 4000, 1000f, 2000f, 1f, 0f, -100000f)
        assertEquals(2000, bottom.y)
    }

    @Test
    fun clampPanLimitsMatchTheImageSlack() {
        val (px, py) = WallpaperCropMath.clampPan(imgW, imgH, frameW, frameH, 1f, 99999f, 99999f)
        assertEquals((imgW - frameW) / 2f, px)
        assertEquals(0f, py)
    }

    @Test
    fun rectAlwaysStaysInsideTheImage() {
        for (zoom in listOf(1f, 1.37f, 2f, 5.5f)) {
            for (pan in listOf(-99999f, -321f, 0f, 123f, 99999f)) {
                val r = WallpaperCropMath.sourceRect(imgW, imgH, frameW, frameH, zoom, pan, pan)
                assertTrue(r.x >= 0 && r.y >= 0, "negative origin at zoom=$zoom pan=$pan: $r")
                assertTrue(r.x + r.width <= imgW && r.y + r.height <= imgH, "out of bounds at zoom=$zoom pan=$pan: $r")
                assertTrue(r.width > 0 && r.height > 0)
            }
        }
    }
}