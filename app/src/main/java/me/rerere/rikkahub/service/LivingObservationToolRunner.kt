package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object LivingObservationToolRunner {
    private val autoObservationTools = setOf(
        "get_battery_info",
        "get_app_usage",
        "today_study_plan",
        "get_gadgetbridge_data",
        "calendar_tool",
        "get_weather",
        "get_location",
    )

    fun canAutoObserve(toolName: String): Boolean {
        val normalized = toolName.removePrefix("mcp__")
        return normalized in autoObservationTools
    }

    fun argumentsFor(toolName: String, intentKind: LivingIntentKind): JsonObject {
        val normalized = toolName.removePrefix("mcp__")
        return when (normalized) {
            "get_app_usage" -> buildJsonObject {
                put("limit", if (intentKind == LivingIntentKind.STUDY_FOCUS) 5 else 3)
            }
            "get_gadgetbridge_data" -> buildJsonObject {
                put(
                    "data_type",
                    when (intentKind) {
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
                put("force_refresh", intentKind == LivingIntentKind.HEALTH_SAFETY)
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
}
