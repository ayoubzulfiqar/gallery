package com.ayoubzulfiqar.gallery.editor

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments

object ColorMatrixHelper {

    fun identityMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    fun buildColorMatrix(adjustments: ColorAdjustments): FloatArray {
        var current = identityMatrix()

        // 1. Contrast & Brightness
        if (adjustments.contrast != 1f || adjustments.brightness != 0f) {
            val c = adjustments.contrast
            val b = adjustments.brightness * 255f
            val translate = (1f - c) * 128f + b
            val cbMatrix = floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            current = multiply(current, cbMatrix)
        }

        // 2. Saturation
        if (adjustments.saturation != 1f) {
            val s = adjustments.saturation
            val lr = 0.213f * (1f - s)
            val lg = 0.715f * (1f - s)
            val lb = 0.072f * (1f - s)
            val satMatrix = floatArrayOf(
                lr + s, lg, lb, 0f, 0f,
                lr, lg + s, lb, 0f, 0f,
                lr, lg, lb + s, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            current = multiply(current, satMatrix)
        }

        // 3. Warmth
        if (adjustments.warmth != 0f) {
            val w = adjustments.warmth * 30f
            val warmthMatrix = floatArrayOf(
                1f, 0f, 0f, 0f, w,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -w,
                0f, 0f, 0f, 1f, 0f
            )
            current = multiply(current, warmthMatrix)
        }

        return current
    }

    fun multiply(m1: FloatArray, m2: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += m1[i * 5 + k] * m2[k * 5 + j]
                }
                result[i * 5 + j] = sum
            }
            // Add translation column
            result[i * 5 + 4] = m1[i * 5 + 0] * m2[4] +
                    m1[i * 5 + 1] * m2[9] +
                    m1[i * 5 + 2] * m2[14] +
                    m1[i * 5 + 3] * m2[19] +
                    m1[i * 5 + 4]
        }
        return result
    }
}
