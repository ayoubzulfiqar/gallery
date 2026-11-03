package com.ayoubzulfiqar.gallery.ui.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

class PinchDensityStateTest {
    @Test
    fun testPinchInCollapsesToMonthsAndYears() {
        val state = PinchDensityState(initialDensity = TimelineDensity.DAYS)
        assertEquals(3, state.currentDensity.columns)

        // Pinching in (scale < 1.0)
        state.onPinch(scaleDelta = 0.7f)
        assertEquals(TimelineDensity.MONTHS, state.calculateSnapTarget())

        state.onPinch(scaleDelta = 0.4f)
        assertEquals(TimelineDensity.YEARS, state.calculateSnapTarget())
    }

    @Test
    fun testPinchOutExpandsToMasonry() {
        val state = PinchDensityState(initialDensity = TimelineDensity.DAYS)
        state.onPinch(scaleDelta = 1.6f)
        assertEquals(TimelineDensity.MASONRY, state.calculateSnapTarget())
    }
}
