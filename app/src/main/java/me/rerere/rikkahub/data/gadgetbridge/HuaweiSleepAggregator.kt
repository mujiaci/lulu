package me.rerere.rikkahub.data.gadgetbridge

internal data class HuaweiSleepStatsRow(
    val timestamp: Long,
    val bedTime: Long,
    val risingTime: Long,
    val wakeupTime: Long,
    val sleepScore: Int,
    val sleepLatency: Int,
    val sleepEfficiency: Int,
    val minHeartRate: Int,
    val maxHeartRate: Int,
    val minOxygenSaturation: Double,
    val maxOxygenSaturation: Double,
    val avgHrv: Int,
    val avgBreathRate: Int,
    val avgOxygenSaturation: Int,
    val avgHeartRate: Int,
    val wakeCount: Int,
    val turnOverCount: Int,
)

internal data class HuaweiSleepStagePoint(
    val timestamp: Long,
    val stage: Int,
)

internal fun buildHuaweiSleepSummary(
    stats: HuaweiSleepStatsRow,
    stages: List<HuaweiSleepStagePoint>,
): SleepSummary {
    val sleepStart = listOf(stats.bedTime, stats.timestamp)
        .filter { it > 0 }
        .minOrNull()
        ?: stats.timestamp
    val sleepEnd = listOf(stats.wakeupTime, stats.risingTime)
        .filter { it > sleepStart }
        .maxOrNull()
        ?: stats.wakeupTime

    val stageCounts = stages
        .filter { it.timestamp in sleepStart..sleepEnd }
        .groupingBy { it.stage }
        .eachCount()

    val totalDuration = if (sleepEnd > sleepStart) {
        ((sleepEnd - sleepStart) / 60_000L).toInt()
    } else {
        0
    }

    return SleepSummary(
        timestamp = sleepStart,
        wakeupTime = sleepEnd,
        totalDuration = totalDuration,
        deepSleep = stageCounts[2] ?: 0,
        lightSleep = stageCounts[1] ?: 0,
        remSleep = stageCounts[3] ?: 0,
        awakeDuration = stageCounts[4] ?: 0,
        isAwake = false,
        sleepScore = stats.sleepScore.toPositiveOrNull(),
        sleepLatency = stats.sleepLatency.toNonNegativeOrNull(),
        sleepEfficiency = stats.sleepEfficiency.toPositiveOrNull(),
        minHeartRate = stats.minHeartRate.toPositiveOrNull(),
        maxHeartRate = stats.maxHeartRate.toPositiveOrNull(),
        avgHeartRate = stats.avgHeartRate.toPositiveOrNull(),
        minOxygenSaturation = stats.minOxygenSaturation.toPositiveIntOrNull(),
        maxOxygenSaturation = stats.maxOxygenSaturation.toPositiveIntOrNull(),
        avgOxygenSaturation = stats.avgOxygenSaturation.toPositiveOrNull(),
        avgHrv = stats.avgHrv.toPositiveOrNull(),
        avgBreathRate = stats.avgBreathRate.toPositiveOrNull(),
        wakeCount = stats.wakeCount.toNonNegativeOrNull(),
        turnOverCount = stats.turnOverCount.toNonNegativeOrNull(),
        stageSampleCount = stages.size,
    )
}

private fun Int.toPositiveOrNull(): Int? = takeIf { it > 0 }

private fun Int.toNonNegativeOrNull(): Int? = takeIf { it >= 0 }

private fun Double.toPositiveIntOrNull(): Int? = takeIf { it > 0.0 }?.toInt()
