package com.ayoubzulfiqar.gallery.ui.theme

import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeTest {

    @Test
    fun defaultThemeModeIsSystem() = runTest {
        val store = LocalMetadataStore()
        assertEquals(ThemeMode.SYSTEM, store.themeMode.first())
    }

    @Test
    fun setThemeModeUpdatesFlow() = runTest {
        val store = LocalMetadataStore()
        store.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.themeMode.first())

        store.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, store.themeMode.first())
    }

    @Test
    fun cycleThemeModeCyclesThroughAllModes() = runTest {
        val store = LocalMetadataStore()
        assertEquals(ThemeMode.SYSTEM, store.themeMode.first())

        store.cycleThemeMode()
        assertEquals(ThemeMode.LIGHT, store.themeMode.first())

        store.cycleThemeMode()
        assertEquals(ThemeMode.DARK, store.themeMode.first())

        store.cycleThemeMode()
        assertEquals(ThemeMode.SYSTEM, store.themeMode.first())
    }
}
