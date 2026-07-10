package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.cihai.CihaiService
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.LocalTimeContextFormatter
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.core.context.GlobalContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.io.File

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("sms")
    data object Sms : LocalToolOption()

    @Serializable
    @SerialName("calendar")
    data object Calendar : LocalToolOption()

    @Serializable
    @SerialName("lulu_journal")
    data object LuluJournal : LocalToolOption()

    @Serializable
    @SerialName("lulu_expression")
    data object LuluExpression : LocalToolOption()

    @Serializable
    @SerialName("allow_skip_reply")
    data object AllowSkipReply : LocalToolOption()
}

class LocalTools(private val context: Context) {
    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = """
                Execute JavaScript code using QuickJS engine (ES2020).
                The result is the value of the last expression in the code.
                For calculations with decimals, use toFixed() to control precision.
                Console output (log/info/warn/error) is captured and returned in 'logs' field.
                No DOM or Node.js APIs available.
                Example: '1 + 2' returns 3; 'const x = 5; x * 2' returns 10.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                    required = listOf("code")
                )
            },
            execute = {
                val logs = arrayListOf<String>()
                val context = QuickJSContext.create()
                context.setConsole(object : QuickJSContext.Console {
                    override fun log(info: String?) {
                        logs.add("[LOG] $info")
                    }

                    override fun info(info: String?) {
                        logs.add("[INFO] $info")
                    }

                    override fun warn(info: String?) {
                        logs.add("[WARN] $info")
                    }

                    override fun error(info: String?) {
                        logs.add("[ERROR] $info")
                    }
                })
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                val payload = buildJsonObject {
                    if (logs.isNotEmpty()) {
                        put("logs", JsonPrimitive(logs.joinToString("\n")))
                    }
                    put(
                        key = "result",
                        element = when (result) {
                            null -> JsonNull
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val timeTool by lazy {
        Tool(
            name = "get_time_info",
            description = """
                Get the current local date and time info from the device.
                Returns year/month/day, weekday, ISO date/time strings, timezone, and timestamp.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                val now = ZonedDateTime.now()
                val date = now.toLocalDate()
                val time = now.toLocalTime().withNano(0)
                val weekday = now.dayOfWeek
                val payload = buildJsonObject {
                    put("year", date.year)
                    put("month", date.monthValue)
                    put("day", date.dayOfMonth)
                    put("weekday", weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    put("weekday_en", weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    put("weekday_index", weekday.value)
                    put("date", date.toString())
                    put("time", time.toString())
                    put("time_24h", time.toString())
                    put("period_zh", LocalTimeContextFormatter.periodLabel(now.hour))
                    put("local_time_text", LocalTimeContextFormatter.format(nowMillis = now.toInstant().toEpochMilli(), zoneId = now.zone))
                    put("time_instruction", "Use time_24h as 24-hour local time. 00:00-04:59 is 凌晨, not 下午.")
                    put("datetime", now.withNano(0).toString())
                    put("timezone", now.zone.id)
                    put("utc_offset", now.offset.id)
                    put("timestamp_ms", now.toInstant().toEpochMilli())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val clipboardTool by lazy {
        Tool(
            name = "clipboard_tool",
            description = """
                Read or write plain text from the device clipboard.
                Use action: read or write. For write, provide text.
                Do NOT write to the clipboard unless the user has explicitly requested it.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                kotlinx.serialization.json.buildJsonArray {
                                    add("read")
                                    add("write")
                                }
                            )
                            put("description", "Operation to perform: read or write")
                        })
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "Text to write to the clipboard (required for write)")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
                when (action) {
                    "read" -> {
                        val payload = buildJsonObject {
                            put("text", context.readClipboardText())
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    "write" -> {
                        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                        context.writeClipboardText(text)
                        val payload = buildJsonObject {
                            put("success", true)
                            put("text", text)
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    else -> error("unknown action: $action, must be one of [read, write]")
                }
            }
        )
    }

    val ttsTool by lazy {
        Tool(
            name = "text_to_speech",
            description = """
                Prepare text for the user's manual chat playback button.
                Use this only when the user asks for spoken audio or when a short voice clip is clearly appropriate.
                The tool does not auto-play audio; it creates a replayable voice bar in the chat UI.
                Provide natural, readable text without markdown formatting, and do not repeat the same text again in the assistant reply.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The text to speak aloud")
                        })
                    },
                    required = listOf("text")
                )
            },
            execute = {
                val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: error("text is required")
                val payload = buildJsonObject {
                    put("success", true)
                    put("queued", false)
                    put("text", text)
                    put("message", "Audio is ready in the chat playback control. Do not repeat the same text in the assistant reply.")
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val askUserTool by lazy {
        Tool(
            name = "ask_user",
            description = """
                Ask the user one or more questions when you need clarification, additional information, or confirmation.
                Each question can optionally provide a list of suggested options for the user to choose from.
                The user may select an option or provide their own free-text answer for each question.
                The answers will be returned as a JSON object mapping question IDs to the user's responses.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("questions", buildJsonObject {
                            put("type", "array")
                            put("description", "List of questions to ask the user")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("id", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Unique identifier for this question")
                                    })
                                    put("question", buildJsonObject {
                                        put("type", "string")
                                        put("description", "The question text to display to the user")
                                    })
                                    put("options", buildJsonObject {
                                        put("type", "array")
                                        put(
                                            "description",
                                            "Optional list of suggested options for the user to choose from"
                                        )
                                        put("items", buildJsonObject {
                                            put("type", "string")
                                        })
                                    })
                                    put("selection_type", buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "enum",
                                            kotlinx.serialization.json.buildJsonArray {
                                                add("text")
                                                add("single")
                                                add("multi")
                                            }
                                        )
                                        put(
                                            "description",
                                            "Answer type: text (free text input, default), single (select exactly one option), multi (select one or more options)"
                                        )
                                    })
                                })
                                put("required", kotlinx.serialization.json.buildJsonArray {
                                    add("id")
                                    add("question")
                                })
                            })
                        })
                    },
                    required = listOf("questions")
                )
            },
            needsApproval = true,
            execute = {
                error("ask_user tool should be handled by HITL flow")
            }
        )
    }

    val luluJournalTool by lazy {
        Tool(
            name = "write_lulu_journal",
            description = """
                Write a visible Cihai diary entry for the character.
                Use when the character wants to record a first-person diary about real feelings, private thoughts, and what was not said aloud.
                This is the only path that saves a formal Lulu diary entry; background perception PASS/WAIT should not use it just to record silence.
                Before calling, compare with the recent diary/inner-journal context available in the prompt, especially the latest 3 entries, and only write if this turn adds new perception, a new change, or a new judgment.
                Write in character and first person. Let the length follow the real thought: do not pad, summarize mechanically, or force a fixed word count.
                Do not write detached third-person notes, field labels, or internal trace dumps.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("title", buildJsonObject {
                            put("type", "string")
                            put("description", "Short entry title")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "First-person in-character journal content to save")
                        })
                        put("mood", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional mood or feeling label")
                        })
                    },
                    required = listOf("content")
                )
            },
            execute = {
                val params = it.jsonObject
                val content = params["content"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: error("content is required")
                require(content.isNotBlank()) { "content is blank" }
                val now = ZonedDateTime.now()
                val cihaiSaved = runCatching {
                    val koin = GlobalContext.get()
                    val settings = koin.get<SettingsStore>().settingsFlow.value
                    val assistant = settings.getCurrentAssistant()
                    koin.get<CihaiService>().addEntry(
                        CihaiEntry(
                            assistantId = assistant.id.toString(),
                            kind = CihaiEntryKind.DIARY,
                            title = params["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "辞海日记" },
                            content = content,
                            emotion = params["mood"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            createdAt = now.toInstant().toEpochMilli(),
                        )
                    )
                }.isSuccess
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", cihaiSaved)
                            put("cihai_saved", cihaiSaved)
                            put("message", "Cihai diary saved")
                        }.toString()
                    )
                )
            },
        )
    }

    val luluExpressionTool by lazy {
        Tool(
            name = "set_lulu_expression_state",
            description = """
                Record the character's current presence for this turn.
                Use when a reply should carry embodied presence. Prefer one complete natural
                Chinese paragraph describing current visible state, behavior, action, posture, and tags.
                Always include inner_voice when possible: write what the character is thinking but
                not saying aloud, in first person, in character, without labels or prompt text.
                Use thought for a short memory of the same unspoken feeling.
                Keep it for the status/heart panel below chat; do not put parenthesized action in the main spoken reply.
                This records intent only; do not claim that the real avatar file has changed.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "One natural Chinese paragraph for the status strip, not parenthesized, merging state, behavior, action, posture, and tags")
                        })
                        put("emoji", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional short mood hint")
                        })
                        put("sticker", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional short action hint")
                        })
                        put("gesture", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional posture/body-language hint")
                        })
                        put("inner_voice", buildJsonObject {
                            put("type", "string")
                            put("description", "Private first-person Chinese inner voice: what I am thinking but not saying aloud, in character, no labels or prompt text")
                        })
                        put("thought", buildJsonObject {
                            put("type", "string")
                            put("description", "Short first-person in-character unspoken thought to remember from this turn, no labels or prompt text")
                        })
                        put("intensity", buildJsonObject {
                            put("type", "number")
                            put("description", "0.0 to 1.0 expression intensity")
                        })
                    }
                )
            },
            execute = {
                val params = it.jsonObject
                val now = ZonedDateTime.now()
                val expression = buildJsonObject {
                    put("created_at", now.toString())
                    put("timestamp_ms", now.toInstant().toEpochMilli())
                    put("description", params["description"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("emoji", params["emoji"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("sticker", params["sticker"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("gesture", params["gesture"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("inner_voice", params["inner_voice"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("thought", params["thought"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("intensity", params["intensity"]?.jsonPrimitive?.doubleOrNull ?: 0.5)
                }
                val luluDir = File(context.filesDir, "lulu").apply { mkdirs() }
                val expressionFile = File(luluDir, "lulu_expression_state.jsonl")
                expressionFile.appendText(expression.toString() + "\n")
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("path", expressionFile.absolutePath)
                            put("message", "Expression state recorded")
                        }.toString()
                    )
                )
            },
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.Sms)) {
            tools.add(createSmsTool(context))
        }
        if (options.contains(LocalToolOption.Calendar)) {
            tools.add(createCalendarTool(context))
        }
        if (options.contains(LocalToolOption.LuluJournal)) {
            tools.add(luluJournalTool)
        }
        if (options.contains(LocalToolOption.LuluExpression) || options.contains(LocalToolOption.TimeInfo)) {
            tools.add(luluExpressionTool)
        }
        return tools
    }
}
