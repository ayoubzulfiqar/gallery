package com.ayoubzulfiqar.gallery.data

import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.data.local.InMemoryKeyValueStore
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.testutil.TestGraph
import com.ayoubzulfiqar.gallery.testutil.media
import com.ayoubzulfiqar.gallery.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataLayerTest {

    @Test
    fun localMetadataSurvivesRestart() {
        val kv = InMemoryKeyValueStore()
        LocalMetadataStore(kv).apply {
            toggleFavorite("a")
            setFavorite(listOf("b", "c"), favorite = true)
            setThemeMode(ThemeMode.DARK)
            setTimelineDensity(TimelineDensity.YEARS)
            addTag("a", "  Travel\u0007 ")
            addTag("a", "Beach")
        }
        val restarted = LocalMetadataStore(kv)
        assertEquals(setOf("a", "b", "c"), restarted.favorites.value)
        assertEquals(ThemeMode.DARK, restarted.themeMode.value)
        assertEquals(TimelineDensity.YEARS, restarted.timelineDensity.value)
        assertEquals(setOf("Travel", "Beach"), restarted.getTags("a"))

        restarted.forget(listOf("a", "b"))
        val again = LocalMetadataStore(kv)
        assertEquals(setOf("c"), again.favorites.value)
        assertTrue(again.getTags("a").isEmpty())
    }

    @Test
    fun themeCyclesThroughAllModes() {
        val store = LocalMetadataStore()
        assertEquals(ThemeMode.SYSTEM, store.themeMode.value)
        store.cycleThemeMode()
        assertEquals(ThemeMode.LIGHT, store.themeMode.value)
        store.cycleThemeMode()
        assertEquals(ThemeMode.DARK, store.themeMode.value)
        store.cycleThemeMode()
        assertEquals(ThemeMode.SYSTEM, store.themeMode.value)
    }

    @Test
    fun repositoryAppliesFavoritesSortsAndHides() = runTest {
        val graph = TestGraph(this, listOf(media("old", 100), media("new", 300), media("mid", 200)))
        val repo = graph.repository
        assertEquals(listOf("new", "mid", "old"), repo.library.first { it.isLoaded }.items.map { it.id })

        repo.toggleFavorite("mid")
        assertTrue(repo.library.first { s -> s.items.any { it.isFavorite } }.items.first { it.id == "mid" }.isFavorite)

        repo.hide(listOf("new"))
        assertEquals(listOf("mid", "old"), repo.library.first { s -> s.items.none { it.id == "new" } }.items.map { it.id })
        repo.unhide(listOf("new"))
        assertEquals(3, repo.library.first { it.items.size == 3 }.items.size)
    }

    @Test
    fun deletionReportsAndForgetsMetadata() = runTest {
        val graph = TestGraph(this, listOf(media("a"), media("b")))
        val repo = graph.repository
        repo.toggleFavorite("a")
        val items = repo.library.first { it.isLoaded }.items

        graph.source.cancelNextDelete = true
        val cancelled = repo.deleteMedia(items.filter { it.id == "a" }, DeleteMode.TRASH)
        assertTrue(cancelled.cancelled)
        assertTrue(cancelled.deletedIds.isEmpty())
        assertTrue(graph.store.isFavorite("a"), "nothing changes when the user declines")

        val result = repo.deleteMedia(items.filter { it.id == "a" }, DeleteMode.TRASH)
        assertEquals(setOf("a"), result.deletedIds)
        assertFalse(graph.store.isFavorite("a"))
        assertEquals(listOf("b"), repo.library.first { it.items.size == 1 }.items.map { it.id })
    }

    @Test
    fun foldersArePrioritizedAndCounted() = runTest {
        val graph = TestGraph(
            this,
            listOf(
                media("w1", 900, bucket = "WhatsApp"),
                media("c1", 100, bucket = "Camera"),
                media("s1", 50, bucket = "Screenshots"),
                media("c2", 200, bucket = "Camera")
            )
        )
        val folders = graph.repository.folders.first { it.isNotEmpty() }
        assertEquals(listOf("Camera", "Screenshots", "WhatsApp"), folders.map { it.name })
        assertEquals(2, folders.first().itemCount)
        assertEquals("demo://media/c2", folders.first().coverUri, "cover is the newest item")
    }
}
