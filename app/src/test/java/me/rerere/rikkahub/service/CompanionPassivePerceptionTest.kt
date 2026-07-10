package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPassivePerceptionTest {
    @Test
    fun `ordinary turns automatically include weather and location sensing`() {
        val names = companionPassivePerceptionToolNames()

        assertTrue("get_weather" in names)
        assertTrue("get_location" in names)
        assertTrue("get_time_info" in names)
        assertTrue("get_app_usage" in names)
    }

    @Test
    fun `automatic passive sensing order is stable`() {
        assertEquals(
            listOf(
                "get_time_info",
                "get_battery_info",
                "get_app_usage",
                "get_gadgetbridge_data",
                "get_notifications",
                "get_location",
                "get_weather",
            ),
            companionPassivePerceptionToolNames(),
        )
    }
}
