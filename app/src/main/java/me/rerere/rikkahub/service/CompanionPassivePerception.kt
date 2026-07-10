package me.rerere.rikkahub.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.passivePerceptionTools
import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.utils.JsonInstant

internal fun companionPassivePerceptionToolNames(): List<String> = listOf(
    "get_time_info",
    "get_battery_info",
    "get_app_usage",
    "get_gadgetbridge_data",
    "get_notifications",
    "get_location",
    "get_weather",
)

internal suspend fun collectCompanionPassivePerceptionFacts(
    tools: List<Tool>,
    observedAt: Long,
): List<CompanionContextFact> = coroutineScope {
    val requestedNames = companionPassivePerceptionToolNames().toSet()
    tools.passivePerceptionTools()
        .filter { it.name in requestedNames }
        .map { tool ->
            async(Dispatchers.IO) {
                val output = withTimeoutOrNull(PASSIVE_PERCEPTION_TIMEOUT_MILLIS) {
                    runCatching {
                        val args = JsonInstant.parseToJsonElement(passivePerceptionArguments(tool.name))
                        tool.execute(args)
                            .filterIsInstance<UIMessagePart.Text>()
                            .joinToString("\n") { it.text }
                            .trim()
                    }.getOrNull()
                } ?: return@async null
                output.takeIf(String::isNotBlank)?.let { value ->
                    CompanionContextFact(
                        key = "perception.${tool.name}",
                        value = value,
                        observedAt = observedAt,
                    )
                }
            }
        }
        .awaitAll()
        .filterNotNull()
}

private fun passivePerceptionArguments(toolName: String): String = when (toolName) {
    "get_notifications" -> """{"limit":5}"""
    "get_app_usage" -> """{"limit":8}"""
    "get_gadgetbridge_data" -> """{"data_type":"daily_summary"}"""
    "get_location" -> """{"include_address":true,"force_refresh":false}"""
    "get_weather" -> """{"force_refresh_location":false}"""
    else -> "{}"
}

private const val PASSIVE_PERCEPTION_TIMEOUT_MILLIS = 3_000L
