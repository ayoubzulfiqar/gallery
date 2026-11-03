package com.ayoubzulfiqar.gallery.domain

import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaOrdering
import com.ayoubzulfiqar.gallery.domain.model.MediaScope
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.domain.model.PinPolicy
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.domain.model.formatDuration
import com.ayoubzulfiqar.gallery.domain.model.formatFileSize
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import com.ayoubzulfiqar.gallery.testutil.media
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainLogicTest {

    private fun utc(year: Int, month: Int, day: Int, hour: Int = 12) =
        LocalDateTime(year, month, day, hour, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

    private val now = utc(2026, 11, 3, 18)
    private val grouper = GroupMediaByTimelineUseCase(timeZone = { TimeZone.UTC }, now = { now })

    @Test
    fun groupsByDayWithRelativeLabelsAndUndatedLast() {
        val items = listOf(
            media("old", utc(2025, 1, 5)),
            media("today", utc(2026, 11, 3, 9)),
            media("undated", 0L),
            media("yesterday", utc(2026, 11, 2)),
            media("today2", utc(2026, 11, 3, 10))
        )
        val groups = grouper(items, TimelineDensity.DAYS)
        assertEquals(listOf("Today", "Yesterday", "Sun, Jan 5, 2025", "Unknown date"), groups.map { it.title })
        assertEquals(listOf("today2", "today"), groups[0].items.map { it.id }, "newest first within a day")
        assertEquals(GroupMediaByTimelineUseCase.UNDATED_KEY, groups.last().key)
    }

    @Test
    fun monthAndYearGroupingUsesStableKeys() {
        val items = listOf(media("a", utc(2026, 11, 1)), media("b", utc(2026, 10, 30)), media("c", utc(2024, 10, 30)))
        val months = grouper(items, TimelineDensity.MONTHS)
        assertEquals(listOf("November", "October", "October 2024"), months.map { it.title })
        assertEquals(3, months.map { it.key }.distinct().size, "same month name in different years must not merge")
        val years = grouper(items, TimelineDensity.YEARS)
        assertEquals(listOf("2026", "2024"), years.map { it.title })
        assertTrue(grouper(emptyList()).isEmpty())
    }

    @Test
    fun periodCacheGroupsExactlyLikeNaivePerItemConversionAcrossDst() {
        val zone = TimeZone.of("America/New_York")
        val fastGrouper = GroupMediaByTimelineUseCase(timeZone = { zone }, now = { now })
        // Every 37 minutes across the March 2026 DST switch plus month/year edges.
        val start = LocalDateTime(2025, 12, 30, 0, 0).toInstant(zone).toEpochMilliseconds()
        val items = (0 until 6000).map { media("i$it", start + it * 37L * 60_000L) }.sortedByDescending { it.dateTakenMillis }
        for (density in TimelineDensity.entries) {
            val groups = fastGrouper(items, density)
            groups.forEach { group ->
                val expectedKeys = group.items.map { item ->
                    val date = kotlinx.datetime.Instant.fromEpochMilliseconds(item.dateTakenMillis).toLocalDateTime(zone).date
                    when (density) {
                        TimelineDensity.YEARS -> "y-${date.year}"
                        TimelineDensity.MONTHS -> "m-${date.year}-${date.monthNumber}"
                        else -> "d-$date"
                    }
                }.toSet()
                assertEquals(setOf(group.key), expectedKeys, "density=$density group=${group.title}")
            }
            assertEquals(items.size, groups.sumOf { it.items.size })
            assertEquals(groups.size, groups.map { it.key }.toSet().size, "keys unique for $density")
        }
    }

    @Test
    fun photosThenVideosIsStableAndScopedToAll() {
        val items = listOf(
            media("v1", 900, mime = "video/mp4"), media("p1", 800), media("v2", 700, mime = "video/mp4"),
            media("p2", 600), media("p3", 500)
        )
        assertEquals(listOf("p1", "p2", "p3", "v1", "v2"), MediaScope.Library(MediaFilter.ALL).arrange(items).map { it.id })
        assertEquals(listOf("v1", "v2"), MediaScope.Library(MediaFilter.VIDEOS).arrange(items).map { it.id })
        val onlyPhotos = items.filter { !it.isVideo }
        assertTrue(MediaOrdering.photosThenVideos(onlyPhotos) === onlyPhotos, "no copy when nothing to reorder")
        assertEquals(
            listOf("v1", "p1", "v2"),
            MediaScope.Folder("bucket_camera").arrange(items.take(3)).map { it.id },
            "folders stay chronological"
        )
    }

    @Test
    fun mediaTypeClassification() {
        assertEquals(MediaType.VIDEO, MediaType.fromMimeType("video/mp4"))
        assertEquals(MediaType.GIF, MediaType.fromMimeType("image/gif"))
        assertEquals(MediaType.RAW, MediaType.fromMimeType("image/x-adobe-dng"))
        assertEquals(MediaType.IMAGE, MediaType.fromMimeType("image/heic"))
        assertEquals(MediaType.VIDEO, MediaType.fromMimeType(null, isVideoHint = true))
    }

    @Test
    fun orientationSwapsDisplayDimensions() {
        val portrait = media("p", width = 4000, height = 3000, orientation = 90)
        assertEquals(3000, portrait.displayWidth)
        assertEquals(4000, portrait.displayHeight)
        assertEquals(0.75f, portrait.aspectRatio)
        assertEquals(1f, media("unknown", width = 0, height = 0).aspectRatio)
    }

    @Test
    fun filtersAndScopes() {
        val photo = media("p")
        val video = media("v", mime = "video/mp4")
        val favorite = media("f").copy(isFavorite = true)
        assertTrue(MediaFilter.PHOTOS.matches(photo))
        assertFalse(MediaFilter.PHOTOS.matches(video))
        assertTrue(MediaFilter.VIDEOS.matches(video))
        assertTrue(MediaFilter.FAVORITES.matches(favorite))
        assertFalse(MediaFilter.FAVORITES.matches(photo))
        val folder = MediaScope.Folder("bucket_camera", MediaFilter.ALL)
        assertTrue(folder.includes(photo))
        assertFalse(folder.includes(media("s", bucket = "Screenshots")))
    }

    @Test
    fun pinPolicyRejectsWeakPins() {
        assertIs<PinPolicy.Validation.Valid>(PinPolicy.validateNewPin("2580"))
        assertIs<PinPolicy.Validation.Valid>(PinPolicy.validateNewPin("90210375"))
        listOf("1234", "4321", "0000", "123", "123456789", "12a4", "").forEach {
            assertIs<PinPolicy.Validation.Invalid>(PinPolicy.validateNewPin(it), it)
        }
    }

    @Test
    fun formatting() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("4.2 MB", formatFileSize(4_404_019))
        assertEquals("0:15", formatDuration(15_400))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("0:00", formatDuration(-5))
    }
}
