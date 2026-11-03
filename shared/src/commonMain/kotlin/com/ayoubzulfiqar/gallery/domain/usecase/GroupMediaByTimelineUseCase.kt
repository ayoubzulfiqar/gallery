package com.ayoubzulfiqar.gallery.domain.usecase

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.core.time.currentTimeMillis
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Groups media into timeline sections, newest first. Items without a usable timestamp are
 * collected in a final "Unknown date" section instead of appearing as January 1970.
 */
class GroupMediaByTimelineUseCase(
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val now: () -> Long = ::currentTimeMillis
) {
    /**
     * @param keyPrefix namespaces group keys so independently grouped lists (photos and videos)
     * can be shown in one grid without key collisions.
     */
    operator fun invoke(
        items: List<MediaItem>,
        density: TimelineDensity = TimelineDensity.MONTHS,
        keyPrefix: String = ""
    ): List<MediaTimelineGroup> {
        if (items.isEmpty()) return emptyList()

        val zone = timeZone()
        val today = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date
        val yesterday = today.minus(DatePeriod(days = 1))
        val sorted = if (items.isSortedNewestFirst()) items else items.sortedByDescending { it.dateTakenMillis }

        val buckets = LinkedHashMap<String, MutableList<MediaItem>>()
        val titles = HashMap<String, String>()
        val undated = mutableListOf<MediaItem>()

        // Items arrive newest first, so consecutive items usually share a period: cache the current
        // period's [start, end) instant range and only convert dates when an item falls outside it.
        var periodStart = Long.MAX_VALUE
        var periodEnd = Long.MIN_VALUE
        var bucket: MutableList<MediaItem>? = null

        for (item in sorted) {
            val millis = item.dateTakenMillis
            if (millis <= 0L) {
                undated += item
                continue
            }
            if (bucket == null || millis < periodStart || millis >= periodEnd) {
                val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
                val first = when (density) {
                    TimelineDensity.YEARS -> LocalDate(date.year, 1, 1)
                    TimelineDensity.MONTHS -> LocalDate(date.year, date.month, 1)
                    TimelineDensity.DAYS, TimelineDensity.MASONRY -> date
                }
                val next = when (density) {
                    TimelineDensity.YEARS -> first.plus(DatePeriod(years = 1))
                    TimelineDensity.MONTHS -> first.plus(DatePeriod(months = 1))
                    TimelineDensity.DAYS, TimelineDensity.MASONRY -> first.plus(DatePeriod(days = 1))
                }
                periodStart = first.atStartOfDayIn(zone).toEpochMilliseconds()
                periodEnd = next.atStartOfDayIn(zone).toEpochMilliseconds()
                val key = keyPrefix + when (density) {
                    TimelineDensity.YEARS -> "y-${date.year}"
                    TimelineDensity.MONTHS -> "m-${date.year}-${date.monthNumber}"
                    TimelineDensity.DAYS, TimelineDensity.MASONRY -> "d-$date"
                }
                bucket = buckets.getOrPut(key) {
                    titles[key] = title(date, density, today, yesterday)
                    mutableListOf()
                }
            }
            bucket.add(item)
        }

        val groups = buckets.map { (key, groupItems) ->
            MediaTimelineGroup(
                key = key,
                title = titles.getValue(key),
                dateRange = groupItems.last().dateTakenMillis..groupItems.first().dateTakenMillis,
                items = groupItems
            )
        }
        return if (undated.isEmpty()) groups else groups + MediaTimelineGroup(
            key = keyPrefix + UNDATED_KEY,
            title = "Unknown date",
            dateRange = 0L..0L,
            items = undated
        )
    }

    private fun title(date: LocalDate, density: TimelineDensity, today: LocalDate, yesterday: LocalDate): String {
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return when (density) {
            TimelineDensity.YEARS -> date.year.toString()
            TimelineDensity.MONTHS -> if (date.year == today.year) month else "$month ${date.year}"
            TimelineDensity.DAYS, TimelineDensity.MASONRY -> when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> {
                    val weekday = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    val shortMonth = month.take(3)
                    if (date.year == today.year) "$weekday, $shortMonth ${date.dayOfMonth}"
                    else "$weekday, $shortMonth ${date.dayOfMonth}, ${date.year}"
                }
            }
        }
    }

    private fun List<MediaItem>.isSortedNewestFirst(): Boolean {
        for (i in 1 until size) if (this[i - 1].dateTakenMillis < this[i].dateTakenMillis) return false
        return true
    }

    companion object {
        const val UNDATED_KEY = "undated"
    }
}
