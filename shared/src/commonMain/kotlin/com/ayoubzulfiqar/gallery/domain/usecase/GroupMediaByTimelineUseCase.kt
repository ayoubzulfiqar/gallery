package com.ayoubzulfiqar.gallery.domain.usecase

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GroupMediaByTimelineUseCase {
    operator fun invoke(items: List<MediaItem>): List<MediaTimelineGroup> {
        if (items.isEmpty()) return emptyList()

        val timeZone = TimeZone.currentSystemDefault()
        val sorted = items.sortedByDescending { it.dateTakenMillis }

        // Group by Year-Month-Day
        val groups = sorted.groupBy { item ->
            val instant = Instant.fromEpochMilliseconds(item.dateTakenMillis)
            val date = instant.toLocalDateTime(timeZone).date
            "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}, ${date.year}"
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
