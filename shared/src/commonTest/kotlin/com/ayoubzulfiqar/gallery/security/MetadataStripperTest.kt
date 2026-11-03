package com.ayoubzulfiqar.gallery.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetadataStripperTest {

    private val stripper = FakeMetadataStripper()

    @Test
    fun stripImageMetadataForShareCreatesCleanTempFile() = runTest {
        val result = stripper.stripImageMetadata("content://media/images/42", inPlace = false)
        assertTrue(result.removedGps)
        assertTrue(result.isTempShareFile)
        assertTrue(result.strippedTagsCount > 0)
        assertEquals("content://media/images/42", stripper.lastStrippedUri)
        assertFalse(stripper.lastInPlace)
    }

    @Test
    fun stripImageMetadataInPlaceKeepsOriginalUri() = runTest {
        val result = stripper.stripImageMetadata("content://media/images/42", inPlace = true)
        assertTrue(result.removedGps)
        assertFalse(result.isTempShareFile)
        assertEquals("content://media/images/42", result.cleanUri)
        assertTrue(stripper.lastInPlace)
    }

    @Test
    fun stripVideoMetadataRemovesGpsAtoms() = runTest {
        val result = stripper.stripVideoMetadata("content://media/video/99", inPlace = false)
        assertTrue(result.removedGps)
        assertTrue(result.isTempShareFile)
        assertEquals("content://media/video/99", stripper.lastStrippedUri)
    }
}
