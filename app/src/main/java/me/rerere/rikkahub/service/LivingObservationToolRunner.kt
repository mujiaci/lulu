package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

object LivingObservationToolRunner {
    private val autoObservationTools = setOf(
        "get_battery_info",
        "get_app_usage",
        "today_study_plan",
        "today_schedule",
        "get_gadgetbridge_data",
        "calendar_tool",
        "get_weather",
        "get_location",
        "set_alarm",
    )

    fun canAutoObserve(toolName: String): Boolean {
        val normalized = normalizeToolName(toolName)
        return normalized in autoObservationTools && normalized != "set_alarm"
    }

    fun canAutoObserve(toolName: String, intent: LivingIntent): Boolean {
        val normalized = normalizeToolName(toolName)
        if (normalized == "set_alarm") {
            return intent.kind == LivingIntentKind.WAKE_UP && intent.targetAtMillis != null
        }
        return normalized in autoObservationTools
    }

    fun resolveToolName(toolName: String): String = normalizeToolName(toolName)

    fun argumentsFor(toolName: String, intent: LivingIntent): JsonObject {
        val normalized = normalizeToolName(toolName)
        return when (normalized) {
            "get_app_usage" -> buildJsonObject {
                put("limit", if (intent.kind == LivingIntentKind.STUDY_FOCUS) 5 else 3)
            }
            "get_gadgetbridge_data" -> buildJsonObject {
                put(
                    "data_type",
                    when (intent.kind) {
                        LivingIntentKind.HEALTH_SAFETY -> "all"
                        LivingIntentKind.WAKE_UP -> "sleep"
                        else -> "daily_summary"
                    },
                )
            }
            "calendar_tool" -> buildJsonObject {
                put("action", "read")
                put("limit", 5)
            }
            "get_location" -> buildJsonObject {
                put("include_address", true)
                put("force_refresh", intent.kind == LivingIntentKind.HEALTH_SAFETY)
            }
            "set_alarm" -> buildJsonObject {
                val target = intent.targetAtMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
                if (target != null) {
                    put("hour", target.hour)
                    put("minute", target.minute)
                    put("label", "露露提醒")
                }
            }
            else -> JsonObject(emptyMap())
        }
    }

    fun formatResult(toolName: String, outputs: List<String>): String {
        val compact = outputs
            .joinToString(" | ") { it.trim().replace(Regex("\\s+"), " ") }
            .take(900)
            .ifBlank { "empty" }
        return "tool_result[$toolName]=$compact"
    }

    fun formatFailure(toolName: String, error: Throwable): String =
        "tool_result[$toolName]=error:${error.message ?: error::class.simpleName.orEmpty()}"

    private fun normalizeToolName(toolName: String): String =
        when (val normalized = toolName.removePrefix("mcp__")) {
            "today_schedule" -> "today_study_plan"
            else -> normalized
        }
}
