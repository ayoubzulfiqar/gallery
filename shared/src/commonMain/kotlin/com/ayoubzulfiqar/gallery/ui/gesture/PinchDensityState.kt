package com.ayoubzulfiqar.gallery.ui.gesture

enum class TimelineDensity(val columns: Int) {
    YEARS(8),
    MONTHS(5),
    DAYS(3),
    MASONRY(1)
}

class PinchDensityState(initialDensity: TimelineDensity = TimelineDensity.DAYS) {
    var currentDensity: TimelineDensity = initialDensity
        private set

    var scaleFactor: Float = 1.0f
        private set

    fun onPinch(scaleDelta: Float) {
        scaleFactor = (scaleFactor * scaleDelta).coerceIn(0.2f, 3.0f)
    }

    fun calculateSnapTarget(): TimelineDensity {
        return when (currentDensity) {
            TimelineDensity.DAYS -> {
                when {
                    scaleFactor <= 0.5f -> TimelineDensity.YEARS
                    scaleFactor <= 0.85f -> TimelineDensity.MONTHS
                    scaleFactor >= 1.4f -> TimelineDensity.MASONRY
                    else -> TimelineDensity.DAYS
                }
            }
            TimelineDensity.MONTHS -> {
                when {
                    scaleFactor <= 0.6f -> TimelineDensity.YEARS
                    scaleFactor >= 1.3f -> TimelineDensity.DAYS
                    else -> TimelineDensity.MONTHS
                }
            }
            TimelineDensity.YEARS -> {
                if (scaleFactor >= 1.3f) TimelineDensity.MONTHS else TimelineDensity.YEARS
            }
            TimelineDensity.MASONRY -> {
                if (scaleFactor <= 0.75f) TimelineDensity.DAYS else TimelineDensity.MASONRY
            }
        }
    }

    fun commitSnap(target: TimelineDensity = calculateSnapTarget()) {
        currentDensity = target
        scaleFactor = 1.0f
    }

    fun reset() {
        scaleFactor = 1.0f
    }
}
