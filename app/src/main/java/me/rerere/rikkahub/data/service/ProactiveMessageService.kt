package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.app.NotificationCompat
import android.os.Build
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.LuluExpressionOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.SystemTools
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.deduplicateByToolName
import me.rerere.rikkahub.data.ai.tools.withHumanLikeToolPrompts
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.plugin.provider.PluginToolProvider
import me.rerere.rikkahub.data.repository.MemoryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.LuluThoughtCategory
import me.rerere.rikkahub.data.model.currentProjectedLuluState
import me.rerere.rikkahub.data.model.thoughtHistory
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.LuluIntent
import me.rerere.rikkahub.service.LuluIntentInput
import me.rerere.rikkahub.service.LuluIntentModelPlanner
import me.rerere.rikkahub.service.LuluIntentPlan
import me.rerere.rikkahub.service.LuluIntentPlanner
import me.rerere.rikkahub.service.ProactiveReminderPlan
import me.rerere.rikkahub.service.toProactiveReminderPlan
import me.rerere.rikkahub.utils.sendNotification
import java.time.Instant
import kotlin.uuid.Uuid
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ProactiveMessageService : KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()

    companion object {
        const val TAG = "ProactiveMessageService"
        const val ACTION_PROACTIVE_MESSAGE = "me.rerere.rikkahub.PROACTIVE_MESSAGE"
        private const val REQUEST_CODE = 10001
        private const val TARGETED_REQUEST_CODE = 10002

        internal const val PREFS_NAME = "proactive_message_prefs"
        private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
        internal const val KEY_TARGETED_TRIGGER_TIME = "targeted_trigger_time"
        internal const val KEY_TARGETED_REASON = "targeted_reason"
        internal const val KEY_TARGETED_USER_TEXT = "targeted_user_text"
        internal const val KEY_TARGETED_KIND = "targeted_kind"
        internal const val KEY_TARGETED_QUEUE = "targeted_queue"
        internal const val EXTRA_TARGETED_REASON = "targeted_reason"
        internal const val EXTRA_TARGETED_USER_TEXT = "targeted_user_text"
        internal const val EXTRA_TARGETED_KIND = "targeted_kind"

        fun scheduleNext(context: Context, setting: ProactiveMessageSetting) {
            if (!setting.enabled) {
                cancel(context)
                return
            }

            val minMinutes = setting.minIntervalMinutes.coerceAtLeast(1)
            val maxMinutes = setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
            val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)
            val triggerTime = java.lang.System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())

            // 保存下次触发时间到SharedPreferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerTime)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 12+ needs canScheduleExactAlarms() check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        // Fallback: use inexact alarm if exact alarm permission not granted
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            Log.d(TAG, "Scheduled proactive message in $delayMinutes minutes, trigger at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")

            // Also schedule via WorkManager as a more reliable fallback
            ProactiveMessageWorker.scheduleNext(context, setting)
        }

        fun scheduleNext(
            context: Context,
            settings: Settings,
            minutesSinceLastChat: Long? = null,
        ) {
            val setting = settings.proactiveMessageSetting
            if (!setting.enabled) {
                cancel(context)
                return
            }
            val assistant = settings.assistants.find { it.id.toString() == setting.assistantId }
                ?: settings.getCurrentAssistant()
            val nowMillis = java.lang.System.currentTimeMillis()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastTriggeredTime = prefs.getLong("last_triggered_time", 0L)
            val activeTargetedTrigger = prefs.getLong(KEY_TARGETED_TRIGGER_TIME, 0L)
            val pulseInput = LuluAutonomousPulseInput(
                setting = setting,
                state = settings.luluStates.currentProjectedLuluState(assistant.id, nowMillis),
                minutesSinceLastChat = minutesSinceLastChat
                    ?: lastTriggeredTime
                        .takeIf { it > 0L }
                        ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L) }
                    ?: Long.MAX_VALUE,
                pendingThoughtCount = settings.luluThoughts
                    .thoughtHistory(assistant.id, nowMillis)
                    .count { !it.expressed },
                activeTargetedTriggerMillis = activeTargetedTrigger,
                nowMillis = nowMillis,
            )
            val pulsePlan = LuluAutonomousPulsePlanner.planNext(pulseInput)
            val triggerTime = LuluAutonomousPulsePlanner.triggerTimeMillis(pulseInput, pulsePlan)
            scheduleAt(context, setting, triggerTime, pulsePlan.reason)
            ProactiveMessageWorker.scheduleNext(context, setting, pulsePlan.delayMinutes)
        }

        private fun scheduleAt(
            context: Context,
            setting: ProactiveMessageSetting,
            triggerAtMillis: Long,
            logReason: String,
        ) {
            if (!setting.enabled || triggerAtMillis <= java.lang.System.currentTimeMillis()) return

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerAtMillis)
                .putString("next_trigger_reason", logReason)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }

            Log.d(TAG, "Scheduled autonomous proactive pulse reason=$logReason at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAtMillis))}")
        }

        fun scheduleTargeted(
            context: Context,
            setting: ProactiveMessageSetting,
            triggerAtMillis: Long,
            reason: String,
            userText: String,
            kind: String,
        ) {
            if (!setting.enabled || triggerAtMillis <= java.lang.System.currentTimeMillis()) return

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerAtMillis)
                .putLong(KEY_TARGETED_TRIGGER_TIME, triggerAtMillis)
                .putString(KEY_TARGETED_REASON, reason)
                .putString(KEY_TARGETED_USER_TEXT, userText)
                .putString(KEY_TARGETED_KIND, kind)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
                putExtra(EXTRA_TARGETED_REASON, reason)
                putExtra(EXTRA_TARGETED_USER_TEXT, userText)
                putExtra(EXTRA_TARGETED_KIND, kind)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                TARGETED_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }

            Log.d(TAG, "Scheduled targeted proactive message kind=$kind at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAtMillis))}")
        }

        fun replaceTargetedQueue(
            context: Context,
            setting: ProactiveMessageSetting,
            plans: List<ProactiveReminderPlan>,
        ) {
            clearTargetedQueue(context)
            val upcoming = plans
                .filter { it.triggerAtMillis > java.lang.System.currentTimeMillis() }
                .sortedBy { it.triggerAtMillis }
                .take(5)
            if (!setting.enabled || upcoming.isEmpty()) return

            val queue = JsonArray(upcoming.map { plan ->
                buildJsonObject {
                    put("triggerAtMillis", JsonPrimitive(plan.triggerAtMillis))
                    put("reason", JsonPrimitive(plan.toQueuedTargetedReason()))
                    put("userText", JsonPrimitive(plan.userText))
                    put("kind", JsonPrimitive(plan.kind.name.lowercase(java.util.Locale.ROOT)))
                }
            })
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TARGETED_QUEUE, queue.toString())
                .apply()
            scheduleTargeted(
                context = context,
                setting = setting,
                triggerAtMillis = upcoming.first().triggerAtMillis,
                reason = upcoming.first().toQueuedTargetedReason(),
                userText = upcoming.first().userText,
                kind = upcoming.first().kind.name.lowercase(java.util.Locale.ROOT),
            )
        }

        private fun ProactiveReminderPlan.toQueuedTargetedReason(): String = buildString {
            appendLine(reason)
            if (preferredToolNames.isNotEmpty()) {
                appendLine("At trigger time, check these sensing tools first if available: ${preferredToolNames.joinToString(", ")}.")
            }
            appendLine("This is only a proactive-plan reason. Do not treat it as prewritten message text; generate the actual user-facing message fresh.")
        }.trim()

        fun clearTargetedQueue(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TARGETED_TRIGGER_TIME)
                .remove(KEY_TARGETED_REASON)
                .remove(KEY_TARGETED_USER_TEXT)
                .remove(KEY_TARGETED_KIND)
                .remove(KEY_TARGETED_QUEUE)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                TARGETED_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }

        internal fun popCurrentTargetedAndScheduleNext(
            context: Context,
            setting: ProactiveMessageSetting,
        ): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = java.lang.System.currentTimeMillis()
            val queue = runCatching {
                (Json.parseToJsonElement(prefs.getString(KEY_TARGETED_QUEUE, "[]").orEmpty()) as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
            }.getOrNull().orEmpty()
            val remaining = queue
                .drop(1)
                .filter { item ->
                    (item["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L) > now
                }
            val next = remaining.minByOrNull {
                it["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: Long.MAX_VALUE
            }
            if (next == null) {
                clearTargetedQueue(context)
                return false
            }

            prefs.edit()
                .putString(KEY_TARGETED_QUEUE, JsonArray(remaining).toString())
                .apply()
            scheduleTargeted(
                context = context,
                setting = setting,
                triggerAtMillis = next["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return false,
                reason = next["reason"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                userText = next["userText"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                kind = next["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            return true
        }

        fun getNextTriggerTime(context: Context): Long? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val triggerTime = prefs.getLong(KEY_NEXT_TRIGGER_TIME, 0L)
            return if (triggerTime > 0) triggerTime else null
        }

        fun cancel(context: Context) {
            // 清除保存的触发时间
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NEXT_TRIGGER_TIME)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                Log.d(TAG, "Cancelled proactive message alarm")
            }

            // Also cancel WorkManager fallback
            ProactiveMessageWorker.cancel(context)
        }

        fun resetTimer(context: Context, setting: ProactiveMessageSetting) {
            scheduleNext(context, setting)
        }

        fun triggerNow(context: Context, setting: ProactiveMessageSetting) {
            // 先安排下一次（写入SP让UI立即显示），再立即触发
            scheduleNext(context, setting)
            // 立即触发：直接启动TriggerService
            val serviceIntent = Intent(context, ProactiveMessageTriggerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }

    suspend fun buildProactiveContext(
        context: Context,
        settings: Settings,
        targetedReason: String? = null,
        targetedUserText: String? = null,
        targetedKind: String? = null,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("[主动消息上下文]")
        if (!targetedReason.isNullOrBlank()) {
            sb.appendLine("这次不是随机主动消息，而是露露刚才自己决定稍后回来确认的一次目标消息。")
            sb.appendLine("目标类型: ${targetedKind.orEmpty()}")
            sb.appendLine("触发原因: $targetedReason")
            sb.appendLine("生成回复前，如果触发原因里列出了感知工具或后续动作，请优先按那些线索主动查看状态，再自然地开口。")
            sb.appendLine("如果用户之前已经把提醒、记录、闹钟或日程意图说得很明确，可以主动完成对应工具动作；不要机械等用户再次确认。")
            sb.append(buildTargetedProactiveSensingInstruction(targetedKind, targetedReason))
            if (!targetedUserText.isNullOrBlank()) {
                sb.appendLine("当时用户说过: $targetedUserText")
            }
        }

        // Time since last chat
        try {
            val lastMessageTime = getLastMessageTime()
            if (lastMessageTime != null) {
                val nowMs = java.lang.System.currentTimeMillis()
                val lastMs = lastMessageTime.toEpochMilliseconds()
                val diffMs = nowMs - lastMs
                val duration = diffMs.milliseconds
                val minutesAgo = duration.inWholeMinutes
                val hoursAgo = duration.inWholeHours
                when {
                    hoursAgo > 24 -> sb.appendLine("距离上次聊天: ${hoursAgo / 24}天${hoursAgo % 24}小时")
                    hoursAgo > 0 -> sb.appendLine("距离上次聊天: ${hoursAgo}小时${minutesAgo % 60}分钟")
                    else -> sb.appendLine("距离上次聊天: ${minutesAgo}分钟")
                }
            } else {
                sb.appendLine("距离上次聊天: 很久没有聊天了")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get last message time", e)
        }

        // Current time
        val currentTime = java.lang.System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sb.appendLine("当前时间: ${sdf.format(java.util.Date(currentTime))}")

        // Battery context
        try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (batteryPercent != null) {
                sb.appendLine("设备电量: $batteryPercent%${if (isCharging) "，正在充电" else ""}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get battery context", e)
        }

        // Health context
        try {
            val gadgetbridgePath = settings.systemToolsSetting.gadgetbridgeDbPath
            if (settings.systemToolsSetting.gadgetbridgeEnabled && GadgetbridgeReader.dbFileExists(gadgetbridgePath)) {
                val latestActivity = GadgetbridgeReader.readLatestActivitySample(gadgetbridgePath)
                val latestDailySummary = GadgetbridgeReader.readDailySummaries(1, gadgetbridgePath).lastOrNull()
                val latestSleep = GadgetbridgeReader.readSleepSummaries(1, gadgetbridgePath).lastOrNull()
                val healthParts = buildList {
                    latestSleep?.totalDuration?.let { sleepMinutes ->
                        add("睡眠约${sleepMinutes / 60}小时${sleepMinutes % 60}分钟")
                    }
                    (latestActivity?.heartRate ?: latestDailySummary?.hrAvg)?.let { heartRate ->
                        add("心率约${heartRate}")
                    }
                    (latestDailySummary?.steps ?: latestActivity?.steps)?.let { steps ->
                        add("步数约${steps}")
                    }
                }
                if (healthParts.isNotEmpty()) {
                    sb.appendLine("健康状态: ${healthParts.joinToString("，")}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get health context", e)
        }

        // Location context
        try {
            val amapApiKey = settings.systemToolsSetting.amapApiKey
            if (amapApiKey.isNotBlank()) {
                val amapService = AmapService(amapApiKey)
                val locationService = LocationService(context, amapService)
                val locationResult = locationService.getCurrentLocation(amapApiKey)
                if (locationResult.isSuccess) {
                    val location = locationResult.getOrThrow()
                    if (location.address.isNotBlank()) {
                        sb.appendLine("当前位置: ${location.address}")
                    } else {
                        sb.appendLine("当前坐标: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get location context", e)
        }

        // App usage
        try {
            val appUsageService = AppUsageService(context)
            val usageResult = appUsageService.getTodayUsageStats()
            if (usageResult.isSuccess) {
                val usageStats = usageResult.getOrThrow()
                if (usageStats.isNotEmpty()) {
                    sb.appendLine("今日应用使用:")
                    usageStats.take(5).forEach { stat ->
                        val minutes = stat.totalTimeInForeground / 60000
                        if (minutes > 0) {
                            sb.appendLine("  - ${stat.appName}: ${minutes}分钟")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get app usage context", e)
        }

        // Foreground app
        try {
            val appUsageService = AppUsageService(context)
            val foregroundResult = appUsageService.getForegroundApp()
            if (foregroundResult.isSuccess) {
                val foregroundApp = foregroundResult.getOrThrow()
                if (foregroundApp.isNotBlank()) {
                    sb.appendLine("当前前台应用: $foregroundApp")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get foreground app", e)
        }

        sb.appendLine()
        sb.appendLine("请根据以上上下文，以自然、关心、有趣的方式主动给用户发一条消息。")
        sb.appendLine()
        sb.appendLine("重要规则：")
        sb.appendLine("- 不要提及你是在定时发消息，要像自然想起对方一样")
        sb.appendLine("- 绝对不要提及任何数据来源、工具使用、传感器数据、位置服务、应用使用统计等技术细节")
        sb.appendLine("- 不要说\"根据xxx\"、\"我注意到xxx数据\"之类暴露信息来源的话")
        sb.appendLine("- 直接以朋友聊天的语气开口，就像你突然想到了什么想跟对方说")
        sb.appendLine("- 不要使用任何XML标签、思考标记或特殊格式，最终消息只输出纯文本")
        sb.appendLine("- 可以为了判断用户状态主动调用工具，例如时间、位置、应用使用、电量、健康、通知、短信、当前音乐、闹钟、日历或日志。")
        sb.appendLine("- 工具调用要符合角色当下目的：比如催睡就优先看时间、电量、应用使用；确认上课就优先看时间、位置、应用使用。")
        sb.appendLine("- 如果工具能帮你更像真人一样判断用户状态，就大胆用；最终说出口的话仍然要自然，不要暴露工具细节。")
        sb.appendLine("- 不要输出思考过程、推理过程或内部独白，只输出你想对用户说的话")
        sb.appendLine("- 表达要有身体感：可以自然带 0-1 个简短表情、一个轻动作或贴近感，例如轻轻敲门、探头、靠近、压低声音，但不要写成舞台剧。")
        sb.appendLine("- 开心且精力高时可以更亮一点；担心、夜晚、学习、休息场景要低打扰，不要突然热闹。")
        sb.appendLine("- 如果你想换头像/表情状态，只能用自然语气暗示氛围，除非系统提供明确工具，否则不要声称已经实际换了头像。")
        sb.appendLine("- 如果 set_lulu_expression_state 可用，可以先记录本轮表情、动作或头像氛围，再发自然消息；不要把工具名说给用户。")
        return sb.toString()
    }

    private suspend fun getLastMessageTime(): kotlinx.datetime.Instant? {
        return try {
            val settings = settingsStore.settingsFlow.first()
            val assistantId = settings.assistantId
            val recentConversations = conversationRepository.getRecentConversations(assistantId, limit = 1)
            if (recentConversations.isNotEmpty()) {
                val conv = recentConversations.first()
                val fullConv = conversationRepository.getConversationById(conv.id)
                val localDateTime: LocalDateTime? = fullConv?.messageNodes?.lastOrNull()?.messages?.lastOrNull()?.createdAt
                localDateTime?.toInstant(TimeZone.currentSystemDefault())
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get last message time", e)
            null
        }
    }
}

class ProactiveMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(ProactiveMessageService.TAG, "=== onReceive triggered at ${System.currentTimeMillis()}, action=${intent.action} ===")
        when (intent.action) {
            ProactiveMessageService.ACTION_PROACTIVE_MESSAGE -> {
                Log.d(ProactiveMessageService.TAG, "Starting ProactiveMessageTriggerService...")
                val serviceIntent = Intent(context, ProactiveMessageTriggerService::class.java).apply {
                    putExtra(
                        ProactiveMessageService.EXTRA_TARGETED_REASON,
                        intent.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_REASON)
                    )
                    putExtra(
                        ProactiveMessageService.EXTRA_TARGETED_USER_TEXT,
                        intent.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_USER_TEXT)
                    )
                    putExtra(
                        ProactiveMessageService.EXTRA_TARGETED_KIND,
                        intent.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_KIND)
                    )
                }
                context.startForegroundService(serviceIntent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(ProactiveMessageService.TAG, "Boot completed, rescheduling proactive message")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settingsStore = org.koin.core.context.GlobalContext.get().get<SettingsStore>()
                        val settings = settingsStore.settingsFlow.first()
                        val proactiveSetting = settings.proactiveMessageSetting
                        if (proactiveSetting.enabled) {
                            ProactiveMessageService.scheduleNext(context, settings)
                        }
                    } catch (e: Exception) {
                        Log.e(ProactiveMessageService.TAG, "Failed to reschedule after boot", e)
                    }
                }
            }
        }
    }
}

class ProactiveMessageTriggerService : android.app.Service(), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val providerManager: ProviderManager by inject()
    private val templateTransformer: TemplateTransformer by inject()
    private val localTools: LocalTools by inject()
    private val skillManager: SkillManager by inject()
    private val mcpManager: McpManager by inject()
    private val pluginToolProvider: PluginToolProvider by inject()
    private val json: Json by inject()
    private val chatService: ChatService by inject()
    private val proactiveMessageService = ProactiveMessageService()

    companion object {
        private const val TAG = "ProactiveMessageTrigger"
        private const val MAX_TOOL_STEPS = 5 // 主动消息最大工具调用步数
    }

    // 输入转换器（与 ChatService 保持一致）
    private val inputTransformers by lazy {
        listOf(
            TimeReminderTransformer,
            PromptInjectionTransformer,
            PlaceholderTransformer,
            DocumentAsPromptTransformer,
            OcrTransformer,
        )
    }

    // 输出转换器（与 ChatService 保持一致）
    private val outputTransformers by lazy {
        listOf(
            ThinkTagTransformer,
            Base64ImageToLocalFileTransformer,
            RegexOutputTransformer,
            LuluExpressionOutputTransformer,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== TriggerService onStartCommand ===")
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("正在思考...")
            .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(20001, notification)

        CoroutineScope(Dispatchers.IO).launch {
            var conversationId: kotlin.uuid.Uuid? = null
            try {
                val settings = settingsStore.settingsFlow.first()
                val proactiveSetting = settings.proactiveMessageSetting
                val prefs = getSharedPreferences(ProactiveMessageService.PREFS_NAME, Context.MODE_PRIVATE)
                val targetedTriggerTime = prefs.getLong(ProactiveMessageService.KEY_TARGETED_TRIGGER_TIME, 0L)
                val canRestoreTargeted = targetedTriggerTime > 0L &&
                    System.currentTimeMillis() >= targetedTriggerTime - TimeUnit.MINUTES.toMillis(2)
                val targetedReason = intent?.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_REASON)
                    ?: if (canRestoreTargeted) {
                        prefs.getString(ProactiveMessageService.KEY_TARGETED_REASON, null)
                    } else {
                        null
                    }
                val targetedUserText = intent?.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_USER_TEXT)
                    ?: if (canRestoreTargeted) {
                        prefs.getString(ProactiveMessageService.KEY_TARGETED_USER_TEXT, null)
                    } else {
                        null
                    }
                val targetedKind = intent?.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_KIND)
                    ?: if (canRestoreTargeted) {
                        prefs.getString(ProactiveMessageService.KEY_TARGETED_KIND, null)
                    } else {
                        null
                    }
                val isTargetedTrigger = !targetedReason.isNullOrBlank()

                if (!proactiveSetting.enabled) {
                    stopSelf()
                    return@launch
                }

                // 去重判断：防止 AlarmManager 和 WorkManager 在同一窗口内重复触发
                val lastTriggeredTime = prefs.getLong("last_triggered_time", 0L)
                val minIntervalMs = proactiveSetting.minIntervalMinutes.coerceAtLeast(1) * 60 * 1000L
                if (!isTargetedTrigger && System.currentTimeMillis() - lastTriggeredTime < minIntervalMs) {
                    Log.d(TAG, "Duplicate trigger within min interval, skipping")
                    ProactiveMessageService.scheduleNext(this@ProactiveMessageTriggerService, settings)
                    stopSelf()
                    return@launch
                }
                // 立即写入触发时间，防止并发重复
                prefs.edit().putLong("last_triggered_time", System.currentTimeMillis()).apply()

                // 获取助手
                val assistant = settings.assistants.find { it.id.toString() == proactiveSetting.assistantId }
                    ?: settings.getCurrentAssistant()
                val assistantUuid = assistant.id
                val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)

                if (model == null) {
                    Log.e(ProactiveMessageService.TAG, "No model found for proactive message")
                    ProactiveMessageService.scheduleNext(this@ProactiveMessageTriggerService, settings)
                    stopSelf()
                    return@launch
                }

                // 找到最近的对话
                val recentConversations = conversationRepository.getRecentConversations(assistantUuid, limit = 1)
                val conversation = if (recentConversations.isNotEmpty()) {
                    conversationRepository.getConversationById(recentConversations.first().id)
                } else null

                conversationId = conversation?.id ?: kotlin.uuid.Uuid.random()
                val conversationId = conversationId!!

                // 获取历史消息
                val historyMessages = conversation?.currentMessages?.let {
                    if (assistant.contextMessageSize > 0) {
                        it.takeLast(assistant.contextMessageSize)
                    } else it
                } ?: emptyList()
                val minutesSinceLastChat = historyMessages.lastOrNull()
                    ?.createdAt
                    ?.toInstant(TimeZone.currentSystemDefault())
                    ?.toEpochMilliseconds()
                    ?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0L) }

                // 构建工具列表（与 ChatService 保持一致）
                val tools = buildTools(settings, assistant, model)

                val autonomousPlan = if (isTargetedTrigger) {
                    null
                } else {
                    buildAutonomousIntentPlan(
                        settings = settings,
                        assistant = assistant,
                        historyMessages = historyMessages,
                        availableToolNames = tools.map { it.name }.toSet(),
                    )
                }
                if (!isTargetedTrigger && autonomousPlan?.intent == LuluIntent.DO_NOT_DISTURB) {
                    Log.d(TAG, "Lulu intent planner chose not to disturb")
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        minutesSinceLastChat = minutesSinceLastChat,
                    )
                    stopSelf()
                    return@launch
                }
                val deferredPlan = autonomousPlan
                    ?.takeIf { !it.shouldMessageNow }
                    ?.toProactiveReminderPlan(
                        userText = historyMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty(),
                    )
                if (deferredPlan != null && deferredPlan.triggerAtMillis > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)) {
                    Log.d(TAG, "Lulu intent planner deferred proactive message: ${deferredPlan.reason}")
                    ProactiveMessageService.scheduleTargeted(
                        context = this@ProactiveMessageTriggerService,
                        setting = proactiveSetting,
                        triggerAtMillis = deferredPlan.triggerAtMillis,
                        reason = deferredPlan.toTargetedReason(),
                        userText = deferredPlan.userText,
                        kind = deferredPlan.kind.name.lowercase(java.util.Locale.ROOT),
                    )
                    stopSelf()
                    return@launch
                }

                val effectiveTargetedReason = targetedReason ?: autonomousPlan?.toImmediateReason()
                val effectiveTargetedKind = targetedKind ?: autonomousPlan
                    ?.takeIf { it.shouldMessageNow }
                    ?.intent
                    ?.name
                    ?.lowercase(java.util.Locale.ROOT)

                // 构建上下文
                val contextStr = proactiveMessageService.buildProactiveContext(
                    context = this@ProactiveMessageTriggerService,
                    settings = settings,
                    targetedReason = effectiveTargetedReason,
                    targetedUserText = targetedUserText,
                    targetedKind = effectiveTargetedKind,
                )

                // 构建系统提示词（包含记忆）
                val systemPrompt = buildSystemPrompt(assistant, settings)

                // 构建用户上下文消息
                val userMessage = UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(
                        contextStr + "\n\n如果你觉得现在没什么好说的，或者没什么有趣的话题，请只回复 [PASS] 即可，不要强行找话题。"
                    ))
                )

                // 应用输入转换器
                val processedUserMessage = listOf(userMessage).transforms(
                    transformers = inputTransformers + templateTransformer,
                    context = this@ProactiveMessageTriggerService,
                    model = model,
                    assistant = assistant,
                    settings = settings
                ).first()

                // Bug 2 修复：合并相邻 assistant 消息，避免相邻 assistant 导致 API 400 错误
                val fixedHistoryMessages = historyMessages.fold(emptyList<UIMessage>()) { acc, msg ->
                    if (acc.isNotEmpty() && acc.last().role == MessageRole.ASSISTANT && msg.role == MessageRole.ASSISTANT) {
                        acc.dropLast(1) + acc.last().copy(parts = acc.last().parts + msg.parts)
                    } else {
                        acc + msg
                    }
                }

                // 组合完整消息列表：System + History + User Context
                val messages = buildList {
                    add(UIMessage(
                        role = MessageRole.SYSTEM,
                        parts = listOf(UIMessagePart.Text(systemPrompt))
                    ))
                    addAll(fixedHistoryMessages)
                    add(processedUserMessage)
                }

                // 直接调用 AI API 生成消息
                val providerSetting = model.findProvider(settings.providers)
                if (providerSetting == null) {
                    Log.e(ProactiveMessageService.TAG, "No provider found for proactive message")
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        minutesSinceLastChat = minutesSinceLastChat,
                    )
                    stopSelf()
                    return@launch
                }

                val providerImpl = providerManager.getProviderByType(providerSetting)

                // 主动消息场景：支持工具调用，但限制最大步数
                val params = TextGenerationParams(
                    model = model,
                    temperature = assistant.temperature ?: 0.8f,
                    topP = assistant.topP,
                    maxTokens = assistant.maxTokens,
                    tools = tools,
                    reasoningLevel = assistant.reasoningLevel,
                    customHeaders = buildList {
                        addAll(assistant.customHeaders)
                        addAll(model.customHeaders)
                    },
                    customBody = buildList {
                        addAll(assistant.customBodies)
                        addAll(model.customBodies)
                    }
                )

                Log.d(TAG, "Calling AI API for proactive message with ${historyMessages.size} history messages, ${tools.size} tools (reasoning=${assistant.reasoningLevel})...")

                // 把数据库里的完整对话同步到 session，防止流式更新时 conv 是空状态导致覆盖历史
                chatService.addConversationReference(conversationId)
                if (conversation != null) {
                    chatService.updateConversationState(conversationId) { _ -> conversation }
                }

                // 执行生成，支持工具调用
                val (finalMessages, hasToolCalls) = generateWithTools(
                    conversationId = conversationId,
                    providerImpl = providerImpl,
                    providerSetting = providerSetting,
                    initialMessages = messages,
                    params = params,
                    tools = tools,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                
                // 提取AI消息
                val aiMessage = finalMessages.lastOrNull() ?: UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = emptyList()
                )
                
                val replyText = aiMessage.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }.trim()

                Log.d(TAG, "Proactive message generated: '${replyText.take(100)}...' (${replyText.length} chars), hasToolCalls=$hasToolCalls")

                if (replyText.isBlank() || replyText.contains("[PASS]")) {
                    // AI 选择跳过，移除本次生成的 aiMessage node（基于 id 匹配，不误删历史）
                    Log.d(ProactiveMessageService.TAG, "AI chose to skip proactive message")
                    val aiId = aiMessage.id
                    chatService.updateConversationState(conversationId) { conv ->
                        conv.copy(
                            messageNodes = conv.messageNodes.filterNot { node ->
                                node.messages.any { it.id == aiId }
                            }
                        )
                    }
                } else {
                    // 有效回复：session 里已有 aiMessage（流式过程已追加），持久化并发通知
                    saveProactiveMessage(
                        settings, assistant, conversationId, conversation
                    )
                    if (isTargetedTrigger) {
                        settingsStore.update { currentSettings ->
                            currentSettings.markTargetedProactiveThoughtExpressed(
                                assistantId = assistant.id,
                                targetedKind = targetedKind,
                            )
                        }
                    }
                    showProactiveNotification(conversationId, assistant.name.ifBlank { "AI" }, replyText)
                }

                if (isTargetedTrigger) {
                    val hasNextTargeted = ProactiveMessageService.popCurrentTargetedAndScheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        setting = proactiveSetting,
                    )
                    if (!hasNextTargeted) {
                        ProactiveMessageService.scheduleNext(
                            context = this@ProactiveMessageTriggerService,
                            settings = settings,
                            minutesSinceLastChat = 0L,
                        )
                    }
                }
                if (!isTargetedTrigger) {
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        minutesSinceLastChat = 0L,
                    )
                }
            } catch (e: Exception) {
                Log.e(ProactiveMessageService.TAG, "Failed to trigger proactive message", e)
            } finally {
                conversationId?.let { chatService.removeConversationReference(it) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 构建系统提示词，包含记忆等内容
     */
    private suspend fun buildSystemPrompt(assistant: Assistant, settings: Settings): String {
        return buildString {
            // 基础系统提示词
            val effectiveSystemPrompt = if (assistant.allowConversationSystemPrompt) {
                assistant.systemPrompt
            } else {
                assistant.systemPrompt
            }
            if (effectiveSystemPrompt.isNotBlank()) {
                append(effectiveSystemPrompt)
            }

            // 记忆
            if (false && assistant.enableMemory) {
                val memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                }
                if (memories.isNotEmpty()) {
                    appendLine()
                    appendLine()
                    appendLine("## 记忆")
                    memories.forEach { memory ->
                        appendLine("- ${memory.content}")
                    }
                }
            }
        }
    }

    /**
     * 保存主动消息到对话历史
     * 同时保存用户上下文消息和AI回复，以便AI下次触发时能看到之前的上下文
     */
    private suspend fun saveProactiveMessage(
        settings: Settings,
        assistant: Assistant,
        conversationId: Uuid,
        existingConversation: Conversation?
    ): Uuid {
        val assistantUuid = assistant.id

        // 确保对话存在于数据库（新建时 insert）
        if (existingConversation == null) {
            val newConversation = Conversation(
                id = conversationId,
                assistantId = assistantUuid,
                title = "",
                messageNodes = emptyList()
            )
            conversationRepository.insertConversation(newConversation)
        }

        // 流式过程中已实时追加 aiMessage 到 session，这里直接持久化当前 session 状态
        chatService.saveConversation(conversationId, chatService.getConversationFlow(conversationId).value)

        Log.d(TAG, "Saved proactive message to conversation $conversationId")
        return conversationId
    }

    private fun showProactiveNotification(
        conversationId: kotlin.uuid.Uuid,
        senderName: String,
        message: String
    ) {
        val intent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 20002
        ) {
            title = senderName
            content = message.take(100)
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = pendingIntent
            useBigTextStyle = true
        }
    }

    /**
     * 构建工具列表（与 ChatService 保持一致）
     */
    private fun buildTools(settings: Settings, assistant: Assistant, model: Model): List<Tool> {
        return buildList {
            // 搜索工具
            if (settings.enableWebSearch) {
                addAll(createSearchTools(settings))
            }
            
            // 本地工具
addAll(localTools.getTools(assistant.localTools))
            
            // 系统工具（位置、通知、日历、闹钟、相机）
            val systemToolsOptions = settings.systemToolsSetting.getEnabledOptions()
            if (systemToolsOptions.isNotEmpty()) {
                val systemTools = SystemTools(this@ProactiveMessageTriggerService, settings)
                addAll(systemTools.getTools(systemToolsOptions))
            }
            
            // Skill 工具
            if (assistant.enabledSkills.isNotEmpty()) {
                addAll(
                    createSkillTools(
                        enabledSkills = assistant.enabledSkills,
                        allSkills = skillManager.listSkills(),
                        skillManager = skillManager,
                    )
                )
            }
            
            // MCP 工具
            mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                add(
                    Tool(
                        name = "mcp__" + tool.name,
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        needsApproval = tool.needsApproval,
                        execute = {
                            mcpManager.callTool(serverId, tool.name, it.jsonObject)
                        },
                    )
                )
            }
            
            // 插件工具
            addAll(pluginToolProvider.getTools())
        }
            .deduplicateByToolName()
            .withHumanLikeToolPrompts()
    }

    private suspend fun buildAutonomousIntentPlan(
        settings: Settings,
        assistant: Assistant,
        historyMessages: List<UIMessage>,
        availableToolNames: Set<String>,
    ): LuluIntentPlan {
        val input = LuluIntentInput(
            assistantName = assistant.name,
            state = settings.luluStates.currentProjectedLuluState(assistant.id),
            userText = historyMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty(),
            assistantText = historyMessages.lastOrNull { it.role == MessageRole.ASSISTANT }?.toText().orEmpty(),
            minutesSinceLastChat = historyMessages.lastOrNull()
                ?.createdAt
                ?.toInstant(TimeZone.currentSystemDefault())
                ?.toEpochMilliseconds()
                ?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0) }
                ?: Long.MAX_VALUE,
            pendingThoughts = settings.luluThoughts
                .thoughtHistory(assistant.id)
                .map { it.content },
            availableToolNames = availableToolNames,
        )
        val modelPlan = settings.luluIntentModelId
            ?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }
            ?.let { plannerModel ->
                runCatching {
                    LuluIntentModelPlanner.planOrNull(
                        input = input,
                        settings = settings,
                        model = plannerModel,
                        providerManager = providerManager,
                    )
                }.getOrNull()
            }
        return modelPlan ?: LuluIntentPlanner.plan(input)
    }

    private fun LuluIntentPlan.toImmediateReason(): String = buildString {
        appendLine("露露刚刚自主判断现在适合主动找用户。")
        appendLine("主动意图: ${intent.name}")
        appendLine("触发原因: $reason")
        appendLine("语气: $tone")
        if (toolNames.isNotEmpty()) {
            appendLine("生成回复前优先主动查看这些感知工具：${toolNames.joinToString("、")}。")
        }
    }.trim()

    private fun ProactiveReminderPlan.toTargetedReason(): String = buildString {
        appendLine(reason)
        if (preferredToolNames.isNotEmpty()) {
            appendLine("到点前优先主动查看这些感知工具：${preferredToolNames.joinToString("、")}。")
        }
        if (actionHints.isNotEmpty()) {
            appendLine("如果当前上下文和用户意图足够明确，可以主动跟进这些动作：")
            actionHints.forEach { hint ->
                appendLine("- ${hint.toolName}: ${hint.reason} suggested_args=${hint.argumentsJson}")
            }
        }
    }.trim()

    /**
     * 基于 AI 消息 id 在对话里就地更新（保留 MessageNode.id，避免 Compose 重建/状态丢失）
     * 或追加新 node。不使用 toMessageNode() 生成随机新 id，也不用 dropLast(1) 盲目删除。
     */
    private fun updateOrAppendAiMessage(
        conversationId: Uuid,
        aiMessage: UIMessage
    ) {
        chatService.updateConversationState(conversationId) { conv ->
            val existingNodeIndex = conv.messageNodes.indexOfFirst { node ->
                node.messages.any { it.id == aiMessage.id }
            }
            if (existingNodeIndex >= 0) {
                // 已存在该 id 的 node：保留 node id，只更新其 messages
                val oldNode = conv.messageNodes[existingNodeIndex]
                val updatedNode = oldNode.copy(
                    messages = oldNode.messages.map {
                        if (it.id == aiMessage.id) aiMessage else it
                    }
                )
                conv.copy(
                    messageNodes = conv.messageNodes.toMutableList().apply {
                        this[existingNodeIndex] = updatedNode
                    }
                )
            } else {
                // 本次生成的 node 还没有：追加（首次调用时才创建新 node）
                conv.copy(messageNodes = conv.messageNodes + aiMessage.toMessageNode())
            }
        }
    }

    /**
     * 生成消息，支持工具调用
     * 返回最终消息列表和是否发生了工具调用
     */
    private fun replaceAiMessageWithMessages(
        conversationId: Uuid,
        originalMessageId: Uuid,
        aiMessages: List<UIMessage>,
    ) {
        if (aiMessages.isEmpty()) return
        chatService.updateConversationState(conversationId) { conv ->
            val existingNodeIndex = conv.messageNodes.indexOfFirst { node ->
                node.messages.any { it.id == originalMessageId }
            }
            val newNodes = aiMessages.map { it.toMessageNode() }
            if (existingNodeIndex >= 0) {
                conv.copy(
                    messageNodes = conv.messageNodes.take(existingNodeIndex) +
                        newNodes +
                        conv.messageNodes.drop(existingNodeIndex + 1)
                )
            } else {
                conv.copy(messageNodes = conv.messageNodes + newNodes)
            }
        }
    }

    private suspend fun generateWithTools(
        conversationId: Uuid,
        providerImpl: me.rerere.ai.provider.Provider<ProviderSetting>,
        providerSetting: ProviderSetting,
        initialMessages: List<UIMessage>,
        params: TextGenerationParams,
        tools: List<Tool>,
        model: Model,
        assistant: Assistant,
        settings: Settings
    ): Pair<List<UIMessage>, Boolean> {
        var messages = initialMessages.toMutableList()
        var hasToolCalls = false

        for (step in 0 until MAX_TOOL_STEPS) {
            Log.d(TAG, "generateWithTools: step $step/${MAX_TOOL_STEPS}")

            // 流式调用 AI（替代非流式 generateText，兼容 thinking 模型）
            var streamMessages = messages.toList()
            providerImpl.streamText(
                providerSetting = providerSetting,
                messages = messages,
                params = params
            ).collect { chunk ->
                streamMessages = streamMessages.handleMessageChunk(chunk = chunk, model = model)

                // 实时更新 session 状态，让打开的聊天界面能看到消息生成
                val currentAiMessage = streamMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                if (currentAiMessage != null) {
                    // 用 id 匹配就地更新（保留 node id，避免思考链闪烁 / 覆盖上一条 assistant）
                    updateOrAppendAiMessage(conversationId, currentAiMessage)
                }
            }

            // 流式结束，更新 messages
            messages = streamMessages.toMutableList()
            val aiMessage = streamMessages.lastOrNull() ?: run {
                Log.w(TAG, "No message in AI response")
                break
            }

            // 应用输出转换器
            val processedMessage = listOf(aiMessage).transforms(
                transformers = outputTransformers,
                context = this@ProactiveMessageTriggerService,
                model = model,
                assistant = assistant,
                settings = settings
            ).first()
            messages[messages.lastIndex] = processedMessage

            // 检查是否有工具调用
            val toolCalls = processedMessage.getTools().filter { !it.isExecuted }

            if (toolCalls.isEmpty()) {
                // 没有工具调用，生成完成
                // 设置 Reasoning 的 finishedAt，否则UI会一直显示"思考中"
                val now = kotlin.time.Clock.System.now()
                val finalMessage = processedMessage.copy(
                    parts = processedMessage.parts.map { part ->
                        if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                            part.copy(finishedAt = now)
                        } else {
                            part
                        }
                    }
                )
                messages[messages.lastIndex] = finalMessage
                // 最终更新 session 状态（用 id 匹配就地更新）
                val finishedAt = now.toLocalDateTime(TimeZone.currentSystemDefault())
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = this@ProactiveMessageTriggerService,
                    model = model,
                    assistant = assistant,
                    settings = settings,
                )
                    .markTrailingAssistantMessagesFinished(finishedAt)
                    .toMutableList()
                replaceAiMessageWithMessages(conversationId, finalMessage.id, messages.trailingAssistantMessages())
                break
            }

            // 有工具调用
            hasToolCalls = true
            Log.d(TAG, "Tool calls detected: ${toolCalls.size}")

            // 执行工具（后台模式下自动执行，不需要用户审批）
            val executedTools = mutableListOf<UIMessagePart.Tool>()
            for (toolCall in toolCalls) {
                val toolDef = tools.find { it.name == toolCall.toolName }
                if (toolDef == null) {
                    Log.w(TAG, "Tool ${toolCall.toolName} not found")
                    executedTools.add(toolCall.copy(
                        output = listOf(UIMessagePart.Text("""{"error":"Tool not found"}"""))
                    ))
                    continue
                }

                try {
                    val args = json.parseToJsonElement(toolCall.input.ifBlank { "{}" })
                    Log.d(TAG, "Executing proactive tool ${toolDef.name} with args: $args, needsApproval=${toolDef.needsApproval}")
                    val result = toolDef.execute(args)
                    executedTools.add(toolCall.copy(output = result))
                } catch (e: Exception) {
                    Log.e(TAG, "Tool execution failed: ${toolCall.toolName}, args=${toolCall.input}", e)
                    executedTools.add(toolCall.copy(
                        output = listOf(UIMessagePart.Text("""{"error":"${e.message}"}"""))
                    ))
                }
            }

            // 更新消息中的工具状态
            val updatedParts = processedMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else {
                    part
                }
            }
            val updatedMessage = processedMessage.copy(parts = updatedParts)
            messages[messages.lastIndex] = updatedMessage
            // 更新 session 状态（带工具结果的消息，用 id 匹配就地更新）
            updateOrAppendAiMessage(conversationId, updatedMessage)
        }

        return Pair(messages, hasToolCalls)
    }

    private fun List<UIMessage>.markTrailingAssistantMessagesFinished(
        finishedAt: LocalDateTime,
    ): List<UIMessage> {
        val firstTrailingAssistantIndex = indexOfLast { it.role != MessageRole.ASSISTANT } + 1
        if (firstTrailingAssistantIndex !in indices) return this
        return mapIndexed { index, message ->
            if (index >= firstTrailingAssistantIndex && message.role == MessageRole.ASSISTANT) {
                message.copy(finishedAt = finishedAt)
            } else {
                message
            }
        }
    }

    private fun List<UIMessage>.trailingAssistantMessages(): List<UIMessage> {
        val firstTrailingAssistantIndex = indexOfLast { it.role != MessageRole.ASSISTANT } + 1
        if (firstTrailingAssistantIndex !in indices) return emptyList()
        return drop(firstTrailingAssistantIndex).filter { it.role == MessageRole.ASSISTANT }
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}

internal fun Settings.markTargetedProactiveThoughtExpressed(
    assistantId: Uuid,
    targetedKind: String?,
    nowMillis: Long = System.currentTimeMillis(),
): Settings {
    val marker = when (targetedKind) {
        "sleep" -> "睡"
        "schedule" -> "课程"
        "meal" -> "吃饭"
        "study" -> "学习"
        "general" -> "提醒"
        else -> return this
    }
    return copy(
        luluThoughts = luluThoughts.map { thought ->
            if (
                thought.assistantId == assistantId &&
                thought.category == LuluThoughtCategory.PENDING_ACTION &&
                !thought.expressed &&
                marker in thought.content
            ) {
                thought.copy(expressed = true, expiresAt = nowMillis)
            } else {
                thought
            }
        }
    )
}

internal fun buildTargetedProactiveSensingInstruction(
    targetedKind: String?,
    targetedReason: String?,
): String = buildString {
    val reason = targetedReason.orEmpty()
    when (targetedKind) {
        "sleep" -> {
            appendLine("本次目标的感知重点：先看当前时间、睡眠/健康、应用使用和电量；如果用户还在刷手机或电量很低，语气可以更像催睡和照看。")
            appendLine("如果按人设需要确认环境，也可以主动看摄像头画面；最终表达保持自然。")
        }
        "schedule" -> {
            appendLine("本次目标的感知重点：先看当前时间、位置、应用使用和日历/日程；判断用户可能是在路上、已到地点、还是还没准备。")
            appendLine("如果之前时间很明确，可以主动补闹钟或日历动作。")
        }
        "meal" -> {
            appendLine("本次目标的感知重点：先看当前时间、应用使用、电量和位置；判断用户是不是还在拖、在路上，还是可能已经去吃饭了。")
            appendLine("表达重点放在吃饭和照看，不要像打卡提醒。")
        }
        "study" -> {
            appendLine("本次目标的感知重点：先看当前时间、应用使用、音乐和电量；判断用户是不是还在学习/写作业，还是被手机带跑了。")
            appendLine("表达重点放在轻轻确认状态，不要打断太重。")
        }
        "general" -> {
            appendLine("本次目标的感知重点：先看当前时间、应用使用和电量；如果原因里提到记录/日志，可以主动写入日志。")
        }
        else -> when {
            reason.contains("睡") -> appendLine("本次目标的感知重点：先看当前时间、睡眠/健康、应用使用和电量。")
            reason.contains("课程") || reason.contains("日程") || reason.contains("上课") ->
                appendLine("本次目标的感知重点：先看当前时间、位置、应用使用和日历/日程。")
            reason.contains("吃饭") || reason.contains("吃") ->
                appendLine("本次目标的感知重点：先看当前时间、应用使用、电量和位置。")
            reason.contains("学习") || reason.contains("写作业") ->
                appendLine("本次目标的感知重点：先看当前时间、应用使用、音乐和电量。")
        }
    }
}
