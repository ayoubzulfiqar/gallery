package com.ayoubzulfiqar.gallery.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import com.ayoubzulfiqar.gallery.data.datasource.ImportResult
import com.ayoubzulfiqar.gallery.data.datasource.MediaImportRequest
import com.ayoubzulfiqar.gallery.data.datasource.PlatformMediaDataSource
import com.ayoubzulfiqar.gallery.di.EditedImageExporter
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.ui.common.AndroidBitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Renders an [EditRecipe] at high resolution with the same operation order as the on-screen preview
 * (crop → flip → rotate → colour matrix → vignette) and saves it as a new JPEG next to the original.
 * The original file is never modified.
 */
class AndroidEditedImageExporter(
    private val context: Context,
    private val mediaSource: PlatformMediaDataSource
) : EditedImageExporter {

    override suspend fun export(item: MediaItem, recipe: EditRecipe): ImportResult {
        val rendered = withContext(Dispatchers.Default) { render(item, recipe) }
            ?: return ImportResult.Failure("Couldn't open the photo for editing")
        return try {
            val baseName = item.displayName.substringBeforeLast('.')
            mediaSource.importMedia(
                MediaImportRequest(
                    displayName = "${baseName}_edited.jpg",
                    mimeType = "image/jpeg",
                    mediaType = MediaType.IMAGE,
                    // Sort right next to the original in the timeline.
                    dateTakenMillis = if (item.dateTakenMillis > 0) item.dateTakenMillis + 1 else System.currentTimeMillis(),
                    relativePath = item.relativePath
                )
            ) { sink ->
                val stream = object : OutputStream() {
                    override fun write(b: Int) = sink.write(byteArrayOf(b.toByte()))
                    override fun write(b: ByteArray, off: Int, len: Int) = sink.write(b, off, len)
                }
                check(rendered.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) { "JPEG encoding failed" }
            }
        } finally {
            rendered.recycle()
        }
    }

    private fun render(item: MediaItem, recipe: EditRecipe): Bitmap? {
        val source = AndroidBitmaps.decodeImage(context.contentResolver, Uri.parse(item.uri), MAX_EXPORT_DIMENSION, software = true)
            ?: return null
        try {
            val crop = recipe.cropRect
            val left = ((crop?.left ?: 0f) * source.width).roundToInt()
            val top = ((crop?.top ?: 0f) * source.height).roundToInt()
            val width = (((crop?.right ?: 1f) * source.width).roundToInt() - left).coerceAtLeast(1)
            val height = (((crop?.bottom ?: 1f) * source.height).roundToInt() - top).coerceAtLeast(1)
            val sideways = EditRenderMath.isSideways(recipe.rotationDegrees)
            val outWidth = if (sideways) height else width
            val outHeight = if (sideways) width else height

            val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            EditRenderMath.colorMatrix(recipe)?.let { paint.colorFilter = ColorMatrixColorFilter(it) }

            canvas.save()
            canvas.translate(outWidth / 2f, outHeight / 2f)
            canvas.rotate(recipe.rotationDegrees)
            canvas.scale(if (recipe.flipHorizontal) -1f else 1f, if (recipe.flipVertical) -1f else 1f)
            canvas.drawBitmap(
                source,
                Rect(left, top, left + width, top + height),
                RectF(-width / 2f, -height / 2f, width / 2f, height / 2f),
                paint
            )
            canvas.restore()

            val edgeAlpha = EditRenderMath.vignetteEdgeAlpha(recipe)
            if (edgeAlpha > 0f) {
                val radius = hypot(outWidth.toFloat(), outHeight.toFloat()) / 2f
                val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        outWidth / 2f, outHeight / 2f, radius,
                        intArrayOf(0x00000000, 0x00000000, ((edgeAlpha * 255).roundToInt() shl 24)),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, outWidth.toFloat(), outHeight.toFloat(), vignette)
            }
            return output
        } finally {
            source.recycle()
        }
    }

    private companion object {
        const val MAX_EXPORT_DIMENSION = 6144
        const val JPEG_QUALITY = 95
    }
}
