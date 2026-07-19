package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.SystemTools
import me.rerere.rikkahub.data.ai.tools.SystemToolOption
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createTodayStudyPlanTool
import me.rerere.rikkahub.data.ai.tools.createCompanionGameTool
import me.rerere.rikkahub.data.ai.tools.activeModelTools
import me.rerere.rikkahub.data.ai.tools.deduplicateByToolName
import me.rerere.rikkahub.data.ai.tools.selectRelevantToolsForPrompt
import me.rerere.rikkahub.data.ai.tools.selectCompanionToolsForGeneration
import me.rerere.rikkahub.data.ai.tools.withConciseToolDescriptions
import me.rerere.rikkahub.data.ai.tools.withHumanLikeToolPrompts
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.plugin.loader.PluginLoader
import me.rerere.rikkahub.plugin.provider.PluginToolProvider
import me.rerere.rikkahub.data.ai.transformers.buildPromptInjectionPlannerContext
import me.rerere.rikkahub.data.ai.transformers.companionInputTransformers
import me.rerere.rikkahub.data.ai.transformers.companionModelPresence
import me.rerere.rikkahub.data.ai.transformers.companionOutputTransformers
import me.rerere.rikkahub.data.ai.transformers.COMPANION_INCOMPLETE_REPLY_MARKER
import me.rerere.rikkahub.data.ai.transformers.sanitizeLuluVisibleExpression
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import me.rerere.rikkahub.data.companion.CompanionActionType
import me.rerere.rikkahub.data.companion.CompanionConcernChange
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchorKind
import me.rerere.rikkahub.data.companion.detectAlwaysOnAnchorCancellations
import me.rerere.rikkahub.data.companion.detectAlwaysOnAnchors
import me.rerere.rikkahub.data.companion.detectExplicitRecurringResponsibilityAnchors
import me.rerere.rikkahub.data.companion.detectExplicitRecurringResponsibilityCancellations
import me.rerere.rikkahub.data.companion.mergeAlwaysOnResponsibilityAnchors
import me.rerere.rikkahub.data.companion.CompanionContinuity
import me.rerere.rikkahub.data.companion.CompanionInteractionModality
import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.data.companion.CompanionConversationTurn
import me.rerere.rikkahub.data.companion.CompanionFollowUpDraft
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionPerceptionPacket
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.CompanionToolExecution
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.buildToolLifeEvent
import me.rerere.rikkahub.data.companion.buildScheduledToolFollowUp
import me.rerere.rikkahub.data.companion.CompanionTurnRole
import me.rerere.rikkahub.data.companion.buildCompanionStateFromTurn
import me.rerere.rikkahub.data.companion.buildCompanionResponsibilityContext
import me.rerere.rikkahub.data.companion.commitmentStatusesBySourceMessageId
import me.rerere.rikkahub.data.companion.isSleepSupervisionGoal
import me.rerere.rikkahub.data.companion.isWakeGoal
import me.rerere.rikkahub.data.companion.reconcileCompanionFollowUpDrafts
import me.rerere.rikkahub.data.companion.toAlwaysOnAnchorOrNull
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.companion.wakeTargetAtOrNull
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.service.AffectiveMemoryExtractor
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.data.service.MemoryExtractionDirection
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.buildAffectiveMemoryExtractionPlan
import me.rerere.rikkahub.data.service.buildDeterministicMemoryCandidates
import me.rerere.rikkahub.data.service.buildCompanionPrivateImpression
import me.rerere.rikkahub.data.service.buildRelationshipEventsFromMemoryCandidates
import me.rerere.rikkahub.data.service.isDurableMemoryCandidate
import me.rerere.rikkahub.data.service.normalizedMemoryIdentity
import me.rerere.rikkahub.data.service.syncCompanionPrivateImpression
import me.rerere.rikkahub.data.service.toMemoryExtractionTurns
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "ChatService"
private const val MAX_HISTORICAL_MEMORY_BATCHES_PER_CONVERSATION = 200
private const val MEMORY_OCCURRENCE_FUTURE_TOLERANCE_MS = 24L * 60 * 60 * 1_000

enum class MemoryReorganizationMode {
    RECENT_BATCH,
    CONTINUE_HISTORY,
    FULL_REBUILD,
}

data class MemoryReorganizationProgress(
    val running: Boolean = false,
    val mode: MemoryReorganizationMode = MemoryReorganizationMode.RECENT_BATCH,
    val assistantId: String? = null,
    val totalConversations: Int = 0,
    val currentConversation: Int = 0,
    val completedBatches: Int = 0,
    val failedBatches: Int = 0,
    val currentBatchStartAt: Long? = null,
    val currentBatchEndAt: Long? = null,
    val repairedTemporalRows: Int = 0,
    val message: String = "",
)

internal fun buildDirectFactAnswerGuard(
    userText: String,
    passiveFacts: List<CompanionContextFact>,
    toolExecutions: List<CompanionToolExecution>,
): String {
    if (!asksForCurrentLocation(userText)) return ""
    val freshOutputs = toolExecutions
        .asReversed()
        .filter { it.toolName == "get_location" }
        .map { it.outputText }
    val passiveOutputs = passiveFacts
        .asReversed()
        .filter { it.key == "perception.get_location" }
        .map { it.value }
    val location = (freshOutputs + passiveOutputs)
        .firstNotNullOfOrNull(::confirmedLocationSummary)
        ?: return ""
    return buildString {
        appendLine("<direct_fact_answer priority=\"highest\">")
        appendLine("用户本轮正在直接询问自己的当前位置。程序已经确认的位置事实：$location")
        appendLine("必须结合角色人设自然、明确地回答这个位置；不得回答不知道、无法查看、没有定位或让用户自己确认。")
        appendLine("不要提工具、JSON、后台输入或数据采集，只把位置当作你此刻确实知道的事实。")
        append("</direct_fact_answer>")
    }
}

private fun confirmedLocationSummary(raw: String): String? {
    val root = runCatching { JsonInstant.parseToJsonElement(raw.trim()) as? JsonObject }.getOrNull() ?: return null
    if (root["success"]?.jsonPrimitive?.contentOrNull != "true") return null
    fun field(name: String): String? = root[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown address", ignoreCase = true) }
    val addressParts = listOf("place_name", "address", "city", "district", "street")
        .mapNotNull(::field)
        .distinct()
    val latitude = field("latitude")
    val longitude = field("longitude")
    val coordinates = if (latitude != null && longitude != null) "$latitude,$longitude" else null
    val summaryParts = buildList {
        if (addressParts.isNotEmpty()) add("地址=${addressParts.joinToString("；")}")
        coordinates?.let { add("坐标=$it") }
    }
    return summaryParts.takeIf { it.isNotEmpty() }?.joinToString("；")
}

private val WAKE_CANCELLATION_MARKERS = setOf(
    "不用叫",
    "别叫我",
    "取消叫醒",
    "取消闹钟",
    "不用喊我",
    "我自己会起",
)
private val SLEEP_CONFIRMATION_MARKERS = setOf(
    "我睡了",
    "去睡了",
    "现在睡",
    "关手机睡觉",
    "晚安我睡了",
)
data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckTitleModelSettings,
}

internal fun persistChatError(error: ChatError) {
    Logging.log(
        tag = error.title ?: "ChatError",
        message = error.error.stackTraceToString(),
    )
}

internal fun shouldGenerateTitle(title: String, force: Boolean): Boolean = force

internal fun appendVoiceCallVisibleTurn(
    conversation: Conversation,
    userText: String?,
    assistantText: String?,
    assistantMessage: UIMessage? = null,
): Conversation {
    val visibleNodes = buildList {
        val cleanUserText = userText?.trim().orEmpty()
        val cleanAssistantText = assistantText?.trim().orEmpty()
        if (cleanUserText.isNotBlank()) {
            add(UIMessage.user(cleanUserText).toMessageNode())
        }
        val cleanAssistantMessage = assistantMessage?.takeIf {
            it.role == MessageRole.ASSISTANT && it.toText().trim().isNotBlank()
        }
        if (cleanAssistantMessage != null) {
            add(cleanAssistantMessage.toMessageNode())
        } else if (cleanAssistantText.isNotBlank()) {
            add(UIMessage.assistant(cleanAssistantText).toMessageNode())
        }
    }
    if (visibleNodes.isEmpty()) return conversation

    return conversation.copy(
        messageNodes = conversation.messageNodes + visibleNodes,
        updateAt = Instant.now(),
    )
}

internal fun Conversation.withoutVoiceCallInstructionLeaks(): Conversation {
    val cleanedNodes = messageNodes.filterNot { node ->
        val message = node.currentMessage
        message.role == MessageRole.USER && message.toText().isVoiceCallInternalInstruction()
    }
    return if (cleanedNodes.size == messageNodes.size) {
        this
    } else {
        copy(messageNodes = cleanedNodes, updateAt = Instant.now())
    }
}

private fun String.isVoiceCallInternalInstruction(): Boolean {
    val text = trim()
    if (text.isBlank()) return false
    val hasVoiceCallContext = text.contains("电话接通") ||
        text.contains("语音电话") ||
        text.contains("来自用户的语音电话")
    val hasOutputRule = text.contains("请只输出") &&
        text.contains("不要输出动作") &&
        text.contains("不要加标签")
    return hasVoiceCallContext && hasOutputRule
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val favoriteRepository: FavoriteRepository,
    private val memoryBankService: MemoryBankService,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val pluginToolProvider: PluginToolProvider,
    private val pluginLoader: PluginLoader,
    private val companionRuntime: CompanionRuntime,
) {
    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    private val _memoryReorganizationProgress = MutableStateFlow(MemoryReorganizationProgress())
    val memoryReorganizationProgress: StateFlow<MemoryReorganizationProgress> =
        _memoryReorganizationProgress.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        val chatError = ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        persistChatError(chatError)
        _errors.update {
            it + chatError
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                _isForeground.value = true
                appScope.launch {
                    runCatching {
                        val settings = settingsStore.settingsFlowRaw.first()
                        ProactiveMessageService.reconcileDurableCommitments(context, settings)
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to reconcile overdue companion commitments", error)
                    }
                }
            }
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init {
        // 添加生命周期观察者
        runOnMainThread {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        }
    }

    fun cleanup() = runCatching {
        runOnMainThread {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        }
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
    }

    // ---- Session 管理 ----

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = appScope,
                onIdle = { removeSession(it) }
            ).also {
                _sessionsVersion.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            _sessionsVersion.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = appScope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        val session = sessions[conversationId] ?: return MutableStateFlow(null)
        return session.processingStatus
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        getOrCreateSession(conversationId) // 确保 session 存在
        // 总是从数据库重新加载最新数据，确保能显示主动消息等新内容
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            val cleanedConversation = conversation.withoutVoiceCallInstructionLeaks()
            if (cleanedConversation !== conversation) {
                saveConversation(conversationId, cleanedConversation)
            } else {
                updateConversation(conversationId, cleanedConversation)
            }
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
                newConversation = true
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // ---- 发送消息 ----

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        val session = getOrCreateSession(conversationId)
        if (answer) {
            session.getJob()?.cancel()
        }

        val job = appScope.launch {
            try {
                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                // Keep timer reset in the same cancellable generation job as the message send.
                val currentAssistantId = currentConversation.assistantId
                val proactiveSetting = settings.getProactiveMessageSetting(currentAssistantId)
                if (proactiveSetting.enabled) {
                    me.rerere.rikkahub.data.service.ProactiveMessageService.scheduleNext(
                        context = context,
                        settings = settings,
                        minutesSinceLastChat = 0L,
                        assistantId = currentAssistantId,
                    )
                }
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // 触发 message_sent 事件钩子
                try {
                    val eventData = JsonObject(
                        mapOf(
                            "assistant_id" to JsonPrimitive(assistant.id.toString()),
                            "conversation_id" to JsonPrimitive(conversationId.toString()),
                            "message" to JsonPrimitive(processedContent.mapNotNull { part ->
                                if (part is UIMessagePart.Text) part.text else null
                            }.joinToString("\n")),
                            "role" to JsonPrimitive("user"),
                            "timestamp" to JsonPrimitive(System.currentTimeMillis())
                        )
                    )
                    pluginLoader.callEvent("message_sent", eventData)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to trigger message_sent event", e)
                }

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        if (answer) {
            session.setJob(job)
        }
    }

    fun requestReply(conversationId: Uuid) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val currentConversation = session.state.value
                if (currentConversation.currentMessages.lastOrNull()?.role != MessageRole.USER) {
                    return@launch
                }

                handleMessageComplete(conversationId)
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_generation))
            }
        }
        session.setJob(job)
    }

    // ---- 添加主动消息 ----

    suspend fun sendVoiceCallTurn(
        conversationId: Uuid,
        text: String,
        visibleUserText: String? = text,
    ): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        initializeConversation(conversationId)
        val beforeConversation = getConversationFlow(conversationId).value.withoutVoiceCallInstructionLeaks()
        saveConversation(conversationId, beforeConversation)

        val replyResult = runCatching {
            withTimeoutOrNull(120_000) {
                generateHiddenVoiceCallReply(
                    conversationId = conversationId,
                    conversation = beforeConversation,
                    text = trimmed,
                )
            }
        }.onFailure {
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
        }.getOrNull() ?: return null
        val replyMessage = replyResult.message
        val reply = sanitizeLuluVisibleExpression(
            replyMessage.toText()
                .ifBlank { replyMessage.extractTextToSpeechToolText() },
        ).trim()
            .takeIf { it.isNotBlank() && it != COMPANION_INCOMPLETE_REPLY_MARKER }
            ?: return null
        val visibleAssistantMessage = replyMessage.copy(
            parts = listOf(UIMessagePart.Text(reply)),
        )

        val cleanedConversation = appendVoiceCallVisibleTurn(
            conversation = beforeConversation,
            userText = visibleUserText,
            assistantText = reply,
            assistantMessage = visibleAssistantMessage,
        )
        saveConversation(conversationId, cleanedConversation)
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(cleanedConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val nowMillis = System.currentTimeMillis()
        val assistantKey = assistant.id.toString()
        val snapshotBeforeTurn = companionRuntime.snapshot(assistantKey)
        val unifiedState = buildCompanionStateFromTurn(
            previous = snapshotBeforeTurn.state,
            assistantText = reply,
            presence = listOf(replyMessage).companionModelPresence(),
            fallbackInnerThought = replyResult.turnPreparation.plan.innerThought
                ?: "电话回合已经结束，保留刚才的立场、承诺和未完成事项，等待下一句。",
            nowMillis = nowMillis,
        )
        val lastUserMessage = cleanedConversation.currentMessages
            .lastOrNull { it.role == MessageRole.USER }
        val scheduledPlans = buildCompanionTurnReminderPlans(
            plan = replyResult.turnPreparation.plan,
            userText = visibleUserText.orEmpty(),
            assistantText = reply,
            nowMillis = nowMillis,
        )
        val followUpDrafts = reconcileCompanionFollowUpDrafts(
            drafts = scheduledPlans.map { plan ->
                CompanionFollowUpDraft(
                    assistantId = assistantKey,
                    category = plan.kind.name.lowercase(Locale.ROOT),
                    reason = plan.toTargetedReason(),
                    sourceText = plan.userText,
                    dueAt = plan.triggerAtMillis,
                    sourceConversationId = conversationId.toString(),
                    sourceMessageId = lastUserMessage?.id?.toString(),
                    preferredToolNames = plan.preferredToolNames,
                    importance = if (
                        plan.kind == ProactiveReminderKind.SCHEDULE ||
                        plan.kind == ProactiveReminderKind.WAKE
                    ) 5 else 3,
                    actionType = if (
                        plan.kind == ProactiveReminderKind.SCHEDULE ||
                        plan.kind == ProactiveReminderKind.WAKE
                    ) CompanionActionType.REMINDER else CompanionActionType.CHECK_IN,
                )
            },
            snapshot = snapshotBeforeTurn,
            latestUserText = visibleUserText.orEmpty(),
        )
        val lifeEvents = replyResult.turnPreparation.toolExecutions.mapNotNull { execution ->
            buildToolLifeEvent(
                assistantId = assistantKey,
                execution = execution,
                source = CompanionLifeEventSource.TOOL,
                nowMillis = nowMillis,
            )
        }
        runCatching {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistantKey,
                    state = unifiedState,
                    lifeEvents = lifeEvents,
                    concernChanges = followUpDrafts.map { draft ->
                        CompanionConcernChange.Upsert(draft.toConcern(nowMillis))
                    },
                    acceptedCommitments = followUpDrafts.map { draft -> draft.toCommitment(nowMillis) },
                    continuity = CompanionContinuity(
                        conversationId = conversationId.toString(),
                        modality = CompanionInteractionModality.VOICE_CALL,
                        lastUserText = visibleUserText
                            ?.take(800)
                            ?: snapshotBeforeTurn.continuity.lastUserText,
                        lastAssistantText = reply.take(800),
                        updatedAt = nowMillis,
                    ),
                    nowMillis = nowMillis,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to persist companion voice turn", error)
        }
        scheduleProactiveReminderFromTurn(settings)
        if (!visibleUserText.isNullOrBlank()) {
            settings.findModelById(assistant.chatModelId ?: settings.chatModelId)?.let { model ->
                launchAffectiveMemoryExtraction(
                    conversationId = conversationId,
                    conversation = cleanedConversation,
                    assistant = assistant,
                    settings = settings,
                    model = model,
                )
            }
        }

        return reply
    }

    private suspend fun generateHiddenVoiceCallReply(
        conversationId: Uuid,
        conversation: Conversation,
        text: String,
    ): VoiceCallGenerationResult? {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(conversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return null
        val processedContent = preprocessUserInputParts(listOf(UIMessagePart.Text(text)), assistant)
        val hiddenMessages = conversation.currentMessages + UIMessage(
            role = MessageRole.USER,
            parts = processedContent,
        )
        val allTools = buildAvailableTools(settings, assistant, conversationId)
            .deduplicateByToolName()
            .selectRelevantToolsForPrompt(hiddenMessages)
        val latestUserText = hiddenMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        memoryBankService.syncCompanionPrivateImpression(
            companionRuntime = companionRuntime,
            assistantId = assistant.id.toString(),
        )
        val memoryContext = memoryBankService.buildRecallContext(
            assistantId = assistant.id.toString(),
            query = latestUserText,
            commitmentStatusesBySourceId = companionRuntime.snapshot(assistant.id.toString())
                .commitmentStatusesBySourceMessageId(),
        )
        val proactiveContext = collectProactiveToolContext(
            conversationId = conversationId,
            messages = hiddenMessages,
            tools = allTools,
            settings = settings,
            assistant = assistant,
            memoryContext = memoryContext,
        )
        val availableTools = allTools
            .selectCompanionToolsForGeneration(
                messages = hiddenMessages,
                preferredToolNames = proactiveContext.plan.toolRequests.map { it.toolName },
            )
            .withConciseToolDescriptions()
            .withHumanLikeToolPrompts()
        var generatedMessages = hiddenMessages

        generationHandler.generateText(
            settings = settings,
            model = model,
            processingStatus = MutableStateFlow<String?>(null),
            messages = hiddenMessages
                .withUserProfileContext(settings)
                .withMemoryRecallContext(memoryContext)
                .withProactiveToolInstruction(assistant, proactiveContext.promptContext),
            assistant = assistant,
            conversationSystemPrompt = conversation.customSystemPrompt,
            inputTransformers = buildList {
                addAll(companionInputTransformers)
                add(templateTransformer)
            },
            outputTransformers = companionOutputTransformers,
            tools = availableTools,
            pluginPromptInjections = pluginToolProvider.getPluginPromptInjections(),
            apiUsageSource = ApiUsageSource.PHONE,
            apiUsageTitle = "电话：${assistant.name.ifBlank { "当前角色" }}",
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> generatedMessages = chunk.messages
            }
        }

        val lastUserIndex = generatedMessages.indexOfLast { it.role == MessageRole.USER }
        val assistantReplies = generatedMessages
            .drop(lastUserIndex + 1)
            .filter { it.role == MessageRole.ASSISTANT }
        val combinedReply = assistantReplies
            .map { message ->
                message.toText().ifBlank { message.extractTextToSpeechToolText() }.trim()
            }
            .filter(String::isNotBlank)
            .joinToString("\n")
        val lastReply = assistantReplies.lastOrNull() ?: return null
        if (combinedReply.isBlank()) return null
        return VoiceCallGenerationResult(
            message = lastReply.copy(parts = listOf(UIMessagePart.Text(combinedReply))),
            turnPreparation = proactiveContext,
        )
    }

    private fun UIMessage.extractTextToSpeechToolText(): String =
        parts
            .filterIsInstance<UIMessagePart.Tool>()
            .lastOrNull { it.toolName == "text_to_speech" }
            ?.input
            ?.let { input ->
                runCatching {
                    JsonInstant.parseToJsonElement(input).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
            }
            .orEmpty()

    fun addProactiveMessage(conversationId: Uuid, aiMessage: UIMessage) {
        launchWithConversationReference(conversationId) {
            try {
                val session = getOrCreateSession(conversationId)
                // 优先从数据库读取完整对话，避免 session 被 idle 清除后用空对话覆盖数据库已有数据
                val currentConversation = conversationRepo.getConversationById(conversationId)
                    ?: session.state.value
                val updated = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + aiMessage.toMessageNode(),
                    updateAt = java.time.Instant.now()
                )
                updateConversation(conversationId, updated)
                saveConversation(conversationId, updated)
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(updated.assistantId)
                    ?: settings.getCurrentAssistant()
                val snapshot = companionRuntime.snapshot(assistant.id.toString())
                val nowMillis = System.currentTimeMillis()
                companionRuntime.applyTurn(
                    CompanionTurnMutation(
                        assistantId = assistant.id.toString(),
                        continuity = CompanionContinuity(
                            conversationId = conversationId.toString(),
                            modality = CompanionInteractionModality.PROACTIVE,
                            lastUserText = snapshot.continuity.lastUserText,
                            lastAssistantText = aiMessage.toText().take(800),
                            updatedAt = nowMillis,
                        ),
                        nowMillis = nowMillis,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "addProactiveMessage failed, conversationId=$conversationId", e)
            }
        }
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具问答和旧会话兼容状态 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value
                val newApprovalState = when {
                    answer != null -> ToolApprovalState.Answered(answer)
                    approved -> ToolApprovalState.Approved
                    else -> ToolApprovalState.Denied(reason)
                }

                // Update the tool interaction state
                val updatedNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { msg ->
                            msg.copy(
                                parts = msg.parts.map { part ->
                                    when {
                                        part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                            part.copy(approvalState = newApprovalState)
                                        }

                                        else -> part
                                    }
                                }
                            )
                        }
                    )
                }
                val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                saveConversation(conversationId, updatedConversation)

                // Check if there are still pending tools
                val hasPendingTools = updatedNodes.any { node ->
                    node.currentMessage.parts.any { part ->
                        part is UIMessagePart.Tool && part.isPending
                    }
                }

                // Only continue generation when all pending tools are handled
                if (!hasPendingTools) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }
        var turnPreparation = CompanionTurnPreparation()

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (settings.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value
            val latestUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }
            val latestUserText = latestUserMessage?.toText().orEmpty()
            val turnStartedAt = System.currentTimeMillis()
            resolveExistingWakeGoalsFromUserMessage(
                settings = settings,
                assistant = assistant,
                conversationId = conversationId,
                assistantId = assistant.id.toString(),
                userText = latestUserText,
                userMessageAt = latestUserMessage?.createdAt
                    ?.toInstant(TimeZone.currentSystemDefault())
                    ?.toEpochMilliseconds()
                    ?: turnStartedAt,
                nowMillis = turnStartedAt,
            )
            val detectedAlwaysOnAnchors = mergeAlwaysOnResponsibilityAnchors(
                detected = detectAlwaysOnAnchors(
                    assistantId = assistant.id.toString(),
                    userText = latestUserText,
                    sourceConversationId = conversationId.toString(),
                    sourceMessageId = latestUserMessage?.id?.toString(),
                    nowMillis = turnStartedAt,
                ),
                explicit = detectExplicitRecurringResponsibilityAnchors(
                    assistantId = assistant.id.toString(),
                    userText = latestUserText,
                    sourceConversationId = conversationId.toString(),
                    sourceMessageId = latestUserMessage?.id?.toString(),
                    nowMillis = turnStartedAt,
                ),
            )
            val cancelledAlwaysOnAnchorIds = (
                detectAlwaysOnAnchorCancellations(
                    assistantId = assistant.id.toString(),
                    userText = latestUserText,
                ) + detectExplicitRecurringResponsibilityCancellations(
                    assistantId = assistant.id.toString(),
                    userText = latestUserText,
                )
            ).distinct()
            if (detectedAlwaysOnAnchors.isNotEmpty() || cancelledAlwaysOnAnchorIds.isNotEmpty()) {
                companionRuntime.applyTurn(
                    CompanionTurnMutation(
                        assistantId = assistant.id.toString(),
                        alwaysOnAnchors = detectedAlwaysOnAnchors,
                        cancelAlwaysOnAnchorIds = cancelledAlwaysOnAnchorIds,
                        nowMillis = turnStartedAt,
                    ),
                )
                if (detectedAlwaysOnAnchors.any { anchor ->
                        anchor.kind == CompanionAlwaysOnAnchorKind.RESPONSIBILITY ||
                            anchor.kind == CompanionAlwaysOnAnchorKind.HEALTH
                    }) {
                    ProactiveMessageService.scheduleAlwaysOnAnchorReview(
                        context = context,
                        settings = settings,
                        assistantId = assistant.id,
                        nowMillis = turnStartedAt,
                    )
                }
            }
            val allTools = buildAvailableTools(settings, assistant, conversationId)
                .deduplicateByToolName()
                .selectRelevantToolsForPrompt(conversation.currentMessages)
            memoryBankService.syncCompanionPrivateImpression(
                companionRuntime = companionRuntime,
                assistantId = assistant.id.toString(),
                nowMillis = turnStartedAt,
            )
            val memoryContext = memoryBankService.buildRecallContext(
                assistantId = assistant.id.toString(),
                query = latestUserText,
                commitmentStatusesBySourceId = companionRuntime.snapshot(assistant.id.toString())
                    .commitmentStatusesBySourceMessageId(),
            )
            turnPreparation = collectProactiveToolContext(
                conversationId = conversationId,
                messages = conversation.currentMessages,
                tools = allTools,
                settings = settings,
                assistant = assistant,
                memoryContext = memoryContext,
            )
            val availableTools = allTools
                .selectCompanionToolsForGeneration(
                    messages = conversation.currentMessages,
                    preferredToolNames = turnPreparation.plan.toolRequests.map { it.toolName },
                )
                .withConciseToolDescriptions()
                .withHumanLikeToolPrompts()

            // start generating
            val session = getOrCreateSession(conversationId)
            generationHandler.generateText(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                }
                    .withUserProfileContext(settings)
                    .withMemoryRecallContext(memoryContext)
                    .withProactiveToolInstruction(assistant, turnPreparation.promptContext),
                assistant = assistant,
                conversationSystemPrompt = conversation.customSystemPrompt,
                inputTransformers = buildList {
                    addAll(companionInputTransformers)
                    add(templateTransformer)
                },
                outputTransformers = companionOutputTransformers,
                tools = availableTools,
                pluginPromptInjections = pluginToolProvider.getPluginPromptInjections(),
            ).onCompletion {
                // 取消 Live Update 通知
                cancelLiveUpdateNotification(conversationId)

                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                        node.copy(messages = node.messages.map { it.finishReasoning() })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId, senderName)
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

                        // 如果应用不在前台，发送 Live Update 通知
                        if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration && settings.displaySetting.enableLiveUpdateNotification) {
                            sendLiveUpdateNotification(conversationId, chunk.messages, senderName)
                        }
                    }
                }
            }
        }.onFailure {
            // 取消 Live Update 通知
            cancelLiveUpdateNotification(conversationId)

            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)
            val lastUserMessage = finalConversation.currentMessages.lastOrNull { it.role == MessageRole.USER }
            val lastUserText = lastUserMessage?.toText().orEmpty()
            val lastAssistantText = finalConversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.toText()
                .orEmpty()
            val nowMillis = System.currentTimeMillis()
            val scheduledPlans = buildCompanionTurnReminderPlans(
                plan = turnPreparation.plan,
                userText = lastUserText,
                assistantText = lastAssistantText,
                nowMillis = nowMillis,
            )
            val deterministicWakeExecution = scheduledPlans
                .firstOrNull { it.kind == ProactiveReminderKind.WAKE }
                ?.let { wakePlan ->
                    ensureWakeAlarmExecution(
                        settings = settings,
                        assistant = assistant,
                        conversationId = conversationId,
                        wakePlan = wakePlan,
                        existingExecutions = turnPreparation.toolExecutions +
                            finalConversation.currentMessages
                                .flatMap { it.getTools() }
                                .filter { it.isExecuted }
                                .map { tool ->
                                    CompanionToolExecution(
                                        toolCallId = tool.toolCallId,
                                        toolName = tool.toolName,
                                        inputJson = tool.input,
                                        outputText = tool.output
                                            .filterIsInstance<UIMessagePart.Text>()
                                            .joinToString("\n") { it.text },
                                    )
                                },
                    )
                }
            val scheduledToolDrafts = (buildScheduledToolFollowUpsFromTurn(
                finalConversation = finalConversation,
                assistantId = assistant.id.toString(),
                conversationId = conversationId.toString(),
                sourceMessageId = lastUserMessage?.id?.toString(),
                nowMillis = nowMillis,
            ) + (turnPreparation.toolExecutions + listOfNotNull(deterministicWakeExecution)).mapNotNull { execution ->
                buildScheduledToolFollowUp(
                    execution = execution,
                    assistantId = assistant.id.toString(),
                    conversationId = conversationId.toString(),
                    sourceMessageId = lastUserMessage?.id?.toString(),
                    nowMillis = nowMillis,
                )
            }).distinctBy { draft -> draft.reason to draft.dueAt }
            val snapshotBeforeTurn = companionRuntime.snapshot(assistant.id.toString())
            val unifiedState = buildCompanionStateFromTurn(
                previous = snapshotBeforeTurn.state,
                assistantText = lastAssistantText,
                presence = finalConversation.currentMessages.takeLast(8).companionModelPresence(),
                fallbackInnerThought = turnPreparation.plan.innerThought
                    ?: "我已经回应了这一轮对话，接下来先留意你会怎样继续。",
                nowMillis = nowMillis,
            )
            val hasWakePlan = scheduledPlans.any { it.kind == ProactiveReminderKind.WAKE }
            val effectiveScheduledToolDrafts = if (hasWakePlan) {
                scheduledToolDrafts.filterNot { draft -> draft.category == "schedule" || draft.category == "wake" }
            } else {
                scheduledToolDrafts
            }
            val effectiveScheduledPlans = if (effectiveScheduledToolDrafts.isEmpty()) {
                scheduledPlans
            } else {
                scheduledPlans.filterNot { it.kind == ProactiveReminderKind.SCHEDULE }
            }
            val wakeTargetAt = effectiveScheduledPlans
                .firstOrNull { it.kind == ProactiveReminderKind.WAKE }
                ?.triggerAtMillis
            val rawFollowUpDrafts = effectiveScheduledPlans.map { plan ->
                val isSleepSupervision = plan.kind == ProactiveReminderKind.SLEEP &&
                    wakeTargetAt != null && plan.triggerAtMillis < wakeTargetAt
                CompanionFollowUpDraft(
                    assistantId = assistant.id.toString(),
                    category = if (isSleepSupervision) {
                        "sleep_supervision"
                    } else {
                        plan.kind.name.lowercase(Locale.ROOT)
                    },
                    reason = plan.toTargetedReason(),
                    sourceText = plan.userText,
                    dueAt = plan.triggerAtMillis,
                    sourceConversationId = conversationId.toString(),
                    sourceMessageId = lastUserMessage?.id?.toString(),
                    preferredToolNames = plan.preferredToolNames,
                    importance = if (
                        plan.kind == ProactiveReminderKind.SCHEDULE || plan.kind == ProactiveReminderKind.WAKE
                    ) 5 else 3,
                    actionType = if (
                        plan.kind == ProactiveReminderKind.SCHEDULE || plan.kind == ProactiveReminderKind.WAKE
                    ) {
                        CompanionActionType.REMINDER
                    } else {
                        CompanionActionType.CHECK_IN
                    },
                    argumentsJson = when {
                        plan.kind == ProactiveReminderKind.WAKE ->
                            """{"wakeTargetAt":${plan.triggerAtMillis},"retryMinutes":5}"""
                        isSleepSupervision ->
                            """{"wakeTargetAt":$wakeTargetAt,"retryMinutes":15}"""
                        else -> "{}"
                    },
                )
            } + effectiveScheduledToolDrafts
            val followUpDrafts = reconcileCompanionFollowUpDrafts(
                drafts = rawFollowUpDrafts,
                snapshot = snapshotBeforeTurn,
                latestUserText = lastUserText,
            )
            val completedToolExecutions = (
                turnPreparation.toolExecutions +
                    listOfNotNull(deterministicWakeExecution) +
                    finalConversation.currentMessages
                        .flatMap { it.getTools() }
                        .filter { it.isExecuted }
                        .map { tool ->
                            CompanionToolExecution(
                                toolCallId = tool.toolCallId,
                                toolName = tool.toolName,
                                inputJson = tool.input,
                                outputText = tool.output
                                    .filterIsInstance<UIMessagePart.Text>()
                                    .joinToString("\n") { it.text },
                            )
                        }
                ).distinctBy { it.toolCallId }
            val lifeEvents = buildList {
                completedToolExecutions.mapNotNullTo(this) { execution ->
                    buildToolLifeEvent(
                        assistantId = assistant.id.toString(),
                        execution = execution,
                        source = CompanionLifeEventSource.TOOL,
                        nowMillis = nowMillis,
                    )
                }
            }
            runCatching {
                companionRuntime.applyTurn(
                    CompanionTurnMutation(
                        assistantId = assistant.id.toString(),
                        state = unifiedState,
                        lifeEvents = lifeEvents,
                        concernChanges = followUpDrafts.map { draft ->
                            CompanionConcernChange.Upsert(draft.toConcern(nowMillis))
                        },
                        acceptedCommitments = followUpDrafts.map { draft -> draft.toCommitment(nowMillis) },
                        continuity = CompanionContinuity(
                            conversationId = conversationId.toString(),
                            modality = CompanionInteractionModality.CHAT,
                            lastUserText = lastUserText.take(800),
                            lastAssistantText = lastAssistantText.take(800),
                            updatedAt = nowMillis,
                        ),
                        nowMillis = nowMillis,
                    ),
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to persist unified companion turn", error)
            }
            scheduleProactiveReminderFromTurn(
                settings = settings,
            )
            launchAffectiveMemoryExtraction(
                conversationId = conversationId,
                conversation = finalConversation,
                assistant = assistant,
                settings = settings,
                model = model,
            )

            // 触发 message_received 事件钩子
            try {
                val lastAssistantMessage = finalConversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                val eventData = JsonObject(
                    mapOf(
                        "assistant_id" to JsonPrimitive(assistant.id.toString()),
                        "conversation_id" to JsonPrimitive(conversationId.toString()),
                        "message" to JsonPrimitive(lastAssistantMessage?.toText() ?: ""),
                        "role" to JsonPrimitive("assistant"),
                        "timestamp" to JsonPrimitive(System.currentTimeMillis())
                    )
                )
                pluginLoader.callEvent("message_received", eventData)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to trigger message_received event", e)
            }

            launchWithConversationReference(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
        }
    }

    private fun scheduleProactiveReminderFromTurn(
        settings: Settings,
    ) {
        val nextCommitment = companionRuntime.nextCommitment() ?: run {
            ProactiveMessageService.clearTargetedQueue(context)
            return
        }
        val assistantId = runCatching { Uuid.parse(nextCommitment.assistantId) }.getOrNull() ?: return
        ProactiveMessageService.scheduleCommitment(
            context = context,
            setting = settings.getProactiveMessageSetting(assistantId),
            commitment = nextCommitment,
        )
    }

    private suspend fun resolveExistingWakeGoalsFromUserMessage(
        settings: Settings,
        assistant: Assistant,
        conversationId: Uuid,
        assistantId: String,
        userText: String,
        userMessageAt: Long,
        nowMillis: Long,
    ) {
        val normalizedText = userText.lowercase()
        var shouldDismissWakeAlarms = false
        companionRuntime.snapshot(assistantId).commitments
            .filter { commitment ->
                commitment.status in setOf(
                    CompanionCommitmentStatus.ACTIVE,
                    CompanionCommitmentStatus.DUE,
                    CompanionCommitmentStatus.EXECUTING,
                    CompanionCommitmentStatus.RETRY_SCHEDULED,
                ) && (commitment.isWakeGoal() || commitment.isSleepSupervisionGoal())
            }
            .forEach { commitment ->
                val userCancelled = WAKE_CANCELLATION_MARKERS.any { marker -> marker in normalizedText }
                val userConfirmedSleep = commitment.isSleepSupervisionGoal() &&
                    SLEEP_CONFIRMATION_MARKERS.any { marker -> marker in normalizedText }
                val userConfirmedAwake = commitment.isWakeGoal() &&
                    commitment.wakeTargetAtOrNull()?.let { target -> userMessageAt >= target } == true
                if (userCancelled || userConfirmedSleep || userConfirmedAwake) {
                    shouldDismissWakeAlarms = shouldDismissWakeAlarms || userCancelled || userConfirmedAwake
                    if (userConfirmedAwake && !userCancelled) {
                        companionRuntime.fulfillCommitmentFromEvidence(
                            assistantId = assistantId,
                            commitmentId = commitment.id,
                            summary = "User sent a message after the wake target and is confirmed awake",
                            completedAt = nowMillis,
                            outputReference = conversationId.toString(),
                        )
                    } else {
                        companionRuntime.cancelCommitment(
                            assistantId = assistantId,
                            commitmentId = commitment.id,
                            reason = if (userCancelled) {
                                "User cancelled the wake or sleep supervision goal"
                            } else {
                                "User confirmed they are going to sleep"
                            },
                            nowMillis = nowMillis,
                        )
                    }
                }
            }
        if (shouldDismissWakeAlarms) {
            dismissWakeAlarms(
                settings = settings,
                assistant = assistant,
                conversationId = conversationId,
            )
        }
    }

    private suspend fun dismissWakeAlarms(
        settings: Settings,
        assistant: Assistant,
        conversationId: Uuid,
    ) {
        val alarmTool = buildAvailableTools(settings, assistant, conversationId)
            .activeModelTools()
            .firstOrNull { it.name == "set_alarm" }
            ?: return
        val assistantName = assistant.name.ifBlank { "当前角色" }
        listOf(
            "${assistantName}叫你起床",
            "${assistantName}继续叫你起床",
        ).forEach { label ->
            runCatching {
                alarmTool.execute(
                    JsonObject(
                        mapOf(
                            "action" to JsonPrimitive("dismiss"),
                            "label" to JsonPrimitive(label),
                        )
                    )
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to dismiss wake alarm: $label", error)
            }
        }
    }

    private fun buildScheduledToolFollowUpsFromTurn(
        finalConversation: Conversation,
        assistantId: String,
        conversationId: String,
        sourceMessageId: String?,
        nowMillis: Long,
    ): List<CompanionFollowUpDraft> {
        val messages = finalConversation.currentMessages
        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex < 0) return emptyList()
        return messages
            .drop(lastUserIndex + 1)
            .flatMap { message -> message.getTools() }
            .asSequence()
            .filter { tool -> tool.isExecuted }
            .mapNotNull { tool ->
                val outputText = tool.output
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                    .trim()
                buildScheduledToolFollowUp(
                    execution = CompanionToolExecution(
                        toolCallId = tool.toolCallId,
                        toolName = tool.toolName,
                        inputJson = tool.input,
                        outputText = outputText,
                    ),
                    assistantId = assistantId,
                    conversationId = conversationId,
                    sourceMessageId = sourceMessageId,
                    nowMillis = nowMillis,
                )
            }
            .distinctBy { draft -> draft.reason to draft.dueAt }
            .toList()
    }

    private suspend fun ensureWakeAlarmExecution(
        settings: Settings,
        assistant: Assistant,
        conversationId: Uuid,
        wakePlan: ProactiveReminderPlan,
        existingExecutions: List<CompanionToolExecution>,
    ): CompanionToolExecution? {
        val target = Instant.ofEpochMilli(wakePlan.triggerAtMillis).atZone(ZoneId.systemDefault())
        if (existingExecutions.any { execution -> execution.isSuccessfulAlarmFor(target.hour, target.minute) }) {
            return null
        }
        val alarmTool = buildAvailableTools(settings, assistant, conversationId)
            .activeModelTools()
            .firstOrNull { it.name == "set_alarm" }
            ?: return null
        val input = JsonObject(
            mapOf(
                "hour" to JsonPrimitive(target.hour),
                "minute" to JsonPrimitive(target.minute),
                "label" to JsonPrimitive("${assistant.name.ifBlank { "当前角色" }}叫你起床"),
            )
        )
        val output = runCatching { alarmTool.execute(input) }
            .getOrElse { error ->
                listOf(UIMessagePart.Text("""{"success":false,"error":${JsonPrimitive(error.message ?: "Alarm failed")}}"""))
            }
        return CompanionToolExecution(
            toolCallId = "wake:${Uuid.random()}",
            toolName = "set_alarm",
            inputJson = input.toString(),
            outputText = output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text },
        )
    }

    private fun CompanionToolExecution.isSuccessfulAlarmFor(hour: Int, minute: Int): Boolean {
        if (toolName != "set_alarm" || !outputText.contains("\"success\":true")) return false
        val input = runCatching { JsonInstant.parseToJsonElement(inputJson).jsonObject }.getOrNull()
            ?: return false
        return input["hour"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() == hour &&
            input["minute"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() == minute
    }

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

    fun retryHistoricalMemoryExtraction(
        assistantId: String? = null,
        mode: MemoryReorganizationMode = MemoryReorganizationMode.RECENT_BATCH,
    ): Job = appScope.launch {
        if (_memoryReorganizationProgress.value.running) return@launch
        val initialProgress = MemoryReorganizationProgress(
            running = true,
            mode = mode,
            assistantId = assistantId,
            message = "正在读取聊天记录…",
        )
        _memoryReorganizationProgress.value = initialProgress
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val targets = settings.assistants
                .filter { assistant -> assistantId == null || assistant.id.toString() == assistantId }
                .flatMap { assistant ->
                    val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                    if (model == null) {
                        emptyList()
                    } else {
                        val conversations = conversationRepo.getConversationsOfAssistant(assistant.id)
                            .first()
                            .mapNotNull { summary -> conversationRepo.getConversationById(summary.id) }
                            .let { items ->
                                if (mode == MemoryReorganizationMode.RECENT_BATCH) {
                                    items.sortedByDescending { it.createAt }
                                } else {
                                    items.sortedBy { it.createAt }
                                }
                            }
                        conversations.map { conversation -> Triple(assistant, model, conversation) }
                    }
                }
            _memoryReorganizationProgress.value = initialProgress.copy(
                totalConversations = targets.size,
                message = if (targets.isEmpty()) "没有找到这个角色的聊天记录" else "正在检查可整理批次…",
            )

            if (mode == MemoryReorganizationMode.FULL_REBUILD) {
                targets.forEach { (assistant, _, conversation) ->
                    memoryBankService.resetExtractionCheckpoint(
                        assistantId = assistant.id.toString(),
                        conversationId = conversation.id.toString(),
                    )
                }
            }

            val direction = if (mode == MemoryReorganizationMode.RECENT_BATCH) {
                MemoryExtractionDirection.RECENT_FIRST
            } else {
                MemoryExtractionDirection.OLDEST_FIRST
            }
            val maximumTotalBatches = if (mode == MemoryReorganizationMode.FULL_REBUILD) {
                Int.MAX_VALUE
            } else {
                1
            }
            var completedBatchCount = 0
            var failedBatchCount = 0
            var repairedTemporalRows = 0

            targetLoop@ for ((targetIndex, target) in targets.withIndex()) {
                val (assistant, model, conversation) = target
                val sourceTimesByNodeId = conversation.messageNodes.toMemoryExtractionTurns()
                    .associate { turn -> turn.nodeId to turn.createdAtMillis }
                    .filterValues { it > 0L }
                repairedTemporalRows += memoryBankService.repairTemporalMetadata(
                    assistantId = assistant.id.toString(),
                    conversationId = conversation.id.toString(),
                    sourceTimesByNodeId = sourceTimesByNodeId,
                )
                repairedTemporalRows += companionRuntime.repairRelationshipEventTimes(
                    assistantId = assistant.id.toString(),
                    sourceTimesByNodeId = sourceTimesByNodeId,
                )

                var completedConversationBatches = 0
                while (
                    completedConversationBatches < MAX_HISTORICAL_MEMORY_BATCHES_PER_CONVERSATION &&
                    completedBatchCount < maximumTotalBatches
                ) {
                    val includeSavedMemories = mode != MemoryReorganizationMode.FULL_REBUILD
                    val processedBefore = memoryBankService.getProcessedSourceNodeIds(
                        assistantId = assistant.id.toString(),
                        conversationId = conversation.id.toString(),
                        includeSavedMemories = includeSavedMemories,
                    )
                    val plan = buildAffectiveMemoryExtractionPlan(
                        messageNodes = conversation.messageNodes,
                        processedSourceNodeIds = processedBefore,
                        extractionInterval = assistant.memoryExtractionInterval,
                        direction = direction,
                    ) ?: break
                    _memoryReorganizationProgress.value = MemoryReorganizationProgress(
                        running = true,
                        mode = mode,
                        assistantId = assistant.id.toString(),
                        totalConversations = targets.size,
                        currentConversation = targetIndex + 1,
                        completedBatches = completedBatchCount,
                        failedBatches = failedBatchCount,
                        currentBatchStartAt = plan.turns.firstOrNull()?.createdAtMillis,
                        currentBatchEndAt = plan.turns.lastOrNull()?.createdAtMillis,
                        repairedTemporalRows = repairedTemporalRows,
                        message = "正在整理第 ${completedBatchCount + 1} 批（${plan.turns.size} 条）",
                    )
                    launchAffectiveMemoryExtraction(
                        conversationId = conversation.id,
                        conversation = conversation,
                        assistant = assistant,
                        settings = settings,
                        model = model,
                        direction = direction,
                        includeSavedMemorySources = includeSavedMemories,
                    ).join()
                    val processedAfter = memoryBankService.getProcessedSourceNodeIds(
                        assistantId = assistant.id.toString(),
                        conversationId = conversation.id.toString(),
                        includeSavedMemories = includeSavedMemories,
                    )
                    val batchNodeIds = plan.turns.map { it.nodeId }
                    if (!processedAfter.containsAll(batchNodeIds)) {
                        failedBatchCount += 1
                        Log.w(
                            TAG,
                            "Historical memory extraction paused for conversation=${conversation.id}; batch remains retryable",
                        )
                        break
                    }
                    completedConversationBatches += 1
                    completedBatchCount += 1
                }
                if (completedBatchCount >= maximumTotalBatches) break@targetLoop
            }

            val finalMessage = when {
                targets.isEmpty() -> "没有找到这个角色的聊天记录"
                failedBatchCount > 0 -> "本批整理失败，进度未跳过，下次可继续重试"
                completedBatchCount == 0 && repairedTemporalRows > 0 -> "没有新的完整批次，已修复 $repairedTemporalRows 条旧时间"
                completedBatchCount == 0 -> "暂无可整理的完整批次；最新 10 条仍保留在当前上下文"
                mode == MemoryReorganizationMode.FULL_REBUILD -> "完整重建完成：$completedBatchCount 批，修复 $repairedTemporalRows 条时间"
                else -> "已整理 $completedBatchCount 批；下一批会从未处理位置继续"
            }
            _memoryReorganizationProgress.value = _memoryReorganizationProgress.value.copy(
                running = false,
                completedBatches = completedBatchCount,
                failedBatches = failedBatchCount,
                repairedTemporalRows = repairedTemporalRows,
                message = finalMessage,
            )
            Logging.log(TAG, "Historical memory extraction completed $completedBatchCount batches")
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Historical memory extraction failed", error)
            _memoryReorganizationProgress.value = _memoryReorganizationProgress.value.copy(
                running = false,
                failedBatches = _memoryReorganizationProgress.value.failedBatches + 1,
                message = error.message?.let { "整理失败：$it" } ?: "整理失败，进度未跳过",
            )
        }
    }

    private fun launchAffectiveMemoryExtraction(
        conversationId: Uuid,
        conversation: Conversation,
        assistant: Assistant,
        settings: Settings,
        model: Model,
        direction: MemoryExtractionDirection = MemoryExtractionDirection.OLDEST_FIRST,
        includeSavedMemorySources: Boolean = true,
    ): Job = appScope.launch {
            runCatching {
                val processedSourceNodeIds = memoryBankService.getProcessedSourceNodeIds(
                    assistantId = assistant.id.toString(),
                    conversationId = conversationId.toString(),
                    includeSavedMemories = includeSavedMemorySources,
                )
                val plan = buildAffectiveMemoryExtractionPlan(
                    messageNodes = conversation.messageNodes,
                    processedSourceNodeIds = processedSourceNodeIds,
                    extractionInterval = assistant.memoryExtractionInterval,
                    direction = direction,
                ) ?: return@runCatching

                val extractionModel = settings.memoryEmbeddingConfig.extractionModelId
                    ?.let { settings.findModelById(it) }
                    ?.takeIf { it.type == ModelType.CHAT }
                    ?: model
                val deterministicCandidates = buildDeterministicMemoryCandidates(plan.turns)
                val extractionProvider = extractionModel.findProvider(settings.providers)
                if (extractionProvider == null) {
                    Log.w(TAG, "Memory extraction skipped: no provider for model=${extractionModel.id}")
                    return@runCatching
                }
                val modelExtraction = runCatching {
                    val providerImpl = providerManager.getProviderByType(extractionProvider)
                    val prompt = AffectiveMemoryExtractor.buildExtractionPrompt(
                        turns = plan.turns,
                        assistantName = assistant.name,
                        assistantPersona = assistant.toLuluPlannerPersona(
                            messages = conversation.currentMessages,
                            settings = settings,
                        ),
                        stateHistory = companionRuntime.snapshot(assistant.id.toString()).stateHistory,
                        responsibilityContext = buildCompanionResponsibilityContext(
                            companionRuntime.snapshot(assistant.id.toString()).alwaysOnAnchors,
                            System.currentTimeMillis(),
                        ),
                    )
                    val chunk = providerImpl.generateText(
                        providerSetting = extractionProvider,
                        messages = listOf(UIMessage.user(prompt)),
                        params = TextGenerationParams(
                            model = extractionModel,
                            temperature = 0.2f,
                            topP = 0.8f,
                            maxTokens = 1200,
                            reasoningLevel = ReasoningLevel.OFF,
                            customHeaders = buildList {
                                addAll(assistant.customHeaders)
                                addAll(extractionModel.customHeaders)
                            },
                            customBody = buildList {
                                addAll(assistant.customBodies)
                                addAll(extractionModel.customBodies)
                            },
                        ),
                    )
                    val rawText = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
                    AffectiveMemoryExtractor.parseExtractionResult(rawText).memories
                }
                val modelExtractionSucceeded = modelExtraction.isSuccess
                val modelCandidates = modelExtraction.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Model memory extraction failed; source messages will be retried", error)
                    emptyList()
                }
                val occurrenceByNodeId = plan.turns.associate { it.nodeId to it.createdAtMillis }
                val fallbackEvidenceNodeIds = plan.turns
                    .asReversed()
                    .filter { it.role.equals("user", ignoreCase = true) }
                    .take(2)
                    .map { it.nodeId }
                    .reversed()
                val candidates = (modelCandidates + deterministicCandidates)
                    .map { it.normalized() }
                    .map { candidate ->
                        val sourceNodeIds = candidate.sourceMessageNodeIds
                            .ifEmpty { fallbackEvidenceNodeIds }
                        val evidenceNodeIds = candidate.evidenceMessageNodeIds
                            .ifEmpty { sourceNodeIds }
                        val sourceMessageAt = (sourceNodeIds + evidenceNodeIds)
                            .mapNotNull(occurrenceByNodeId::get)
                            .filter { it > 0L }
                            .maxOrNull()
                        val modelOccurrence = candidate.occurredAtMillis
                            ?.takeIf { occurredAt ->
                                occurredAt > 0L && (
                                    sourceMessageAt == null ||
                                        occurredAt <= sourceMessageAt + MEMORY_OCCURRENCE_FUTURE_TOLERANCE_MS
                                    )
                            }
                        candidate.copy(
                            userSignal = candidate.userSignal ?: candidate.content,
                            sourceMessageNodeIds = sourceNodeIds,
                            evidenceMessageNodeIds = evidenceNodeIds,
                            sourceMessageAtMillis = sourceMessageAt,
                            occurredAtMillis = modelOccurrence ?: sourceMessageAt,
                        )
                    }
                    .filter { it.isDurableMemoryCandidate() }
                    .distinctBy { it.content.normalizedMemoryIdentity() }
                    .take(8)
                if (candidates.isEmpty()) {
                    // An explicitly empty result means the model found nothing durable. A
                    // non-empty result rejected by validation is a compatibility problem and
                    // must stay retryable instead of permanently skipping this conversation.
                    val safelyProcessedEmptyResult = modelExtractionSucceeded && modelCandidates.isEmpty()
                    if (safelyProcessedEmptyResult) {
                        memoryBankService.markExtractionProcessed(
                            assistantId = assistant.id.toString(),
                            conversationId = conversationId.toString(),
                            sourceNodeIds = plan.turns.map { it.nodeId },
                        )
                    }
                    Logging.log(
                        TAG,
                        "Memory extraction found no durable memories for conversation=$conversationId reason=${plan.reason} success=$modelExtractionSucceeded checkpointed=$safelyProcessedEmptyResult",
                    )
                    return@runCatching
                }

                val savedMemories = memoryBankService.saveExtractedMemories(
                    candidates = candidates,
                    assistantId = assistant.id.toString(),
                    conversationId = conversationId.toString(),
                    createdAt = System.currentTimeMillis(),
                )
                val correctedMemoryCount = memoryBankService.reconcileConversationalCorrections(
                    assistantId = assistant.id.toString(),
                    corrections = savedMemories,
                )
                val relationshipEvents = buildRelationshipEventsFromMemoryCandidates(
                    candidates = candidates,
                    assistantId = assistant.id.toString(),
                    conversationId = conversationId.toString(),
                    createdAt = System.currentTimeMillis(),
                )
                val privateImpression = if (correctedMemoryCount > 0) {
                    memoryBankService.rebuildStoredPrivateImpression(
                        assistantId = assistant.id.toString(),
                        nowMillis = System.currentTimeMillis(),
                    )
                } else {
                    buildCompanionPrivateImpression(
                        previous = companionRuntime.snapshot(assistant.id.toString()).privateImpression,
                        candidates = candidates,
                        nowMillis = System.currentTimeMillis(),
                    )
                }
                if (relationshipEvents.isNotEmpty() || privateImpression.updatedAt > 0L) {
                    companionRuntime.applyTurn(
                        CompanionTurnMutation(
                            assistantId = assistant.id.toString(),
                            privateImpression = privateImpression,
                            relationshipEvents = relationshipEvents,
                            nowMillis = System.currentTimeMillis(),
                        )
                    )
                }
                if (modelExtractionSucceeded) {
                    memoryBankService.markExtractionProcessed(
                        assistantId = assistant.id.toString(),
                        conversationId = conversationId.toString(),
                        sourceNodeIds = plan.turns.map { it.nodeId },
                    )
                }
                runCatching {
                    memoryBankService.processPendingVectors()
                    memoryBankService.runAutoMaintenanceIfDue()
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Memory maintenance failed after extraction for conversation=$conversationId", error)
                }
                Logging.log(
                    TAG,
                    "Saved ${savedMemories.size}/${candidates.size} affective memories for conversation=$conversationId reason=${plan.reason}",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Affective memory extraction failed for conversation=$conversationId", error)
            }
        }


    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool interactions.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            ),
            approvalState = ToolApprovalState.Denied("Generation cancelled by user")
        )
    }

    private fun buildAvailableTools(
        settings: Settings,
        assistant: Assistant,
        conversationId: Uuid? = null,
    ): List<Tool> = buildList {
        add(createTodayStudyPlanTool(assistant.id.toString(), assistant.name))
        add(createCompanionGameTool(assistant.id.toString()))
        conversationId?.let { add(createFavoriteCurrentUserMessageTool(it)) }
        if (settings.enableWebSearch) {
            addAll(createSearchTools(settings))
        }
        addAll(localTools.getTools(assistant.localTools, assistant.id.toString()))

        val systemToolsOptions = settings.systemToolsSetting.getEnabledOptions() + SystemToolOption.Battery
        if (systemToolsOptions.isNotEmpty()) {
            val systemTools = SystemTools(context, settings)
            addAll(systemTools.getTools(systemToolsOptions))
        }

        if (assistant.enabledSkills.isNotEmpty()) {
            addAll(
                createSkillTools(
                    enabledSkills = assistant.enabledSkills,
                    allSkills = skillManager.listSkills(),
                    skillManager = skillManager,
                )
            )
        }

        mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
            if (assistant.mcpServers.contains(serverId)) {
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
        }

        addAll(pluginToolProvider.getTools())
    }

    private fun createFavoriteCurrentUserMessageTool(conversationId: Uuid): Tool = Tool(
        name = "favorite_user_message",
        description = "Favorite the current user's message only when the character genuinely wants to keep it as something precious or personally meaningful.",
        execute = {
            val conversation = conversationRepo.getConversationById(conversationId)
                ?: error("Conversation not found")
            val node = conversation.messageNodes.lastOrNull { it.currentMessage.role == MessageRole.USER }
                ?: error("No user message to favorite")
            favoriteRepository.addNodeFavorite(
                NodeFavoriteTarget(
                    conversationId = conversation.id,
                    conversationTitle = conversation.title,
                    nodeId = node.id,
                    node = node,
                )
            )
            listOf(UIMessagePart.Text("""{"success":true,"message":"Current user message favorited"}"""))
        },
    )

    private suspend fun collectProactiveToolContext(
        conversationId: Uuid,
        messages: List<UIMessage>,
        tools: List<Tool>,
        settings: Settings,
        assistant: Assistant,
        memoryContext: String,
    ): CompanionTurnPreparation {
        val latestUserText = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        if (latestUserText.isBlank()) return CompanionTurnPreparation()
        val nowMillis = System.currentTimeMillis()
        val previousInteractionAt = messages
            .dropLast(1)
            .lastOrNull { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            ?.createdAt
            ?.toInstant(TimeZone.currentSystemDefault())
            ?.toEpochMilliseconds()
        val passiveFacts = collectCompanionPassivePerceptionFacts(
            tools = tools,
            observedAt = nowMillis,
        )
        val perceptionInput = CompanionPerceptionInput(
                assistantId = assistant.id.toString(),
                assistantName = assistant.name,
                persona = assistant.toLuluPlannerPersona(
                    messages = messages,
                    settings = settings,
                ),
                conversationId = conversationId.toString(),
                recentTurns = messages.takeLast(12).map { message ->
                    CompanionConversationTurn(
                        role = message.role.toCompanionTurnRole(),
                        content = message.toText(),
                        createdAt = message.createdAt
                            .toInstant(TimeZone.currentSystemDefault())
                            .toEpochMilliseconds(),
                        sourceId = message.id.toString(),
                    )
                },
                contextFacts = listOfNotNull(
                    previousInteractionAt?.let { previousAt ->
                        CompanionContextFact(
                            key = "minutes_since_previous_interaction",
                            value = ((nowMillis - previousAt) / 60_000L).coerceAtLeast(0L).toString(),
                            observedAt = nowMillis,
                        )
                    },
                ) + passiveFacts,
                availableToolNames = tools.activeModelTools().map { it.name }.toSet(),
                memoryContext = memoryContext,
                nowMillis = nowMillis,
        )
        var companionPerception = companionRuntime.perception(perceptionInput)
        val roleName = assistant.name.ifBlank { "当前角色" }
        val planResult = buildChatTurnPlan(
            settings = settings,
            assistant = assistant,
            perception = companionPerception,
        )
        val plan = planResult.plan
        val responsibilityUpserts = plan.responsibilityAnchorUpserts.mapNotNull { draft ->
            draft.toAlwaysOnAnchorOrNull(
                assistantId = assistant.id.toString(),
                sourceConversationId = conversationId.toString(),
                sourceMessageId = messages.lastOrNull { it.role == MessageRole.USER }?.id?.toString(),
                nowMillis = nowMillis,
            )
        }
        if (responsibilityUpserts.isNotEmpty() || plan.cancelResponsibilityAnchorIds.isNotEmpty()) {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistant.id.toString(),
                    alwaysOnAnchors = responsibilityUpserts,
                    cancelAlwaysOnAnchorIds = plan.cancelResponsibilityAnchorIds,
                    nowMillis = nowMillis,
                ),
            )
            companionPerception = companionRuntime.perception(perceptionInput)
            if (responsibilityUpserts.isNotEmpty()) {
                ProactiveMessageService.scheduleAlwaysOnAnchorReview(
                    context = context,
                    settings = settings,
                    assistantId = assistant.id,
                    nowMillis = nowMillis,
                )
            }
        }
        val unifiedContext = companionPerception.toPromptContext()
        val toolExecutions = executeCompanionPlannedTools(
            plan = plan,
            tools = tools,
        )
        val directFactAnswerGuard = buildDirectFactAnswerGuard(
            userText = latestUserText,
            passiveFacts = passiveFacts,
            toolExecutions = toolExecutions,
        )
        val promptContext = buildString {
            appendLine("<companion_private_context>")
            appendLine(unifiedContext)
            appendLine()
            if (toolExecutions.isNotEmpty()) {
                appendLine("本轮回复前已经完成的角色行动：")
                toolExecutions.forEach { execution ->
                    appendLine("- ${execution.toolName}: ${execution.outputText.take(800)}")
                }
                appendLine("成功完成的动作不要重复调用；把结果作为刚刚亲自完成或感知到的事实。")
                appendLine()
            }
            plan.innerThought?.takeIf { it.isNotBlank() }?.let { thought ->
                appendLine("本轮${roleName}没说出口的心里话：$thought")
                appendLine("这只是后台第一人称心声，不要把它当成工具结果，也不要原样复述。")
                appendLine()
            }
            plan.expressionGuidance?.takeIf { it.isNotBlank() }?.let { guidance ->
                appendLine("本轮${roleName}自己的表达打算：$guidance")
                appendLine("这只是后台表达方向，不要把它原样说给用户。")
                appendLine()
            }
            if (plan.expressionAffordances.isNotEmpty()) {
                appendLine("本轮可用表达池：${plan.expressionAffordances.joinToString(", ") { it.name }}")
                appendLine("表达池只是表达层 affordance，不决定是否行动，也不要逐字复述这些标签。")
                appendLine()
            }
            directFactAnswerGuard.takeIf(String::isNotBlank)?.let { guard ->
                appendLine(guard)
                appendLine()
            }
            append("</companion_private_context>")
        }
        return CompanionTurnPreparation(
            promptContext = promptContext.trim(),
            plan = plan,
            toolExecutions = toolExecutions,
        )
    }

    private suspend fun executeCompanionPlannedTools(
        plan: CompanionChatTurnPlan,
        tools: List<Tool>,
    ): List<CompanionToolExecution> = buildList {
        plan.toolRequests
            .filter { request -> request.autoExecutable && request.toolName != "ask_user" }
            .forEach { request ->
            val tool = tools.activeModelTools().firstOrNull { it.name == request.toolName }
                ?: return@forEach
            val output = runCatching {
                val args = JsonInstant.parseToJsonElement(request.argumentsJson.ifBlank { "{}" })
                tool.execute(args)
            }.getOrElse { error ->
                listOf(UIMessagePart.Text("""{"success":false,"error":${JsonPrimitive(error.message ?: "Tool execution failed")}}"""))
            }
            add(
                CompanionToolExecution(
                    toolCallId = "planner:${Uuid.random()}",
                    toolName = request.toolName,
                    inputJson = request.argumentsJson,
                    outputText = output
                        .filterIsInstance<UIMessagePart.Text>()
                        .joinToString("\n") { it.text }
                        .trim(),
                )
            )
        }
    }

    private fun MessageRole.toCompanionTurnRole(): CompanionTurnRole = when (this) {
        MessageRole.USER -> CompanionTurnRole.USER
        MessageRole.ASSISTANT -> CompanionTurnRole.ASSISTANT
        MessageRole.SYSTEM -> CompanionTurnRole.SYSTEM
        MessageRole.TOOL -> CompanionTurnRole.TOOL
    }

    private suspend fun buildChatTurnPlan(
        settings: Settings,
        assistant: Assistant,
        perception: CompanionPerceptionPacket,
    ): ChatTurnPlanResult {
        val input = CompanionChatTurnPlanInput(perception = perception)
        val plannerModelId = settings.luluIntentModelId ?: assistant.chatModelId ?: settings.chatModelId
        val modelPlan = plannerModelId
            ?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }
            ?.let { model ->
                runCatching {
                    CompanionChatTurnModelPlanner.planChatTurnOrNull(
                        input = input,
                        settings = settings,
                        model = model,
                        providerManager = providerManager,
                    )
                }.getOrNull()
            }
        val requiredFactChecks = ProactiveToolPlanner.requiredFactChecks(
            userText = perception.recentTurns.lastOrNull { it.role == CompanionTurnRole.USER }?.content.orEmpty(),
            availableToolNames = perception.availableToolNames,
        )
        val basePlan = modelPlan ?: CompanionChatTurnPlan()
        return ChatTurnPlanResult(
            plan = basePlan.copy(
                toolRequests = (requiredFactChecks + basePlan.toolRequests)
                    .distinctBy { it.toolName }
                    .take(5),
            ),
        )
    }

    private fun Assistant.toLuluPlannerPersona(
        messages: List<UIMessage> = emptyList(),
        settings: Settings? = null,
    ): String = buildString {
        appendLine("角色名：${name.ifBlank { "当前角色" }}")
        if (systemPrompt.isNotBlank()) {
            appendLine("系统人设：")
            appendLine(systemPrompt)
        }
        if (appearancePrompt.isNotBlank()) {
            appendLine("外貌设定：")
            appendLine(appearancePrompt)
        }
        if (messageTemplate.isNotBlank() && messageTemplate != "{{ message }}") {
            appendLine("语言/消息模板：")
            appendLine(messageTemplate)
        }
        settings?.let { currentSettings ->
            buildPromptInjectionPlannerContext(
                messages = messages,
                assistant = this@toLuluPlannerPersona,
                modeInjections = currentSettings.modeInjections,
                lorebooks = currentSettings.lorebooks,
            ).takeIf(String::isNotBlank)?.let { injectionContext ->
                appendLine("模式与世界书：")
                appendLine(injectionContext)
            }
        }
    }.trim()

    private data class ChatTurnPlanResult(
        val plan: CompanionChatTurnPlan,
    )

    private data class CompanionTurnPreparation(
        val promptContext: String = "",
        val plan: CompanionChatTurnPlan = CompanionChatTurnPlan(),
        val toolExecutions: List<CompanionToolExecution> = emptyList(),
    )

    private data class VoiceCallGenerationResult(
        val message: UIMessage,
        val turnPreparation: CompanionTurnPreparation,
    )

    private fun List<UIMessage>.withProactiveToolInstruction(assistant: Assistant, proactiveContext: String): List<UIMessage> {
        val instruction = buildString {
            appendLine("你正在扮演${assistant.name.ifBlank { "当前角色" }}，不是客服、旁白或提示词分析器。")
            appendLine("最高优先级是用户为这个角色设置的核心人设、关系类型、世界观、语言风格与明确边界；任何运行时情绪、关系数值、陪伴目标或表达建议都只能在人设内部调节，绝不能把任意角色改写成默认温柔、亲密、恋爱或顺从型陪伴者。")
            appendLine("先以该角色自己的理解方式回应用户最新一句话，不要跳去讲后台资料。")
            appendLine("连续感来自事实与立场的延续：相关时自然承接上一场景、共同经历、称呼习惯、未完成事项和已经作出的承诺；不要为了证明记得而罗列记忆，也不要在用户已纠正后坚持旧记录。")
            appendLine("称呼、停顿、情绪强度、幽默与冲突方式都必须来自具体人设和当前关系证据，不预设昵称、亲密程度或固定语气。长短跟随情境，不要每轮套同一模板，也不要客服式总结后连续追问。")
            appendLine("<companion_runtime> 与 <companion_private_context> 中的内容都是没有说出口的私密感知。只能内化后表达，绝不能复述标签、字段、规则、XML、用户资料或‘本轮可用表达池’。")
            appendLine("<companion_runtime> 中的 perception_facts 是程序已自动提供的当前感知；自然使用，不提工具或数据采集。")
            appendLine("当前可见工具都属于会产生查询、写入或设备动作的主动能力，只在角色形成明确意图时使用。")
            appendLine("today_study_plan 管理本 App 的考研计划；calendar_tool 只处理手机系统日历。")
            appendLine("需要稍后主动回来时自然说清时间和目的；最终只输出角色真正会说出口的话。")
            if (proactiveContext.isNotBlank()) {
                appendLine()
                append(proactiveContext)
            }
        }.trim()
        return listOf(UIMessage.system(instruction)) + this
    }

    private fun List<UIMessage>.withMemoryRecallContext(memoryContext: String): List<UIMessage> {
        if (memoryContext.isBlank()) return this
        return listOf(UIMessage.system(memoryContext)) + this
    }

    private fun List<UIMessage>.withUserProfileContext(settings: Settings): List<UIMessage> {
        val context = settings.buildUserProfileContext()
        if (context.isBlank()) return this
        return listOf(UIMessage.system(context)) + this
    }

    private fun Settings.buildUserProfileContext(): String {
        val display = displaySetting
        val nickname = display.userNickname.trim()
        val profile = display.userProfile.trim()
        val appearance = display.userAppearancePrompt.trim()
        if (nickname.isBlank() && profile.isBlank() && appearance.isBlank()) return ""
        return buildString {
            appendLine("<private_user_profile>")
            appendLine("以下资料只作为事实约束，用于理解用户并保持互动一致；绝不逐字复述，也不要告诉用户你在读取资料。它们不能改变角色与用户的关系类型或人设语气。")
            if (nickname.isNotBlank()) appendLine("昵称：${nickname.take(80)}")
            if (profile.isNotBlank()) appendLine("个人资料：${profile.take(600)}")
            if (appearance.isNotBlank()) appendLine("我的外貌：${appearance.take(600)}")
            appendLine("聊天、称呼、关系感、身体/性别/外貌描写、以及涉及用户出现在画面里的内容，都要优先遵守这些资料。")
            append("</private_user_profile>")
        }.trim()
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        if (!shouldGenerateTitle(conversation.title, force)) return

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.titleModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText() })
                    ),
                ),
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckTitleModelSettings,
            )
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            val suggestions =
                result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessions[conversationId]?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 10
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText() }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                ),
            )

            return result.choices[0].message?.toText()?.trim()
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // ---- 通知 ----

    private fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String) {
        // 先取消 Live Update 通知
        cancelLiveUpdateNotification(conversationId)

        val conversation = getConversationFlow(conversationId).value
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1
        ) {
            title = senderName
            content = conversation.currentMessages.lastOrNull()?.toText()?.take(50)?.trim() ?: ""
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid): Int {
        return conversationId.hashCode() + 10000
    }

    private fun sendLiveUpdateNotification(
        conversationId: Uuid,
        messages: List<UIMessage>,
        senderName: String
    ) {
        val lastMessage = messages.lastOrNull() ?: return
        val parts = lastMessage.parts

        // 确定当前状态
        val (chipText, statusText, contentText) = determineNotificationContent(parts)

        context.sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = getLiveUpdateNotificationId(conversationId)
        ) {
            title = senderName
            content = contentText
            subText = statusText
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            useBigTextStyle = true
            contentIntent = getPendingIntent(context, conversationId)
            requestPromotedOngoing = true
            shortCriticalText = chipText
        }
    }

    private fun determineNotificationContent(parts: List<UIMessagePart>): Triple<String, String, String> {
        // 检查最近的 part 来确定状态
        val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
        val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
        val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()

        return when {
            // 正在执行工具
            lastTool != null && !lastTool.isExecuted -> {
                val toolName = lastTool.toolName.removePrefix("mcp__")
                Triple(
                    context.getString(R.string.notification_live_update_chip_tool),
                    context.getString(R.string.notification_live_update_tool, toolName),
                    lastTool.input.take(100)
                )
            }
            // 正在思考（Reasoning 未结束）
            lastReasoning != null && lastReasoning.finishedAt == null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_thinking),
                    context.getString(R.string.notification_live_update_thinking),
                    lastReasoning.reasoning.takeLast(200)
                )
            }
            // 正在写回复
            lastText != null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_writing),
                    lastText.text.takeLast(200)
                )
            }
            // 默认状态
            else -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_title),
                    ""
                )
            }
        }
    }

    private fun cancelLiveUpdateNotification(conversationId: Uuid) {
        context.cancelNotification(getLiveUpdateNotificationId(conversationId))
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = Conversation(
            id = Uuid.random(),
            assistantId = currentConversation.assistantId,
            messageNodes = copiedNodes,
            customSystemPrompt = currentConversation.customSystemPrompt,
        )

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val job = sessions[conversationId]?.getJob() ?: return
        job.cancel()
        runCatching { job.join() }

        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }
}
