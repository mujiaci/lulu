package me.rerere.rikkahub.data.gadgetbridge

import java.time.LocalDate

data class DailySummary(
    val timestamp: Long,
    val date: LocalDate,
    val steps: Int,
    val hrResting: Int?,
    val hrMax: Int?,
    val hrMin: Int?,
    val hrAvg: Int?,
    val stressAvg: Int?,
    val calories: Int?,
    val spo2Avg: Int?,
)

data class ActivitySample(
    val timestamp: Long,
    val heartRate: Int?,
    val steps: Int?,
    val stress: Int?,
    val spo2: Int?,
    val rawIntensity: Int?,
)

data class SleepSummary(
    val timestamp: Long,
    val wakeupTime: Long,
    val totalDuration: Int,
    val deepSleep: Int,
    val lightSleep: Int,
    val remSleep: Int,
    val awakeDuration: Int,
    val isAwake: Boolean,
    val sleepScore: Int? = null,
    val sleepLatency: Int? = null,
    val sleepEfficiency: Int? = null,
    val minHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val minOxygenSaturation: Int? = null,
    val maxOxygenSaturation: Int? = null,
    val avgOxygenSaturation: Int? = null,
    val avgHrv: Int? = null,
    val avgBreathRate: Int? = null,
    val wakeCount: Int? = null,
    val turnOverCount: Int? = null,
    val stageSampleCount: Int = 0,
) {
    val isNap: Boolean get() = isAwake && deepSleep == 0 && lightSleep == 0 && remSleep == 0
}

data class HealthUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dbFileExists: Boolean = true,
    val currentHeartRate: Int? = null,
    val dailySummaries7: List<DailySummary> = emptyList(),
    val dailySummaries30: List<DailySummary> = emptyList(),
    val sleepSummaries: List<SleepSummary> = emptyList(),
    val latestSpo2: Int? = null,
    val latestStress: Int? = null,
    val todaySteps: Int = 0,
    val todayCalories: Int? = null,
    val stepsRange: StepsRange = StepsRange.SEVEN_DAYS,
)

enum class StepsRange {
    SEVEN_DAYS,
    THIRTY_DAYS,
}
