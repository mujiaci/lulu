package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.buildPromptInjectionPlannerContext
import me.rerere.rikkahub.data.ai.transformers.companionInputTransformers
import me.rerere.rikkahub.data.ai.transformers.companionModelPresence
import me.rerere.rikkahub.data.ai.transformers.companionOutputTransformers
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.SystemTools
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createTodayStudyPlanTool
import me.rerere.rikkahub.data.ai.tools.deduplicateByToolName
import me.rerere.rikkahub.data.ai.tools.activeModelTools
import me.rerere.rikkahub.data.ai.tools.selectRelevantToolsForPrompt
import me.rerere.rikkahub.data.ai.tools.selectCompanionToolsForGeneration
import me.rerere.rikkahub.data.ai.tools.withConciseToolDescriptions
import me.rerere.rikkahub.data.ai.tools.withHumanLikeToolPrompts
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.plugin.provider.PluginToolProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.companion.CompanionActionResult
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.data.companion.CompanionConversationTurn
import me.rerere.rikkahub.data.companion.CompanionModelPresence
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionState
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.CompanionTurnRole
import me.rerere.rikkahub.data.companion.buildCompanionStateFromTurn
import me.rerere.rikkahub.data.companion.isSleepSupervisionGoal
import me.rerere.rikkahub.data.companion.isWakeGoal
import me.rerere.rikkahub.data.companion.retryMinutesOrDefault
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.companion.wakeTargetAtOrNull
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyTaskSource
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.CompanionDecisionMode
import me.rerere.rikkahub.service.CompanionIntent
import me.rerere.rikkahub.service.CompanionIntentDecision
import me.rerere.rikkahub.service.CompanionIntentFallbackPlanner
import me.rerere.rikkahub.service.CompanionIntentInput
import me.rerere.rikkahub.service.CompanionIntentModelPlanner
import me.rerere.rikkahub.service.collectCompanionPassivePerceptionFacts
import me.rerere.rikkahub.service.ProactiveReminderPlan
import me.rerere.rikkahub.service.toProactiveReminderPlan
import me.rerere.rikkahub.utils.sendNotification
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ProactiveMessageService : KoinComponent {
    private val studyStore: StudyStore by inject()
    private val cihaiStore: CihaiStore by inject()

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
        internal const val KEY_TARGETED_COMMITMENT_ID = "targeted_commitment_id"
        internal const val KEY_TARGETED_QUEUE = "targeted_queue"
        internal const val EXTRA_TARGETED_REASON = "targeted_reason"
        internal const val EXTRA_TARGETED_USER_TEXT = "targeted_user_text"
        internal const val EXTRA_TARGETED_KIND = "targeted_kind"
        internal const val EXTRA_COMMITMENT_ID = "commitment_id"
        internal const val EXTRA_ASSISTANT_ID = "assistant_id"

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
                .putString(EXTRA_ASSISTANT_ID, setting.assistantId)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
                putExtra(EXTRA_ASSISTANT_ID, setting.assistantId)
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
            assistantId: Uuid? = null,
        ) {
            val setting = settings.getProactiveMessageSetting(assistantId)
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
            val companionRuntime = org.koin.core.context.GlobalContext.get().get<CompanionRuntime>()
            val pulseInput = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = companionRuntime.snapshot(assistant.id.toString()),
                minutesSinceLastChat = minutesSinceLastChat
                    ?: lastTriggeredTime
                        .takeIf { it > 0L }
                        ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L) }
                    ?: Long.MAX_VALUE,
                activeTargetedTriggerMillis = activeTargetedTrigger,
                nowMillis = nowMillis,
            )
            val pulsePlan = CompanionAutonomousPulsePlanner.planNext(pulseInput)
            val triggerTime = CompanionAutonomousPulsePlanner.triggerTimeMillis(pulseInput, pulsePlan)
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
                .putString(EXTRA_ASSISTANT_ID, setting.assistantId)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
                putExtra(EXTRA_ASSISTANT_ID, setting.assistantId)
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
            assistantId: String = setting.assistantId,
            commitmentId: String? = null,
        ) {
            if (!setting.enabled || triggerAtMillis <= java.lang.System.currentTimeMillis()) return

            val preferencesEditor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerAtMillis)
                .putLong(KEY_TARGETED_TRIGGER_TIME, triggerAtMillis)
                .putString(KEY_TARGETED_REASON, reason)
                .putString(KEY_TARGETED_USER_TEXT, userText)
                .putString(KEY_TARGETED_KIND, kind)
                .putString(EXTRA_ASSISTANT_ID, assistantId)
            if (commitmentId.isNullOrBlank()) {
                preferencesEditor.remove(KEY_TARGETED_COMMITMENT_ID)
            } else {
                preferencesEditor.putString(KEY_TARGETED_COMMITMENT_ID, commitmentId)
            }
            preferencesEditor.apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
                putExtra(EXTRA_ASSISTANT_ID, assistantId)
                putExtra(EXTRA_TARGETED_REASON, reason)
                putExtra(EXTRA_TARGETED_USER_TEXT, userText)
                putExtra(EXTRA_TARGETED_KIND, kind)
                commitmentId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COMMITMENT_ID, it) }
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
            commitmentId?.takeIf { it.isNotBlank() }?.let { id ->
                ProactiveMessageWorker.scheduleTargeted(
                    context = context,
                    triggerAtMillis = triggerAtMillis,
                    assistantId = assistantId,
                    commitmentId = id,
                )
            }
        }

        fun scheduleCommitment(
            context: Context,
            setting: ProactiveMessageSetting,
            commitment: CompanionCommitment,
        ) {
            scheduleTargeted(
                context = context,
                setting = setting,
                triggerAtMillis = maxOf(commitment.dueAt, System.currentTimeMillis() + 1_000L),
                reason = commitment.promise,
                userText = commitment.actionPlan.contextText,
                kind = commitment.actionPlan.category.ifBlank { "commitment" },
                assistantId = commitment.assistantId,
                commitmentId = commitment.id,
            )
        }

        fun clearTargetedQueue(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TARGETED_TRIGGER_TIME)
                .remove(KEY_TARGETED_REASON)
                .remove(KEY_TARGETED_USER_TEXT)
                .remove(KEY_TARGETED_KIND)
                .remove(KEY_TARGETED_COMMITMENT_ID)
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
            ProactiveMessageWorker.cancelTargeted(context)
        }

        fun resetAssistantProjection(
            context: Context,
            settings: Settings,
            assistantId: Uuid,
        ) {
            val id = assistantId.toString()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!shouldResetProactiveProjection(prefs.getString(EXTRA_ASSISTANT_ID, null), id)) return

            clearTargetedQueue(context)
            cancel(context)
            prefs.edit()
                .remove(EXTRA_ASSISTANT_ID)
                .remove("last_triggered_time")
                .remove("next_trigger_reason")
                .apply()
            scheduleNext(
                context = context,
                settings = settings,
                assistantId = assistantId,
            )
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
            ProactiveMessageWorker.cancelTargeted(context)
        }

        fun resetTimer(context: Context, setting: ProactiveMessageSetting) {
            scheduleNext(context, setting)
        }

        fun triggerNow(context: Context, setting: ProactiveMessageSetting) {
            // 先安排下一次（写入SP让UI立即显示），再立即触发
            scheduleNext(context, setting)
            // 立即触发：直接启动TriggerService
            val serviceIntent = Intent(context, ProactiveMessageTriggerService::class.java).apply {
                putExtra(EXTRA_ASSISTANT_ID, setting.assistantId)
            }
            context.startForegroundService(serviceIntent)
        }
    }

    suspend fun buildProactiveContext(
        context: Context,
        settings: Settings,
        assistant: Assistant,
        minutesSinceLastChat: Long?,
        targetedReason: String? = null,
        targetedUserText: String? = null,
        targetedKind: String? = null,
    ): String {
        val sb = StringBuilder()
        val assistantName = assistant.name.ifBlank { "当前角色" }
        sb.appendLine("[主动消息上下文]")
        if (!targetedReason.isNullOrBlank()) {
            sb.appendLine("这次不是随机主动消息，而是${assistantName}刚才自己决定稍后回来确认的一次目标消息。")
            sb.appendLine("目标类型: ${targetedKind.orEmpty()}")
            sb.appendLine("触发原因: $targetedReason")
            sb.appendLine("生成回复前，先结合本上下文已经提供的感知事实；如果列出了后续动作，再判断是否需要执行后自然开口。")
            sb.appendLine("如果用户之前已经把提醒、记录、闹钟或日程意图说得很明确，可以主动完成对应工具动作；不要机械等用户再次确认。")
            sb.append(buildTargetedProactiveSensingInstruction(targetedKind, targetedReason))
            if (!targetedUserText.isNullOrBlank()) {
                sb.appendLine("当时用户说过: $targetedUserText")
            }
        }

        if (minutesSinceLastChat != null) {
            val hoursAgo = minutesSinceLastChat / 60
            when {
                hoursAgo > 24 -> sb.appendLine("距离上次聊天: ${hoursAgo / 24}天${hoursAgo % 24}小时")
                hoursAgo > 0 -> sb.appendLine("距离上次聊天: ${hoursAgo}小时${minutesSinceLastChat % 60}分钟")
                else -> sb.appendLine("距离上次聊天: ${minutesSinceLastChat}分钟")
            }
        } else {
            sb.appendLine("距离上次聊天: 很久没有聊天了")
        }

        // Current time
        val currentTime = java.lang.System.currentTimeMillis()
        sb.appendLine(me.rerere.rikkahub.service.LocalTimeContextFormatter.format(currentTime))

        // Study plan context
        try {
            val studyState = studyStore.state.first()
            val selectedStudyAssistant = studyState.selectedAssistantId
            if (selectedStudyAssistant == assistant.id.toString()) {
                val planTasks = studyState.tasks.filter { it.source == StudyTaskSource.Plan }
                val manualTasks = studyState.tasks.filter { it.source == StudyTaskSource.Manual }
                val undoneTasks = studyState.tasks.filterNot { it.done }
                if (planTasks.isNotEmpty() || manualTasks.isNotEmpty() || studyState.stats.totalPomodoros > 0) {
                val donePlan = planTasks.count { it.done }
                sb.appendLine("今日考研计划:")
                sb.appendLine("  - 日期: ${studyState.today}")
                sb.appendLine("  - 计划待办完成: $donePlan/${planTasks.size}")
                sb.appendLine("  - 手动待办: ${manualTasks.count { it.done }}/${manualTasks.size}")
                sb.appendLine("  - 累计番茄钟: ${studyState.stats.totalPomodoros} 个，累计学习 ${studyState.stats.totalStudyMinutes} 分钟")
                if (undoneTasks.isNotEmpty()) {
                    sb.appendLine("  - 未完成待办:")
                    undoneTasks.take(6).forEach { task ->
                        sb.appendLine("    · ${task.title}")
                    }
                }
                if (undoneTasks.isNotEmpty()) {
                    sb.appendLine("  - 你就是今天陪用户学习的角色。主动回来时可以自然提醒未完成待办，语气要像偏爱和陪伴，不要像系统催办；优先帮用户启动一个最小任务。")
                }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get study plan context", e)
        }

        try {
            val assistantId = assistant.id.toString()
            val cihaiState = cihaiStore.state.first()
            val recentEntries = recentFormalDiaryEntries(
                entries = cihaiState.entries,
                assistantId = assistantId,
            )
            if (recentEntries.isNotEmpty()) {
                sb.appendLine("辞海上下文:")
                if (recentEntries.isNotEmpty()) {
                    sb.appendLine("  - 最近正式日记:")
                    recentEntries.forEach { entry ->
                        sb.appendLine("    · ${entry.kind.label}｜${entry.title}: ${entry.content.take(80)}")
                    }
                }
                if (recentEntries.isNotEmpty()) {
                    sb.appendLine("  - 调用 write_lulu_journal 前必须对比以上最近 3 篇正式日记；只能写本轮新增的真实感受或变化，没有新内容就不要写。")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Cihai context", e)
        }

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
        sb.appendLine("- 时间、电量、健康、位置和应用使用等被动感知已经直接写在上下文里，不要为了重复读取这些信息而调用工具。")
        sb.appendLine("- 工具只用于角色决定主动做出的动作，例如设闹钟、写日历、看摄像头、控制音乐、收藏消息或写正式日记。正式日记只在确有新内容时调用 write_lulu_journal。")
        sb.appendLine("- 涉及时间时必须以“当前本地时间”的 24 小时制为准，00:00-04:59 是凌晨，不能说成下午或中午。")
        sb.appendLine("- 先基于已有感知判断，再按角色当下目的选择必要动作；最终说出口的话仍然要自然，不要暴露技术细节。")
        sb.appendLine("- 不要输出思考过程、推理过程或内部独白，只输出你想对用户说的话")
        sb.appendLine("- 表达可以有适量的节奏和动作感，但必须服从角色人设与关系边界；没有明确依据时不要虚构身体接触、空间距离或亲密动作。")
        sb.appendLine("- 开心且精力高时可以更亮一点；担心、夜晚、学习、休息场景要轻声一点，不要突然抢走用户注意力。")
        sb.appendLine("- 如果你想换头像/表情状态，只能用自然语气暗示氛围，除非系统提供明确工具，否则不要声称已经实际换了头像。")
        sb.appendLine("- 如果 set_lulu_expression_state 可用，可以先记录一句完整动作描写，把表情、动作和姿势合在一起；不要把工具名说给用户。")
        return sb.toString()
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
                        ProactiveMessageService.EXTRA_ASSISTANT_ID,
                        intent.getStringExtra(ProactiveMessageService.EXTRA_ASSISTANT_ID)
                    )
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
                    putExtra(
                        ProactiveMessageService.EXTRA_COMMITMENT_ID,
                        intent.getStringExtra(ProactiveMessageService.EXTRA_COMMITMENT_ID)
                    )
                }
                context.startForegroundService(serviceIntent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(ProactiveMessageService.TAG, "Boot completed, rescheduling proactive message")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settingsStore = org.koin.core.context.GlobalContext.get().get<SettingsStore>()
                        val companionRuntime = org.koin.core.context.GlobalContext.get().get<CompanionRuntime>()
                        val settings = settingsStore.settingsFlow.first()
                        companionRuntime.nextCommitment()?.let { commitment ->
                            val assistantId = runCatching { Uuid.parse(commitment.assistantId) }.getOrNull()
                            val setting = assistantId?.let { settings.getProactiveMessageSetting(it) }
                            if (setting?.enabled == true) {
                                ProactiveMessageService.scheduleCommitment(context, setting, commitment)
                            }
                        }
                        val proactiveSetting = settings.getProactiveMessageSetting()
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
    private val memoryBankService: MemoryBankService by inject()
    private val providerManager: ProviderManager by inject()
    private val templateTransformer: TemplateTransformer by inject()
    private val localTools: LocalTools by inject()
    private val skillManager: SkillManager by inject()
    private val mcpManager: McpManager by inject()
    private val pluginToolProvider: PluginToolProvider by inject()
    private val json: Json by inject()
    private val chatService: ChatService by inject()
    private val companionRuntime: CompanionRuntime by inject()
    private val proactiveMessageService = ProactiveMessageService()

    companion object {
        private const val TAG = "ProactiveMessageTrigger"
        private const val MAX_TOOL_STEPS = 5 // 主动消息最大工具调用步数
        private const val MAX_COMMITMENT_ATTEMPTS = 3
        private const val COMMITMENT_RETRY_MINUTES = 15L
        private const val MAX_SCHEDULER_CANDIDATES = 50
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
            var executingCommitment: CompanionCommitment? = null
            var completedCommitmentResult: CompanionActionResult? = null
            try {
                val settings = settingsStore.settingsFlow.first()
                val prefs = getSharedPreferences(ProactiveMessageService.PREFS_NAME, Context.MODE_PRIVATE)
                val triggerAssistantId = intent?.getStringExtra(ProactiveMessageService.EXTRA_ASSISTANT_ID)
                    ?: prefs.getString(ProactiveMessageService.EXTRA_ASSISTANT_ID, null)
                val triggerAssistantUuid = triggerAssistantId
                    ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                val proactiveSetting = settings.getProactiveMessageSetting(triggerAssistantUuid)
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
                val targetedCommitmentId = intent?.getStringExtra(ProactiveMessageService.EXTRA_COMMITMENT_ID)
                    ?: if (canRestoreTargeted) {
                        prefs.getString(ProactiveMessageService.KEY_TARGETED_COMMITMENT_ID, null)
                    } else {
                        null
                    }
                val isDurableCommitmentTrigger = !targetedCommitmentId.isNullOrBlank()
                val isTargetedTrigger = isDurableCommitmentTrigger || !targetedReason.isNullOrBlank()

                if (!proactiveSetting.enabled) {
                    if (isDurableCommitmentTrigger && !triggerAssistantId.isNullOrBlank()) {
                        companionRuntime.snapshot(triggerAssistantId).commitments
                            .firstOrNull { it.id == targetedCommitmentId }
                            ?.let { commitment ->
                                discardUnschedulableCommitment(commitment, "Proactive messaging disabled")
                            }
                    }
                    stopSelf()
                    return@launch
                }

                if (targetedKind == "living_presence") {
                    Log.d(TAG, "Discarding legacy Living Presence trigger")
                    val hasNextTargeted = ProactiveMessageService.popCurrentTargetedAndScheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        setting = proactiveSetting,
                    )
                    if (!hasNextTargeted) {
                        ProactiveMessageService.scheduleNext(
                            context = this@ProactiveMessageTriggerService,
                            settings = settings,
                            assistantId = triggerAssistantUuid,
                        )
                    }
                    stopSelf()
                    return@launch
                }

                // 去重判断：防止 AlarmManager 和 WorkManager 在同一窗口内重复触发
                val lastTriggeredTime = prefs.getLong("last_triggered_time", 0L)
                val minIntervalMs = proactiveSetting.minIntervalMinutes.coerceAtLeast(1) * 60 * 1000L
                if (!isTargetedTrigger && System.currentTimeMillis() - lastTriggeredTime < minIntervalMs) {
                    Log.d(TAG, "Duplicate trigger within min interval, skipping")
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        assistantId = triggerAssistantUuid,
                    )
                    stopSelf()
                    return@launch
                }
                // 立即写入触发时间，防止并发重复
                prefs.edit().putLong("last_triggered_time", System.currentTimeMillis()).apply()

                // 获取助手
                val assistant = if (isDurableCommitmentTrigger) {
                    settings.assistants.find { it.id.toString() == triggerAssistantId }
                } else {
                    settings.assistants.find { it.id.toString() == proactiveSetting.assistantId }
                        ?: settings.getCurrentAssistant()
                }
                if (assistant == null) {
                    Log.w(TAG, "Ignoring commitment for missing assistant id=$triggerAssistantId")
                    if (!triggerAssistantId.isNullOrBlank()) {
                        companionRuntime.snapshot(triggerAssistantId).commitments
                            .firstOrNull { it.id == targetedCommitmentId }
                            ?.let { commitment ->
                                discardUnschedulableCommitment(
                                    commitment,
                                    "Assistant configuration no longer exists",
                                )
                            }
                    }
                    stopSelf()
                    return@launch
                }
                val assistantUuid = assistant.id
                if (isDurableCommitmentTrigger) {
                    val started = companionRuntime.beginCommitment(
                        assistantId = assistantUuid.toString(),
                        commitmentId = targetedCommitmentId.orEmpty(),
                        nowMillis = System.currentTimeMillis(),
                    )
                    if (started == null) {
                        Log.d(TAG, "Commitment is missing, already handled, or not due: $targetedCommitmentId")
                        scheduleNextDurableCommitment()
                        stopSelf()
                        return@launch
                    }
                    executingCommitment = started
                }
                val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)

                if (model == null) {
                    Log.e(ProactiveMessageService.TAG, "No model found for proactive message")
                    val activeCommitment = executingCommitment
                    if (activeCommitment != null) {
                        failDurableCommitment(
                            commitment = activeCommitment,
                            summary = "No chat model configured for proactive execution",
                        )
                        executingCommitment = null
                    } else {
                        ProactiveMessageService.scheduleNext(
                            context = this@ProactiveMessageTriggerService,
                            settings = settings,
                            assistantId = assistantUuid,
                        )
                    }
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
                val latestUserMessageAt = historyMessages
                    .lastOrNull { it.role == MessageRole.USER }
                    ?.createdAt
                    ?.toInstant(TimeZone.currentSystemDefault())
                    ?.toEpochMilliseconds()

                val allTools = buildAllTools(settings, assistant)
                    .deduplicateByToolName()
                    .selectRelevantToolsForPrompt(historyMessages)
                val activeTools = allTools.activeModelTools()
                val nowMillis = System.currentTimeMillis()
                val screenInteractive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                val passiveFacts = collectCompanionPassivePerceptionFacts(
                    tools = allTools,
                    observedAt = nowMillis,
                ) + CompanionContextFact(
                    key = "perception.screen_interactive",
                    value = screenInteractive.toString(),
                    observedAt = nowMillis,
                )
                val memoryQuery = listOfNotNull(
                    targetedReason,
                    targetedUserText,
                    executingCommitment?.promise,
                    historyMessages.lastOrNull { it.role == MessageRole.USER }?.toText(),
                ).filter(String::isNotBlank).joinToString("\n")
                val memoryContext = memoryBankService.buildRecallContext(
                    assistantId = assistantUuid.toString(),
                    query = memoryQuery,
                )
                val companionContext = companionRuntime.perception(
                    CompanionPerceptionInput(
                        assistantId = assistantUuid.toString(),
                        assistantName = assistant.name,
                        persona = assistant.toLuluPlannerPersona(
                            messages = historyMessages,
                            settings = settings,
                        ),
                        conversationId = conversationId.toString(),
                        recentTurns = historyMessages.takeLast(12).map { message ->
                            CompanionConversationTurn(
                                role = when (message.role) {
                                    MessageRole.USER -> CompanionTurnRole.USER
                                    MessageRole.ASSISTANT -> CompanionTurnRole.ASSISTANT
                                    MessageRole.SYSTEM -> CompanionTurnRole.SYSTEM
                                    MessageRole.TOOL -> CompanionTurnRole.TOOL
                                },
                                content = message.toText(),
                                createdAt = message.createdAt
                                    .toInstant(TimeZone.currentSystemDefault())
                                    .toEpochMilliseconds(),
                                sourceId = message.id.toString(),
                            )
                        },
                        contextFacts = listOfNotNull(
                            minutesSinceLastChat?.let { minutes ->
                                CompanionContextFact(
                                    key = "minutes_since_previous_interaction",
                                    value = minutes.toString(),
                                    observedAt = nowMillis,
                                )
                            },
                        ) + passiveFacts,
                        availableToolNames = activeTools.map { it.name }.toSet(),
                        memoryContext = memoryContext,
                        nowMillis = nowMillis,
                    ),
                ).toPromptContext()
                executingCommitment?.let { commitment ->
                    val wakeTargetAt = commitment.wakeTargetAtOrNull()
                    val completedBeforeMessage = when {
                        commitment.isWakeGoal() && wakeTargetAt != null -> shouldCompleteWakeGoal(
                            wakeTargetAt = wakeTargetAt,
                            latestUserMessageAt = latestUserMessageAt,
                            perceivedUserState = null,
                        )
                        commitment.isSleepSupervisionGoal() && wakeTargetAt != null ->
                            shouldCompleteSleepSupervision(
                                wakeTargetAt = wakeTargetAt,
                                nowMillis = nowMillis,
                                observationDueAt = commitment.dueAt,
                                latestUserMessageAt = latestUserMessageAt,
                                screenInteractive = screenInteractive,
                                perceivedUserState = null,
                            )
                        else -> false
                    }
                    if (completedBeforeMessage) {
                        companionRuntime.finishCommitment(
                            assistantId = commitment.assistantId,
                            commitmentId = commitment.id,
                            result = CompanionActionResult(
                                success = true,
                                summary = if (commitment.isWakeGoal()) {
                                    "User activity confirmed after the wake target"
                                } else {
                                    "User appears to have stopped using the phone and started resting"
                                },
                                completedAt = nowMillis,
                            ),
                        )
                        executingCommitment = null
                        scheduleNextDurableCommitment()
                        stopSelf()
                        return@launch
                    }
                }
                val autonomousPlan = if (isTargetedTrigger) {
                    null
                } else {
                    buildAutonomousIntentPlan(
                        settings = settings,
                        assistant = assistant,
                        historyMessages = historyMessages,
                        availableToolNames = activeTools.map { it.name }.toSet(),
                        passiveFacts = passiveFacts,
                        memoryContext = memoryContext,
                    )
                }
                if (!isTargetedTrigger && autonomousPlan?.intent == CompanionIntent.WAIT) {
                    Log.d(TAG, "Companion intent planner chose not to disturb")
                    runCatching {
                        persistCompanionState(
                            assistant = assistant,
                            state = buildAutonomousPlanPresenceState(
                                previous = companionRuntime.snapshot(assistant.id.toString()).state,
                                assistantName = assistant.name,
                                plan = autonomousPlan,
                                nowMillis = nowMillis,
                            ),
                            nowMillis = nowMillis,
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to persist waiting companion state", error)
                    }
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        minutesSinceLastChat = minutesSinceLastChat,
                        assistantId = assistantUuid,
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
                    autonomousPlan?.let { plan ->
                        runCatching {
                            persistCompanionState(
                                assistant = assistant,
                                state = buildAutonomousPlanPresenceState(
                                    previous = companionRuntime.snapshot(assistant.id.toString()).state,
                                    assistantName = assistant.name,
                                    plan = plan,
                                    nowMillis = nowMillis,
                                ),
                                nowMillis = nowMillis,
                            )
                        }.onFailure { error ->
                            Log.w(TAG, "Failed to persist deferred companion state", error)
                        }
                    }
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

                val effectiveTargetedReason = targetedReason
                    ?: executingCommitment?.promise
                    ?: autonomousPlan?.toImmediateReason(assistant.name)
                val effectiveTargetedKind = targetedKind
                    ?: executingCommitment?.actionPlan?.category?.takeIf { it.isNotBlank() }
                    ?: autonomousPlan
                    ?.takeIf { it.shouldMessageNow }
                    ?.intent
                    ?.name
                    ?.lowercase(java.util.Locale.ROOT)

                // 构建上下文
                val contextStr = buildString {
                    appendLine(companionContext)
                    if (memoryContext.isNotBlank()) {
                        appendLine()
                        appendLine(memoryContext)
                    }
                    appendLine()
                    append(
                        proactiveMessageService.buildProactiveContext(
                            context = this@ProactiveMessageTriggerService,
                            settings = settings,
                            assistant = assistant,
                            minutesSinceLastChat = minutesSinceLastChat,
                            targetedReason = effectiveTargetedReason,
                            targetedUserText = targetedUserText ?: executingCommitment?.actionPlan?.contextText,
                            targetedKind = effectiveTargetedKind,
                        ),
                    )
                }

                // 构建系统提示词（包含记忆）
                val systemPrompt = buildSystemPrompt(assistant)

                // 构建用户上下文消息
                val userMessage = UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(
                        contextStr + "\n\n如果你觉得现在没什么好说的，或者没什么有趣的话题，请只回复 [PASS] 即可，不要强行找话题。"
                    ))
                )

                // 合并相邻 assistant 消息，避免部分 API 拒绝连续 assistant 角色。
                val fixedHistoryMessages = historyMessages.fold(emptyList<UIMessage>()) { acc, msg ->
                    if (acc.isNotEmpty() && acc.last().role == MessageRole.ASSISTANT && msg.role == MessageRole.ASSISTANT) {
                        acc.dropLast(1) + acc.last().copy(parts = acc.last().parts + msg.parts)
                    } else {
                        acc + msg
                    }
                }

                val messages = composeProactiveGenerationMessages(
                    systemPrompt = systemPrompt,
                    historyMessages = fixedHistoryMessages,
                    currentContext = userMessage,
                ).transforms(
                    transformers = companionInputTransformers + templateTransformer,
                    context = this@ProactiveMessageTriggerService,
                    model = model,
                    assistant = assistant,
                    settings = settings,
                )

                val preferredToolNames = buildList {
                    executingCommitment?.actionPlan?.toolName?.let(::add)
                    addAll(executingCommitment?.actionPlan?.preferredToolNames.orEmpty())
                    addAll(autonomousPlan?.toolNames.orEmpty())
                }
                val tools = activeTools
                    .selectCompanionToolsForGeneration(
                        messages = historyMessages + userMessage,
                        preferredToolNames = preferredToolNames,
                    )
                    .withConciseToolDescriptions()
                    .withHumanLikeToolPrompts()

                // 直接调用 AI API 生成消息
                val providerSetting = model.findProvider(settings.providers)
                if (providerSetting == null) {
                    Log.e(ProactiveMessageService.TAG, "No provider found for proactive message")
                    val activeCommitment = executingCommitment
                    if (activeCommitment != null) {
                        failDurableCommitment(
                            commitment = activeCommitment,
                            summary = "No provider configured for proactive execution",
                        )
                        executingCommitment = null
                    } else {
                        ProactiveMessageService.scheduleNext(
                            context = this@ProactiveMessageTriggerService,
                            settings = settings,
                            minutesSinceLastChat = minutesSinceLastChat,
                            assistantId = assistantUuid,
                        )
                    }
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
                val finalPresence = finalMessages.companionModelPresence()

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
                    runCatching {
                        persistProactiveCompanionState(
                            assistant = assistant,
                            assistantText = replyText,
                            presence = finalPresence,
                            nowMillis = System.currentTimeMillis(),
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to persist proactive companion state projection", error)
                    }
                    showProactiveNotification(conversationId, assistant.name.ifBlank { "AI" }, replyText)
                }

                executingCommitment?.let { commitment ->
                    val completedAt = System.currentTimeMillis()
                    val actionResult = CompanionActionResult(
                        success = true,
                        summary = if (replyText.isBlank() || replyText.contains("[PASS]")) {
                            "Reappraised at the promised time; no message was needed"
                        } else {
                            "Proactive message delivered"
                        },
                        completedAt = completedAt,
                        outputReference = conversationId.toString(),
                    )
                    completedCommitmentResult = actionResult
                    val wakeTargetAt = commitment.wakeTargetAtOrNull()
                    val perceivedUserState = finalPresence?.userState
                    val shouldContinue = when {
                        commitment.isWakeGoal() && wakeTargetAt != null -> !shouldCompleteWakeGoal(
                            wakeTargetAt = wakeTargetAt,
                            latestUserMessageAt = latestUserMessageAt,
                            perceivedUserState = perceivedUserState,
                        )
                        commitment.isSleepSupervisionGoal() && wakeTargetAt != null ->
                            !shouldCompleteSleepSupervision(
                                wakeTargetAt = wakeTargetAt,
                                nowMillis = completedAt,
                                observationDueAt = commitment.dueAt,
                                latestUserMessageAt = latestUserMessageAt,
                                screenInteractive = screenInteractive,
                                perceivedUserState = perceivedUserState,
                            )
                        else -> false
                    }
                    if (shouldContinue) {
                        val retryMinutes = commitment.retryMinutesOrDefault()
                        val nextDueAt = completedAt + TimeUnit.MINUTES.toMillis(retryMinutes.toLong())
                        if (commitment.isWakeGoal()) {
                            scheduleWakeContinuationAlarm(
                                tools = tools,
                                triggerAtMillis = nextDueAt,
                                assistantName = assistant.name,
                            )
                        }
                        companionRuntime.continueCommitment(
                            assistantId = commitment.assistantId,
                            commitmentId = commitment.id,
                            result = actionResult.copy(
                                summary = if (commitment.isWakeGoal()) {
                                    "Wake attempt completed; user is not yet confirmed awake. " +
                                        buildContinuationObservationSummary(passiveFacts, screenInteractive)
                                } else {
                                    "Sleep supervision checked; user still appears active. " +
                                        buildContinuationObservationSummary(passiveFacts, screenInteractive)
                                },
                            ),
                            nextDueAt = nextDueAt,
                        )
                    } else {
                        companionRuntime.finishCommitment(
                            assistantId = commitment.assistantId,
                            commitmentId = commitment.id,
                            result = actionResult,
                        )
                    }
                    executingCommitment = null
                    completedCommitmentResult = null
                }

                val hasNextTargeted = if (isDurableCommitmentTrigger) {
                    scheduleNextDurableCommitment()
                } else if (isTargetedTrigger) {
                    ProactiveMessageService.popCurrentTargetedAndScheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        setting = proactiveSetting,
                    )
                } else {
                    false
                }
                if (isTargetedTrigger && !hasNextTargeted) {
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        minutesSinceLastChat = 0L,
                        assistantId = assistantUuid,
                    )
                }
                if (!isTargetedTrigger) {
                    ProactiveMessageService.scheduleNext(
                        context = this@ProactiveMessageTriggerService,
                        settings = settings,
                        minutesSinceLastChat = 0L,
                        assistantId = assistantUuid,
                    )
                }
            } catch (e: Exception) {
                Log.e(ProactiveMessageService.TAG, "Failed to trigger proactive message", e)
                val activeCommitment = executingCommitment
                if (activeCommitment != null) {
                    runCatching {
                        val completedResult = completedCommitmentResult
                        if (completedResult != null) {
                            companionRuntime.finishCommitment(
                                assistantId = activeCommitment.assistantId,
                                commitmentId = activeCommitment.id,
                                result = completedResult,
                            )
                            scheduleNextDurableCommitment()
                        } else {
                            failDurableCommitment(
                                commitment = activeCommitment,
                                summary = (e.message ?: e::class.simpleName ?: "Proactive execution failed").take(500),
                            )
                        }
                    }.onFailure { persistenceError ->
                        Log.e(TAG, "Failed to persist proactive commitment failure", persistenceError)
                    }
                    executingCommitment = null
                }
            } finally {
                conversationId?.let { chatService.removeConversationReference(it) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun failDurableCommitment(
        commitment: CompanionCommitment,
        summary: String,
    ) {
        val completedAt = System.currentTimeMillis()
        if (commitment.isWakeGoal() || commitment.isSleepSupervisionGoal()) {
            companionRuntime.continueCommitment(
                assistantId = commitment.assistantId,
                commitmentId = commitment.id,
                result = CompanionActionResult(
                    success = false,
                    summary = summary,
                    completedAt = completedAt,
                ),
                nextDueAt = completedAt + TimeUnit.MINUTES.toMillis(
                    commitment.retryMinutesOrDefault().toLong(),
                ),
            )
            scheduleNextDurableCommitment()
            return
        }
        val retryAt = if (commitment.attemptCount < MAX_COMMITMENT_ATTEMPTS) {
            completedAt + TimeUnit.MINUTES.toMillis(
                (COMMITMENT_RETRY_MINUTES * commitment.attemptCount.coerceAtLeast(1)).coerceAtMost(60L),
            )
        } else {
            null
        }
        companionRuntime.finishCommitment(
            assistantId = commitment.assistantId,
            commitmentId = commitment.id,
            result = CompanionActionResult(
                success = false,
                summary = summary,
                completedAt = completedAt,
            ),
            retryAt = retryAt,
        )
        scheduleNextDurableCommitment()
    }

    private suspend fun scheduleWakeContinuationAlarm(
        tools: List<Tool>,
        triggerAtMillis: Long,
        assistantName: String,
    ) {
        val alarmTool = tools.firstOrNull { it.name == "set_alarm" } ?: return
        val target = Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault())
        runCatching {
            alarmTool.execute(
                JsonObject(
                    mapOf(
                        "hour" to JsonPrimitive(target.hour),
                        "minute" to JsonPrimitive(target.minute),
                        "label" to JsonPrimitive("${assistantName.ifBlank { "当前角色" }}继续叫你起床"),
                    )
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to schedule continued wake alarm", error)
        }
    }

    private suspend fun persistProactiveCompanionState(
        assistant: Assistant,
        assistantText: String,
        presence: CompanionModelPresence?,
        nowMillis: Long,
    ) {
        val unifiedState = buildCompanionStateFromTurn(
            previous = companionRuntime.snapshot(assistant.id.toString()).state,
            assistantText = assistantText,
            presence = presence,
            nowMillis = nowMillis,
        )
        persistCompanionState(assistant, unifiedState, nowMillis)
    }

    private suspend fun persistCompanionState(
        assistant: Assistant,
        state: CompanionState,
        nowMillis: Long,
    ) {
        companionRuntime.applyTurn(
            CompanionTurnMutation(
                assistantId = assistant.id.toString(),
                state = state,
                nowMillis = nowMillis,
            ),
        )
    }

    private suspend fun scheduleNextDurableCommitment(): Boolean {
        val settings = settingsStore.settingsFlow.first()
        repeat(MAX_SCHEDULER_CANDIDATES) {
            val next = companionRuntime.nextCommitment() ?: run {
                clearDurableCommitmentProjection()
                return false
            }
            val assistantUuid = runCatching { Uuid.parse(next.assistantId) }.getOrNull()
            if (assistantUuid == null) {
                discardUnschedulableCommitment(next, "Commitment has an invalid assistant ID")
                return@repeat
            }
            val setting = settings.getProactiveMessageSetting(assistantUuid)
            if (!setting.enabled) {
                discardUnschedulableCommitment(next, "Proactive messaging disabled")
                return@repeat
            }
            ProactiveMessageService.scheduleCommitment(
                context = this,
                setting = setting,
                commitment = next,
            )
            return true
        }
        clearDurableCommitmentProjection()
        return false
    }

    private suspend fun discardUnschedulableCommitment(
        commitment: CompanionCommitment,
        reason: String,
    ) {
        val nowMillis = System.currentTimeMillis()
        if (commitment.status == CompanionCommitmentStatus.EXECUTING) {
            companionRuntime.finishCommitment(
                assistantId = commitment.assistantId,
                commitmentId = commitment.id,
                result = CompanionActionResult(
                    success = false,
                    summary = reason,
                    completedAt = nowMillis,
                ),
            )
        } else {
            companionRuntime.cancelCommitment(
                assistantId = commitment.assistantId,
                commitmentId = commitment.id,
                reason = reason,
                nowMillis = nowMillis,
            )
        }
    }

    private fun clearDurableCommitmentProjection() {
        getSharedPreferences(ProactiveMessageService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(ProactiveMessageService.KEY_TARGETED_TRIGGER_TIME)
            .remove(ProactiveMessageService.KEY_TARGETED_REASON)
            .remove(ProactiveMessageService.KEY_TARGETED_USER_TEXT)
            .remove(ProactiveMessageService.KEY_TARGETED_KIND)
            .remove(ProactiveMessageService.KEY_TARGETED_COMMITMENT_ID)
            .apply()
        ProactiveMessageWorker.cancelTargeted(this)
    }

    private fun buildSystemPrompt(assistant: Assistant): String = assistant.systemPrompt

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

    private fun buildAllTools(
        settings: Settings,
        assistant: Assistant,
    ): List<Tool> {
        return buildList {
            add(createTodayStudyPlanTool())

            // 搜索工具
            if (settings.enableWebSearch) {
                addAll(createSearchTools(settings))
            }
            
            // 本地工具
            addAll(localTools.getTools(assistant.localTools, assistant.id.toString()))
            
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
    }

    private suspend fun buildAutonomousIntentPlan(
        settings: Settings,
        assistant: Assistant,
        historyMessages: List<UIMessage>,
        availableToolNames: Set<String>,
        passiveFacts: List<CompanionContextFact>,
        memoryContext: String,
    ): CompanionIntentDecision {
        val nowMillis = System.currentTimeMillis()
        val minutesSinceLastChat = historyMessages.lastOrNull()
            ?.createdAt
            ?.toInstant(TimeZone.currentSystemDefault())
            ?.toEpochMilliseconds()
            ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0) }
            ?: Long.MAX_VALUE
        val perception = companionRuntime.perception(
            CompanionPerceptionInput(
                assistantId = assistant.id.toString(),
                assistantName = assistant.name,
                persona = assistant.toLuluPlannerPersona(
                    messages = historyMessages,
                    settings = settings,
                ),
                recentTurns = historyMessages.takeLast(12).map { message ->
                    CompanionConversationTurn(
                        role = when (message.role) {
                            MessageRole.USER -> CompanionTurnRole.USER
                            MessageRole.ASSISTANT -> CompanionTurnRole.ASSISTANT
                            MessageRole.SYSTEM -> CompanionTurnRole.SYSTEM
                            MessageRole.TOOL -> CompanionTurnRole.TOOL
                        },
                        content = message.toText(),
                        createdAt = message.createdAt
                            .toInstant(TimeZone.currentSystemDefault())
                            .toEpochMilliseconds(),
                        sourceId = message.id.toString(),
                    )
                },
                contextFacts = listOf(
                    CompanionContextFact(
                        key = "minutes_since_previous_interaction",
                        value = minutesSinceLastChat.toString(),
                        observedAt = nowMillis,
                    ),
                ) + passiveFacts,
                availableToolNames = availableToolNames,
                memoryContext = memoryContext,
                nowMillis = nowMillis,
            ),
        )
        val input = CompanionIntentInput(
            perception = perception,
            mode = CompanionDecisionMode.BACKGROUND,
            minutesSinceLastChat = minutesSinceLastChat,
        )
        val modelPlan = settings.luluIntentModelId
            ?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }
            ?.let { plannerModel ->
                runCatching {
                    CompanionIntentModelPlanner.planOrNull(
                        input = input,
                        settings = settings,
                        model = plannerModel,
                        providerManager = providerManager,
                    )
                }.getOrNull()
            }
        return modelPlan ?: CompanionIntentFallbackPlanner.plan(input)
    }

    private fun Assistant.toLuluPlannerPersona(
        messages: List<UIMessage>,
        settings: Settings,
    ): String = buildString {
        appendLine("角色名：${name.ifBlank { "当前角色" }}")
        if (systemPrompt.isNotBlank()) {
            appendLine("系统人设：")
            appendLine(systemPrompt.take(1600))
        }
        if (appearancePrompt.isNotBlank()) {
            appendLine("外貌设定：")
            appendLine(appearancePrompt.take(500))
        }
        if (messageTemplate.isNotBlank() && messageTemplate != "{{ message }}") {
            appendLine("语言/消息模板：")
            appendLine(messageTemplate.take(400))
        }
        buildPromptInjectionPlannerContext(
            messages = messages,
            assistant = this@toLuluPlannerPersona,
            modeInjections = settings.modeInjections,
            lorebooks = settings.lorebooks,
        ).takeIf(String::isNotBlank)?.let { injectionContext ->
            appendLine("模式与世界书：")
            appendLine(injectionContext.take(1600))
        }
    }.trim()

    private fun CompanionIntentDecision.toImmediateReason(assistantName: String): String = buildString {
        appendLine("${assistantName.ifBlank { "当前角色" }}刚刚自主判断现在适合主动找用户。")
        appendLine("主动意图: ${intent.name}")
        appendLine("触发原因: $reason")
        appendLine("语气: $tone")
        if (toolNames.isNotEmpty()) {
            appendLine("生成回复前优先结合这些感知项或行动能力：${toolNames.joinToString("、")}。被动感知无需重复调用。")
        }
    }.trim()

    private fun ProactiveReminderPlan.toTargetedReason(): String = buildString {
        appendLine(reason)
        if (preferredToolNames.isNotEmpty()) {
            appendLine("到点时优先结合这些感知项或行动能力：${preferredToolNames.joinToString("、")}。被动感知无需重复调用。")
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
                transformers = companionOutputTransformers,
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
                    transformers = companionOutputTransformers,
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

            // 执行工具：后台主动消息把工具当作角色的本地感知/行动能力。
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
                    Log.d(TAG, "Executing proactive tool ${toolDef.name} with args: $args")
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

internal fun shouldResetProactiveProjection(
    projectedAssistantId: String?,
    clearedAssistantId: String,
): Boolean = clearedAssistantId.isNotBlank() && projectedAssistantId == clearedAssistantId

internal fun buildAutonomousPlanPresenceState(
    previous: CompanionState,
    assistantName: String,
    plan: CompanionIntentDecision,
    nowMillis: Long = System.currentTimeMillis(),
): CompanionState {
    val innerVoice = plan.innerThought.cleanSilentInnerVoice()
        ?: when (plan.intent) {
            CompanionIntent.FOLLOW_UP -> "我把这件明确的后续记着，到点会重新看真实情况。"
            CompanionIntent.STAY_AVAILABLE -> "我先不打断，把注意留在这里，等下一次有意义的变化。"
            CompanionIntent.REACH_OUT -> "安静了一阵，我想按自己的人设确认现在是否适合开口。"
            CompanionIntent.OBSERVE -> "我先重新看清上下文，再决定行动和表达。"
            CompanionIntent.WAIT -> "现在没有足够理由打扰，我先保持安静。"
        }
    val name = assistantName.ifBlank { "当前角色" }
    return previous.copy(
        statusText = when (plan.intent) {
            CompanionIntent.WAIT -> "安静留意"
            CompanionIntent.STAY_AVAILABLE, CompanionIntent.OBSERVE -> "在心里记着"
            CompanionIntent.FOLLOW_UP -> "记着后续"
            CompanionIntent.REACH_OUT -> "想主动联系"
        },
        innerThought = innerVoice,
        mindState = when (plan.intent) {
            CompanionIntent.WAIT -> "安静留意着现在的变化"
            CompanionIntent.STAY_AVAILABLE -> "把注意留在这里"
            CompanionIntent.REACH_OUT -> "想着怎样自然开口"
            CompanionIntent.OBSERVE -> "重新确认现在的情况"
            CompanionIntent.FOLLOW_UP -> "记着接下来要照看的事"
        },
        activityMode = if (plan.intent == CompanionIntent.OBSERVE) "observing" else "waiting",
        updatedAt = nowMillis,
        sinceAt = nowMillis,
        selfScene = when (plan.intent) {
            CompanionIntent.WAIT -> "$name 暂时没有开口，只是继续留意接下来的变化。"
            CompanionIntent.STAY_AVAILABLE -> "$name 把注意力留在这里，安静等着下一次有意义的变化。"
            CompanionIntent.OBSERVE -> "$name 正在重新看清现在的情况，还没有急着开口。"
            CompanionIntent.FOLLOW_UP -> "$name 把要继续确认的事记在心里，等合适的时候回来。"
            CompanionIntent.REACH_OUT -> "$name 正在斟酌怎样自然地开口联系你。"
        },
    )
}

internal fun composeProactiveGenerationMessages(
    systemPrompt: String,
    historyMessages: List<UIMessage>,
    currentContext: UIMessage,
): List<UIMessage> = buildList {
    if (systemPrompt.isNotBlank()) add(UIMessage.system(systemPrompt))
    addAll(historyMessages)
    add(currentContext)
}

internal fun shouldCompleteWakeGoal(
    wakeTargetAt: Long,
    latestUserMessageAt: Long?,
    perceivedUserState: String?,
): Boolean = latestUserMessageAt?.let { it >= wakeTargetAt } == true ||
    perceivedUserState.equals("awake", ignoreCase = true)

internal fun shouldCompleteSleepSupervision(
    wakeTargetAt: Long,
    nowMillis: Long,
    observationDueAt: Long,
    latestUserMessageAt: Long?,
    screenInteractive: Boolean,
    perceivedUserState: String?,
): Boolean {
    if (nowMillis >= wakeTargetAt) return true
    if (perceivedUserState.equals("asleep", ignoreCase = true)) return true
    val userActiveAfterObservation = latestUserMessageAt?.let { it >= observationDueAt } == true
    return !screenInteractive && !userActiveAfterObservation
}

private fun buildContinuationObservationSummary(
    passiveFacts: List<CompanionContextFact>,
    screenInteractive: Boolean,
): String = buildString {
    append("screen_interactive=$screenInteractive")
    passiveFacts
        .filter { fact ->
            fact.key in setOf(
                "perception.get_location",
                "perception.get_app_usage",
                "perception.get_gadgetbridge_data",
                "perception.get_weather",
            )
        }
        .forEach { fact ->
            append("; ${fact.key.removePrefix("perception.")}=${fact.value.take(100)}")
        }
}.take(420)


private fun String.cleanSilentInnerVoice(): String? {
    val compact = trim()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
    if (compact.isBlank()) return null
    val technicalMarkers = listOf(
        "Seven-layer trace",
        "Perception=",
        "requested_tools=",
        "tool_result[",
        "JSON 字段",
    )
    if (technicalMarkers.any { marker -> compact.contains(marker, ignoreCase = true) }) return null
    return compact.take(180)
}

internal fun recentFormalDiaryEntries(
    entries: List<CihaiEntry>,
    assistantId: String,
    limit: Int = 3,
): List<CihaiEntry> = entries
    .filter { entry ->
        entry.assistantId == assistantId && entry.kind == CihaiEntryKind.DIARY
    }
    .sortedBy { it.createdAt }
    .takeLast(limit.coerceAtLeast(0))

internal fun buildTargetedProactiveSensingInstruction(
    targetedKind: String?,
    targetedReason: String?,
): String = buildString {
    val reason = targetedReason.orEmpty()
    when (targetedKind) {
        "wake" -> {
            appendLine("本次目标是持续叫醒用户：先看目标时间后用户是否发过消息，再看屏幕/应用使用、健康活动、位置移动、天气和电量。")
            appendLine("把承诺中的 last_result 当作上一次观察，与本轮位置和应用状态比较；明显移动、目标时间后新消息或持续主动使用手机可以支持 awake，仍在原地且缺少活动只能判断 asleep/uncertain。")
            appendLine("没有足够证据确认用户已经醒来时，继续叫醒并补下一个短间隔闹钟；不能因为已经发过一次消息就把目标视为完成。")
        }
        "sleep", "sleep_supervision" -> {
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
            appendLine("本次目标的感知重点：先看当前时间、应用使用和电量；没有明确行动价值时保持安静，不要为了记录判断过程而写辞海。")
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
