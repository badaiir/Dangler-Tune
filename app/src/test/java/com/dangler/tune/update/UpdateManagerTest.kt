package com.dangler.tune.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateManager.isNewer("v1.10", "1.2"))
        assertTrue(UpdateManager.isNewer("1.13", "1.12.9"))
        assertTrue(UpdateManager.isNewer("2.0", "1.99"))
        assertTrue(UpdateManager.isNewer("1.13.1", "1.13"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(UpdateManager.isNewer("1.13", "v1.13"))
        assertFalse(UpdateManager.isNewer("1.13.0", "1.13"))
        assertFalse(UpdateManager.isNewer("1.12", "1.13"))
        assertFalse(UpdateManager.isNewer("1.2", "1.10"))
    }

    @Test
    fun versionCodeFromTagMatchesReleaseWorkflow() {
        assertEquals(11300, UpdateManager.versionCodeFromTag("v1.13"))
        assertEquals(11302, UpdateManager.versionCodeFromTag("1.13.2"))
        assertEquals(20000, UpdateManager.versionCodeFromTag("v2.0"))
        assertEquals(10100, UpdateManager.versionCodeFromTag("1.1"))
    }

    @Test
    fun normalizeStripsV() {
        assertEquals("1.13", UpdateManager.normalize(" v1.13 "))
        assertEquals("1.13", UpdateManager.normalize("V1.13"))
    }
}
