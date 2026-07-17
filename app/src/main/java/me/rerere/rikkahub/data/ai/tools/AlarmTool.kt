package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAlarmTool(context: Context): Tool = Tool(
    name = "set_alarm",
    description = "Set an alarm on the user's device through the system clock app.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("hour") {
                    put("type", "integer")
                    put("description", "Hour in 24-hour format (0-23).")
                }
                putJsonObject("action") {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("set"))
                        add(JsonPrimitive("dismiss"))
                    })
                    put("description", "Set a new alarm or dismiss an alarm previously created with the same label")
                }
                putJsonObject("minute") {
                    put("type", "integer")
                    put("description", "Minute (0-59).")
                }
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "A label/name for the alarm (optional)")
                }
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull ?: "set"
        val hour = params["hour"]?.jsonPrimitive?.content?.toIntOrNull()
        val minute = params["minute"]?.jsonPrimitive?.content?.toIntOrNull()
        val label = params["label"]?.jsonPrimitive?.content ?: ""

        if (action == "dismiss") {
            if (label.isBlank()) {
                return@Tool listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "label is required when dismissing an alarm")
                    }.toString()
                ))
            }
            return@Tool try {
                val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.queryIntentActivities(intent, 0).isNullOrEmpty()) {
                    listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", "No clock app found that supports dismissing alarms")
                        }.toString()
                    ))
                } else {
                    context.startActivity(intent)
                    listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("action", "dismiss")
                            put("label", label)
                        }.toString()
                    ))
                }
            } catch (e: Exception) {
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", e.message ?: "Failed to dismiss alarm")
                    }.toString()
                ))
            }
        }

        if (hour == null || minute == null) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", "Missing required parameters: hour and minute")
                }.toString()
            ))
        }

        if (hour !in 0..23 || minute !in 0..59) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", "Invalid time: hour must be 0-23, minute must be 0-59")
                }.toString()
            ))
        }

        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (label.isNotBlank()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val activities = context.packageManager.queryIntentActivities(intent, 0)
            if (activities.isNullOrEmpty()) {
                return@Tool listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "No clock app found that supports setting alarms")
                    }.toString()
                ))
            }

            context.startActivity(intent)

            val displayHour = String.format("%02d", hour)
            val displayMinute = String.format("%02d", minute)

            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("alarm_time", "$displayHour:$displayMinute")
                    put("label", label)
                    put("message", "Alarm set for $displayHour:$displayMinute${if (label.isNotBlank()) " ($label)" else ""}")
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", e.message ?: "Failed to set alarm")
                }.toString()
            ))
        }
    }
)
