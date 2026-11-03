package com.ayoubzulfiqar.gallery.domain.usecase

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.ui.gesture.TimelineDensity
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GroupMediaByTimelineUseCase {
    operator fun invoke(
        items: List<MediaItem>,
        density: TimelineDensity = TimelineDensity.MONTHS
    ): List<MediaTimelineGroup> {
        if (items.isEmpty()) return emptyList()

        val timeZone = TimeZone.currentSystemDefault()
        val sorted = items.sortedByDescending { it.dateTakenMillis }

        val groups = sorted.groupBy { item ->
            val instant = Instant.fromEpochMilliseconds(item.dateTakenMillis)
            val date = instant.toLocalDateTime(timeZone).date
            val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
            when (density) {
                TimelineDensity.YEARS -> "${date.year}"
                TimelineDensity.MONTHS -> "$monthName ${date.year}"
                TimelineDensity.DAYS, TimelineDensity.MASONRY -> "$monthName ${date.dayOfMonth}, ${date.year}"
            }
        }

        return groups.map { (title, groupItems) ->
            val minDate = groupItems.minOf { it.dateTakenMillis }
            val maxDate = groupItems.maxOf { it.dateTakenMillis }
            MediaTimelineGroup(
                title = title,
                dateRange = minDate..maxDate,
                items = groupItems
            )
        }
    }
}
