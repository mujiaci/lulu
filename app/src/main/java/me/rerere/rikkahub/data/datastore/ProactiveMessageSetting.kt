package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class ProactiveMessageSetting(
    val enabled: Boolean = false,
    val naturalScheduling: Boolean = true,
    val minIntervalMinutes: Int = 30,
    val maxIntervalMinutes: Int = 90,
    val assistantId: String = "",
)

fun ProactiveMessageSetting.forAssistant(assistantId: String): ProactiveMessageSetting =
    copy(assistantId = assistantId)


@Serializable
data class ProactiveCallSetting(
    val enabled: Boolean = false,
    val frequency: ProactiveCallFrequency = ProactiveCallFrequency.NORMAL,
    val minIntervalHours: Int = 12,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 8,
    val allowMobileData: Boolean = true,
    val fullScreenWhenAllowed: Boolean = true,
    val ringtoneUri: String? = null,
)

@Serializable
enum class ProactiveCallFrequency {
    OCCASIONAL,
    NORMAL,
    FREQUENT,
}

internal fun ProactiveCallSetting.isQuietHour(hour: Int): Boolean {
    val start = quietStartHour.coerceIn(0, 23)
    val end = quietEndHour.coerceIn(0, 23)
    return when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }
}

internal fun shouldUseProactiveCallChannel(
    setting: ProactiveCallSetting,
    localHour: Int,
    millisSinceLastCall: Long,
    selector: Int,
): Boolean {
    if (!setting.enabled || setting.isQuietHour(localHour)) return false
    if (millisSinceLastCall < setting.minIntervalHours.coerceAtLeast(1) * 60L * 60L * 1_000L) return false
    val chancePercent = when (setting.frequency) {
        ProactiveCallFrequency.OCCASIONAL -> 15
        ProactiveCallFrequency.NORMAL -> 30
        ProactiveCallFrequency.FREQUENT -> 50
    }
    return selector.coerceIn(0, 99) < chancePercent
}
