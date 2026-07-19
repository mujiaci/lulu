package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveCallSettingTest {
    @Test
    fun `overnight quiet hours block calls across midnight`() {
        val setting = ProactiveCallSetting(enabled = true, quietStartHour = 23, quietEndHour = 8)

        assertTrue(setting.isQuietHour(23))
        assertTrue(setting.isQuietHour(2))
        assertFalse(setting.isQuietHour(12))
    }

    @Test
    fun `cooldown blocks call channel even when frequency selector qualifies`() {
        val setting = ProactiveCallSetting(
            enabled = true,
            frequency = ProactiveCallFrequency.FREQUENT,
            minIntervalHours = 12,
            quietStartHour = 0,
            quietEndHour = 0,
        )

        assertFalse(
            shouldUseProactiveCallChannel(
                setting = setting,
                localHour = 12,
                millisSinceLastCall = 60L * 60L * 1_000L,
                selector = 0,
            ),
        )
    }

    @Test
    fun `frequency only changes delivery chance after persona chose contact`() {
        val elapsed = 24L * 60L * 60L * 1_000L
        val occasional = ProactiveCallSetting(
            enabled = true,
            frequency = ProactiveCallFrequency.OCCASIONAL,
            quietStartHour = 0,
            quietEndHour = 0,
        )
        val frequent = occasional.copy(frequency = ProactiveCallFrequency.FREQUENT)

        assertFalse(shouldUseProactiveCallChannel(occasional, 12, elapsed, selector = 25))
        assertTrue(shouldUseProactiveCallChannel(frequent, 12, elapsed, selector = 25))
    }
}
