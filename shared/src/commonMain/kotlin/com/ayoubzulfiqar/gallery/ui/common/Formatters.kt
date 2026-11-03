package com.ayoubzulfiqar.gallery.ui.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** "Tue, Nov 3, 2026 · 14:05" in the device time zone; "Unknown date" for missing timestamps. */
fun formatDateTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    if (epochMillis <= 0L) return "Unknown date"
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val weekday = dt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month = dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val time = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    return "$weekday, $month ${dt.dayOfMonth}, ${dt.year} · $time"
}

fun formatCoordinate(latitude: Double, longitude: Double): String {
    fun part(value: Double, positive: Char, negative: Char): String {
        val rounded = (abs(value) * 100_000).roundToLong() / 100_000.0
        return "$rounded° ${if (value >= 0) positive else negative}"
    }
    return "${part(latitude, 'N', 'S')}, ${part(longitude, 'E', 'W')}"
}

fun formatDecimal(value: Double, decimals: Int = 1): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val rounded = (value * factor).roundToLong() / factor
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

fun megapixels(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    val mp = width.toLong() * height / 1_000_000.0
    return "${formatDecimal(mp)} MP"
}
