package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

object LivingObservationToolRunner {
    fun canAutoObserve(toolName: String): Boolean {
        return normalizeToolName(toolName).isNotBlank()
    }

    @Suppress("UNUSED_PARAMETER")
    fun canAutoObserve(toolName: String, intent: LivingIntent): Boolean {
        return canAutoObserve(toolName)
    }

    fun resolveToolName(toolName: String): String = normalizeToolName(toolName)

    fun argumentsFor(toolName: String, intent: LivingIntent): JsonObject {
        return argumentsFor(
            toolName = toolName,
            intentKind = intent.kind,
            targetAtMillis = intent.targetAtMillis,
        )
    }

    fun argumentsFor(toolName: String, intentKind: LivingIntentKind): JsonObject {
        return argumentsFor(
            toolName = toolName,
            intentKind = intentKind,
            targetAtMillis = null,
        )
    }

    private fun argumentsFor(
        toolName: String,
        intentKind: LivingIntentKind,
        targetAtMillis: Long?,
    ): JsonObject {
        val normalized = normalizeToolName(toolName)
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
            "get_time_info" -> JsonObject(emptyMap())
            "set_alarm" -> buildJsonObject {
                val target = targetAtMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
                if (target != null) {
                    put("hour", target.hour)
                    put("minute", target.minute)
                    put("label", "提醒")
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
