package me.rerere.rikkahub.data.service

import android.location.Location
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationServiceTest {
    @Test
    fun `last known location is fresh only within ten minutes`() {
        val now = 1_000_000_000L

        assertTrue(Location("test").apply {
            time = now - 9 * 60 * 1000L
        }.isFreshEnough(now))

        assertFalse(Location("test").apply {
            time = now - 11 * 60 * 1000L
        }.isFreshEnough(now))
    }

    @Test
    fun `cached location is skipped when force refresh is requested`() {
        assertTrue(shouldUseCachedLocation(forceRefresh = false))
        assertFalse(shouldUseCachedLocation(forceRefresh = true))
    }
}
