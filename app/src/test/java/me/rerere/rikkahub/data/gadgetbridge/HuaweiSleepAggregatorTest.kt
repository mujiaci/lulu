package me.rerere.rikkahub.data.gadgetbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HuaweiSleepAggregatorTest {
    @Test
    fun huawei_sleep_uses_bed_time_and_stage_breakdown() {
        val stats = HuaweiSleepStatsRow(
            timestamp = 1782421860000L,
            bedTime = 1782421260000L,
            risingTime = 1782448320000L,
            wakeupTime = 1782448080000L,
            sleepScore = 84,
            sleepLatency = 10,
            sleepEfficiency = 96,
            minHeartRate = -1,
            maxHeartRate = -1,
            minOxygenSaturation = -1.0,
            maxOxygenSaturation = -1.0,
            avgHrv = 46,
            avgBreathRate = 15,
            avgOxygenSaturation = 96,
            avgHeartRate = 67,
            wakeCount = -1,
            turnOverCount = -1,
        )
        val stages =
            List(264) { HuaweiSleepStagePoint(1782421260000L + it * 60_000L, 1) } +
                List(78) { HuaweiSleepStagePoint(1782421260000L + (264 + it) * 60_000L, 2) } +
                List(91) { HuaweiSleepStagePoint(1782421260000L + (342 + it) * 60_000L, 3) } +
                List(18) { HuaweiSleepStagePoint(1782421260000L + (433 + it) * 60_000L, 4) }

        val summary = buildHuaweiSleepSummary(stats, stages)

        assertEquals(1782421260000L, summary.timestamp)
        assertEquals(1782448320000L, summary.wakeupTime)
        assertEquals(451, summary.totalDuration)
        assertEquals(78, summary.deepSleep)
        assertEquals(264, summary.lightSleep)
        assertEquals(91, summary.remSleep)
        assertEquals(18, summary.awakeDuration)
        assertEquals(84, summary.sleepScore)
        assertEquals(96, summary.sleepEfficiency)
        assertEquals(10, summary.sleepLatency)
        assertEquals(67, summary.avgHeartRate)
        assertEquals(96, summary.avgOxygenSaturation)
        assertEquals(451, summary.stageSampleCount)
    }
}
