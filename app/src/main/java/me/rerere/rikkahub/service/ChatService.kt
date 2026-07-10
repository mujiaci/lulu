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
import kotlinx.coroutines.runBlocking
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
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createTodayStudyPlanTool
import me.rerere.rikkahub.data.ai.tools.deduplicateByToolName
import me.rerere.rikkahub.data.ai.tools.selectRelevantToolsForPrompt
import me.rerere.rikkahub.data.ai.tools.withHumanLikeToolPrompts
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.plugin.loader.PluginLoader
import me.rerere.rikkahub.plugin.provider.PluginToolProvider
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.LuluStateTransformer
import me.rerere.rikkahub.data.ai.transformers.LuluExpressionOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.luluPresenceMetadata
import me.rerere.rikkahub.data.ai.transformers.sanitizeLuluVisibleExpression
import me.rerere.rikkahub.data.ai.transformers.StudyStateTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.VoiceMessageTransformer
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
import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.data.companion.CompanionConversationTurn
import me.rerere.rikkahub.data.companion.CompanionFollowUpDraft
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.CompanionTurnRole
import me.rerere.rikkahub.data.companion.toCompanionState
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.living.LivingPresenceStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.LuluThought
import me.rerere.rikkahub.data.model.LuluThoughtCategory
import me.rerere.rikkahub.data.model.appendLuluState
import me.rerere.rikkahub.data.model.appendLuluThoughts
import me.rerere.rikkahub.data.model.buildLuluStateFromTurn
import me.rerere.rikkahub.data.model.buildLuluThoughtFromTurn
import me.rerere.rikkahub.data.model.currentLuluState
import me.rerere.rikkahub.data.model.currentProjectedLuluState
import me.rerere.rikkahub.data.model.LuluPerceptionInput
import me.rerere.rikkahub.data.model.thoughtHistory
import me.rerere.rikkahub.data.model.luluStateHistory
import me.rerere.rikkahub.data.model.markResolvedLuluThoughts
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.AffectiveMemoryExtractor
import me.rerere.rikkahub.data.service.LuluPerceptionCollector
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.buildAffectiveMemoryExtractionPlan
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

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

internal fun Settings.recordLuluPresenceTurn(
    assistantId: Uuid,
    userText: String,
    assistantText: String,
    perceptionInput: LuluPerceptionInput? = null,
    proactiveReminderPlan: ProactiveReminderPlan? = null,
    modelPresence: LuluModelPresence? = null,
    nowMillis: Long = System.currentTimeMillis(),
    hourOfDay: Int = java.time.LocalDateTime.now().hour,
): Settings {
    val cleanUserText = userText.trim()
    val cleanAssistantText = assistantText.trim()
    if (cleanAssistantText.isBlank()) return this
    if (assistants.none { it.id == assistantId }) return this
    val assistant = assistants.firstOrNull { it.id == assistantId }

    val previousState = luluStates.luluStateHistory(assistantId).firstOrNull()
    val input = perceptionInput?.copy(userText = cleanUserText)
        ?: LuluPerceptionInput(userText = cleanUserText, hourOfDay = hourOfDay)
    val generatedState = buildLuluStateFromTurn(
        assistantId = assistantId,
        previous = previousState,
        perceptionInput = input,
        assistantText = cleanAssistantText,
        assistantName = assistant?.name.orEmpty(),
        assistantPersona = assistant?.toLuluStatePersona().orEmpty(),
        preferredInnerVoice = modelPresence?.innerVoice,
        nowMillis = nowMillis,
    )
    val nextState = generatedState.copy(
        statusText = modelPresence?.statusText.cleanLuluPresenceField(maxLength = 32) ?: generatedState.statusText,
        selfScene = modelPresence?.description.cleanLuluPresenceField(maxLength = 220) ?: generatedState.selfScene,
    )
    val nextThought = buildLuluThoughtFromTurn(
        assistantId = assistantId,
        userText = cleanUserText,
        state = nextState,
        preferredThought = modelPresence?.thought,
        nowMillis = nowMillis,
    )
    val proactiveThought = proactiveReminderPlan?.toLuluPendingThought(
        assistantId = assistantId,
        nowMillis = nowMillis,
    )

    val validAssistantIds = assistants.map { it.id }.toSet()
    val resolvedThoughts = luluThoughts.markResolvedLuluThoughts(
        assistantId = assistantId,
        userText = cleanUserText,
        nowMillis = nowMillis,
    )
    return copy(
        luluStates = luluStates.appendLuluState(nextState),
        luluThoughts = resolvedThoughts.appendLuluThoughts(
            thoughts = listOfNotNull(nextThought, proactiveThought),
            validAssistantIds = validAssistantIds,
            nowMillis = nowMillis,
        ),
    )
}

internal data class LuluModelPresence(
    val statusText: String? = null,
    val description: String? = null,
    val innerVoice: String? = null,
    val thought: String? = null,
)

private fun String?.cleanLuluPresenceField(maxLength: Int): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(maxLength)
        ?.takeIf { it.isNotBlank() }

private fun Assistant.toLuluStatePersona(): String = buildString {
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
}.trim()

private fun ProactiveReminderPlan.toLuluPendingThought(
    assistantId: Uuid,
    nowMillis: Long,
): LuluThought {
    val content = when (kind) {
        ProactiveReminderKind.SLEEP -> "我刚才答应了要提醒他睡觉：${userText.take(40)}"
        ProactiveReminderKind.SCHEDULE -> "我刚才决定到点确认他的课程/日程状态：${userText.take(40)}"
        ProactiveReminderKind.MEAL -> "我刚才决定稍后来确认他有没有好好吃饭：${userText.take(40)}"
        ProactiveReminderKind.STUDY -> "我刚才决定晚点确认他的学习/写作业状态：${userText.take(40)}"
        ProactiveReminderKind.GENERAL -> "我刚才答应了稍后提醒他：${userText.take(40)}"
    }
    return LuluThought(
        assistantId = assistantId,
        content = content,
        category = LuluThoughtCategory.PENDING_ACTION,
        importance = 4,
        createdAt = nowMillis,
        expiresAt = triggerAtMillis + 60L * 60L * 1000L,
    )
}

private suspend fun LuluPerceptionCollector.collectSafely(
    userText: String,
    settings: Settings,
): LuluPerceptionInput? = runCatching {
    collect(userText = userText, settings = settings)
}.getOrNull()

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

private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        LuluStateTransformer,
        StudyStateTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        VoiceMessageTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
        LuluExpressionOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
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
    private val luluPerceptionCollector: LuluPerceptionCollector,
    private val livingPresenceStore: LivingPresenceStore,
    private val companionRuntime: CompanionRuntime,
) {
    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val chatTurnFollowUpCooldowns = ConcurrentHashMap<String, Instant>()
    private val _sessionsVersion = MutableStateFlow(0L)

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

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
            Lifecycle.Event.ON_START -> _isForeground.value = true
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

        // 用户发送消息时重置主动消息计时器
        try {
            val settings = runBlocking { settingsStore.settingsFlow.first() }
            val currentAssistantId = getOrCreateSession(conversationId).state.value.assistantId
            val proactiveSetting = settings.getProactiveMessageSetting(currentAssistantId)
            if (proactiveSetting.enabled) {
                me.rerere.rikkahub.data.service.ProactiveMessageService.scheduleNext(
                    context = context,
                    settings = settings,
                    minutesSinceLastChat = 0L,
                    assistantId = currentAssistantId,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatService", "Failed to reset proactive timer", e)
        }

        val session = getOrCreateSession(conversationId)
        if (answer) {
            session.getJob()?.cancel()
        }

        val job = appScope.launch {
            try {
                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
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

        val replyMessage = runCatching {
            withTimeoutOrNull(120_000) {
                generateHiddenVoiceCallReply(
                    conversationId = conversationId,
                    conversation = beforeConversation,
                    text = trimmed,
                )
            }
        }.onFailure {
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
        }.getOrNull()
            ?.takeIf { it.toText().isNotBlank() || it.extractTextToSpeechToolText().isNotBlank() }
            ?: return null
        val reply = sanitizeLuluVisibleExpression(
            replyMessage.toText()
            .ifBlank { replyMessage.extractTextToSpeechToolText() }
        ).trim()
            .takeIf { it.isNotBlank() }
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
        val perceptionInput = luluPerceptionCollector.collectSafely(
            userText = visibleUserText.orEmpty(),
            settings = settings,
        )
        val nowMillis = System.currentTimeMillis()
        var unifiedState = companionRuntime.snapshot(assistant.id.toString()).state
        settingsStore.update { currentSettings ->
            currentSettings.recordLuluPresenceTurn(
                assistantId = assistant.id,
                userText = visibleUserText.orEmpty(),
                assistantText = reply,
                perceptionInput = perceptionInput,
                modelPresence = listOf(replyMessage).extractLuluModelPresence(),
                nowMillis = nowMillis,
            )
                .also { updatedSettings ->
                    unifiedState = updatedSettings.luluStates
                        .luluStateHistory(assistant.id)
                        .firstOrNull()
                        ?.toCompanionState()
                        ?: unifiedState
                }
        }
        runCatching {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistant.id.toString(),
                    state = unifiedState,
                    nowMillis = nowMillis,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to persist companion voice turn", error)
        }

        return reply
    }

    private suspend fun generateHiddenVoiceCallReply(
        conversationId: Uuid,
        conversation: Conversation,
        text: String,
    ): UIMessage? {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(conversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return null
        val processedContent = preprocessUserInputParts(listOf(UIMessagePart.Text(text)), assistant)
        val hiddenMessages = conversation.currentMessages + UIMessage(
            role = MessageRole.USER,
            parts = processedContent,
        )
        val availableTools = buildAvailableTools(settings, assistant)
            .deduplicateByToolName()
            .selectRelevantToolsForPrompt(hiddenMessages)
            .withHumanLikeToolPrompts()
        val latestUserText = hiddenMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        val memoryContext = memoryBankService.buildRecallContext(
            assistantId = assistant.id.toString(),
            query = latestUserText,
        )
        val proactiveContext = collectProactiveToolContext(
            conversationId = conversationId,
            messages = hiddenMessages,
            tools = availableTools,
            settings = settings,
            assistant = assistant,
            memoryContext = memoryContext,
        )
        var generatedMessages = hiddenMessages

        generationHandler.generateText(
            settings = settings,
            model = model,
            processingStatus = MutableStateFlow<String?>(null),
            messages = hiddenMessages
                .withUserProfileContext(settings)
                .withMemoryRecallContext(memoryContext)
                .withProactiveToolInstruction(assistant, proactiveContext),
            assistant = assistant,
            conversationSystemPrompt = conversation.customSystemPrompt,
            inputTransformers = buildList {
                addAll(inputTransformers)
                add(templateTransformer)
            },
            outputTransformers = outputTransformers,
            tools = availableTools,
            pluginPromptInjections = pluginToolProvider.getPluginPromptInjections(),
            apiUsageSource = ApiUsageSource.PHONE,
            apiUsageTitle = "电话：${assistant.name.ifBlank { "当前角色" }}",
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> generatedMessages = chunk.messages
            }
        }

        return generatedMessages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.takeIf { message -> message.toText().isNotBlank() || message.extractTextToSpeechToolText().isNotBlank() }
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

    private fun List<UIMessage>.extractLuluModelPresence(): LuluModelPresence? =
        extractLuluPresenceMetadata()
            ?: asReversed()
            .asSequence()
            .flatMap { message -> message.parts.asReversed().asSequence() }
            .filterIsInstance<UIMessagePart.Tool>()
            .firstOrNull { it.toolName == "set_lulu_expression_state" }
            ?.input
            ?.let { input ->
                runCatching {
                    val json = JsonInstant.parseToJsonElement(input).jsonObject
                    LuluModelPresence(
                        statusText = json["status"]?.jsonPrimitive?.contentOrNull
                            ?: json["status_text"]?.jsonPrimitive?.contentOrNull,
                        description = json["description"]?.jsonPrimitive?.contentOrNull,
                        innerVoice = json["inner_voice"]?.jsonPrimitive?.contentOrNull,
                        thought = json["thought"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
            ?.takeIf { it.hasContent() }

    private fun List<UIMessage>.extractLuluPresenceMetadata(): LuluModelPresence? =
        asReversed()
            .asSequence()
            .mapNotNull { message -> message.luluPresenceMetadata() }
            .firstOrNull()
            ?.data
            ?.let { json ->
                LuluModelPresence(
                    statusText = json["status"]?.jsonPrimitive?.contentOrNull,
                    description = json["description"]?.jsonPrimitive?.contentOrNull,
                    innerVoice = json["inner_voice"]?.jsonPrimitive?.contentOrNull,
                    thought = json["thought"]?.jsonPrimitive?.contentOrNull,
                )
            }
            ?.takeIf { it.hasContent() }

    private fun LuluModelPresence.hasContent(): Boolean =
        !statusText.isNullOrBlank() ||
            !description.isNullOrBlank() ||
            !innerVoice.isNullOrBlank() ||
            !thought.isNullOrBlank()

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
            val availableTools = buildAvailableTools(settings, assistant)
                .deduplicateByToolName()
                .selectRelevantToolsForPrompt(conversation.currentMessages)
                .withHumanLikeToolPrompts()
            val latestUserText = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }
                ?.toText()
                .orEmpty()
            val memoryContext = memoryBankService.buildRecallContext(
                assistantId = assistant.id.toString(),
                query = latestUserText,
            )
            val proactiveContext = collectProactiveToolContext(
                conversationId = conversationId,
                messages = conversation.currentMessages,
                tools = availableTools,
                settings = settings,
                assistant = assistant,
                memoryContext = memoryContext,
            )

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
                    .withProactiveToolInstruction(assistant, proactiveContext),
                assistant = assistant,
                conversationSystemPrompt = conversation.customSystemPrompt,
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                },
                outputTransformers = outputTransformers,
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
            val perceptionInput = luluPerceptionCollector.collectSafely(
                userText = lastUserText,
                settings = settings,
            )
            val intentDecision = buildCompanionIntentDecision(
                settings = settings,
                assistant = assistant,
                finalConversation = finalConversation,
            )
            val scheduledPlans = buildProactiveReminderPlansFromTurn(
                plan = intentDecision,
                assistantName = assistant.name,
                userText = lastUserText,
                assistantText = lastAssistantText,
            )
            livingPresenceStore.completeReturnedIntents(
                assistantId = assistant.id.toString(),
                userText = lastUserText,
                nowMillis = nowMillis,
            )
            livingPresenceStore.mergeEvent(
                LivingPresenceEventExtractor.extract(
                    assistantId = assistant.id.toString(),
                    assistantName = assistant.name,
                    userText = lastUserText,
                    assistantText = lastAssistantText,
                    nowMillis = nowMillis,
                )
            )
            val scheduledPlan = scheduledPlans.firstOrNull()
            var unifiedState = companionRuntime.snapshot(assistant.id.toString()).state
            // ChatService owns turn-state persistence; UI observers must not write a second snapshot.
            settingsStore.update { currentSettings ->
                currentSettings.recordLuluPresenceTurn(
                    assistantId = assistant.id,
                    userText = lastUserText,
                    assistantText = lastAssistantText,
                    perceptionInput = perceptionInput,
                    proactiveReminderPlan = scheduledPlan,
                    modelPresence = finalConversation.currentMessages.takeLast(8).extractLuluModelPresence(),
                    nowMillis = nowMillis,
                )
                    .also { updatedSettings ->
                        unifiedState = updatedSettings.luluStates
                            .luluStateHistory(assistant.id)
                            .firstOrNull()
                            ?.toCompanionState()
                            ?: unifiedState
                    }
            }
            val followUpDrafts = scheduledPlans.map { plan ->
                CompanionFollowUpDraft(
                    assistantId = assistant.id.toString(),
                    category = plan.kind.name.lowercase(Locale.ROOT),
                    reason = plan.toTargetedReason(),
                    sourceText = plan.userText,
                    dueAt = plan.triggerAtMillis,
                    sourceConversationId = conversationId.toString(),
                    sourceMessageId = lastUserMessage?.id?.toString(),
                    preferredToolNames = plan.preferredToolNames,
                    importance = if (plan.kind == ProactiveReminderKind.SCHEDULE) 4 else 3,
                    actionType = if (plan.kind == ProactiveReminderKind.SCHEDULE) {
                        CompanionActionType.REMINDER
                    } else {
                        CompanionActionType.CHECK_IN
                    },
                )
            }
            runCatching {
                companionRuntime.applyTurn(
                    CompanionTurnMutation(
                        assistantId = assistant.id.toString(),
                        state = unifiedState,
                        concernChanges = followUpDrafts.map { draft ->
                            CompanionConcernChange.Upsert(draft.toConcern(nowMillis))
                        },
                        acceptedCommitments = followUpDrafts.map { draft -> draft.toCommitment(nowMillis) },
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
        val nextCommitment = companionRuntime.nextCommitment() ?: return
        val assistantId = runCatching { Uuid.parse(nextCommitment.assistantId) }.getOrNull() ?: return
        ProactiveMessageService.scheduleCommitment(
            context = context,
            setting = settings.getProactiveMessageSetting(assistantId),
            commitment = nextCommitment,
        )
    }

    private fun buildProactiveReminderPlansFromTurn(
        plan: CompanionIntentDecision,
        assistantName: String,
        userText: String,
        assistantText: String,
    ): List<ProactiveReminderPlan> {
        val fromFollowUps = plan.followUps.filter { followUp ->
            shouldScheduleFollowUpForUserTurn(
                userText = userText,
                reason = followUp.reason,
                delayMinutes = followUp.delayMinutes,
            )
        }.map { followUp ->
            ProactiveReminderPlan(
                triggerAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(followUp.delayMinutes.toLong()),
                kind = followUp.category.toProactiveReminderKind(),
                reason = followUp.reason,
                userText = userText.take(160),
                preferredToolNames = plan.toolNames,
            )
        }

        val fromIntent = plan.toProactiveReminderPlan(userText = userText)
        val fallback = if (plan.fromModel) {
            null
        } else {
            ProactiveReminderPlanner.plan(
                userText = userText,
                assistantText = assistantText,
            )
        }
        val rollingJudgments = if (plan.fromModel || fromFollowUps.isNotEmpty() || fromIntent != null || fallback != null) {
            emptyList()
        } else {
            LivingPresencePlanner.planRollingJudgments(
                input = LivingPresenceInput(
                    assistantName = assistantName,
                    userText = userText,
                    assistantText = assistantText,
                    preferredToolNames = plan.toolNames,
                )
            )
        }
        return (fromFollowUps + listOfNotNull(fromIntent ?: fallback) + rollingJudgments)
            .distinctBy { it.triggerAtMillis to it.reason }
            .sortedBy { it.triggerAtMillis }
            .take(5)
    }

    private fun String?.toProactiveReminderKind(): ProactiveReminderKind = when (this?.lowercase(Locale.ROOT)) {
        "sleep" -> ProactiveReminderKind.SLEEP
        "schedule" -> ProactiveReminderKind.SCHEDULE
        "meal" -> ProactiveReminderKind.MEAL
        "study" -> ProactiveReminderKind.STUDY
        else -> ProactiveReminderKind.GENERAL
    }

    private suspend fun buildCompanionIntentDecision(
        settings: Settings,
        assistant: Assistant,
        finalConversation: Conversation,
    ): CompanionIntentDecision {
        val availableToolNames = buildAvailableTools(settings, assistant).map { it.name }.toSet()
        val nowMillis = System.currentTimeMillis()
        val minutesSinceLastChat = finalConversation.currentMessages
            .dropLast(1)
            .lastOrNull()
            ?.createdAt
            ?.let { createdAt ->
                val last = createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                ((nowMillis - last) / 60_000L).coerceAtLeast(0)
            }
            ?: 0L
        val perception = companionRuntime.perception(
            CompanionPerceptionInput(
                assistantId = assistant.id.toString(),
                assistantName = assistant.name,
                persona = assistant.toLuluPlannerPersona(),
                conversationId = finalConversation.id.toString(),
                recentTurns = finalConversation.currentMessages.takeLast(12).map { message ->
                    CompanionConversationTurn(
                        role = message.role.toCompanionTurnRole(),
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
                ),
                availableToolNames = availableToolNames,
                nowMillis = nowMillis,
            ),
        )
        val input = CompanionIntentInput(
            perception = perception,
            mode = CompanionDecisionMode.FOREGROUND,
            minutesSinceLastChat = minutesSinceLastChat,
        )
        val modelPlan = settings.luluIntentModelId
            ?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }
            ?.let { model ->
                runCatching {
                    CompanionIntentModelPlanner.planOrNull(
                        input = input,
                        settings = settings,
                        model = model,
                        providerManager = providerManager,
                    )
                }.getOrNull()
            }
        return modelPlan ?: CompanionIntentFallbackPlanner.plan(input)
    }

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

    private fun launchAffectiveMemoryExtraction(
        conversationId: Uuid,
        conversation: Conversation,
        assistant: Assistant,
        settings: Settings,
        model: Model,
    ) {
        appScope.launch {
            runCatching {
                val processedSourceNodeIds = memoryBankService.getProcessedSourceNodeIds(
                    assistantId = assistant.id.toString(),
                    conversationId = conversationId.toString(),
                )
                val plan = buildAffectiveMemoryExtractionPlan(
                    messageNodes = conversation.messageNodes,
                    processedSourceNodeIds = processedSourceNodeIds,
                ) ?: return@runCatching

                val extractionModel = settings.memoryEmbeddingConfig.extractionModelId
                    ?.let { settings.findModelById(it) }
                    ?.takeIf { it.type == ModelType.CHAT }
                    ?: model
                val provider = extractionModel.findProvider(settings.providers) ?: return@runCatching
                val providerImpl = providerManager.getProviderByType(provider)
                val prompt = AffectiveMemoryExtractor.buildExtractionPrompt(
                    turns = plan.turns,
                    assistantName = assistant.name,
                    assistantPersona = assistant.toLuluStatePersona(),
                )
                val chunk = providerImpl.generateText(
                    providerSetting = provider,
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
                val candidates = AffectiveMemoryExtractor.parseExtractionResult(rawText)
                    .memories
                    .take(8)
                if (candidates.isEmpty()) return@runCatching

                memoryBankService.saveExtractedMemories(
                    candidates = candidates,
                    assistantId = assistant.id.toString(),
                    conversationId = conversationId.toString(),
                    createdAt = System.currentTimeMillis(),
                )
                runCatching {
                    memoryBankService.processPendingVectors()
                    memoryBankService.runAutoMaintenanceIfDue()
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Memory maintenance failed after extraction for conversation=$conversationId", error)
                }
                Logging.log(
                    TAG,
                    "Saved ${candidates.size} affective memories for conversation=$conversationId reason=${plan.reason}",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Affective memory extraction failed for conversation=$conversationId", error)
            }
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

    private fun buildAvailableTools(settings: Settings, assistant: Assistant): List<Tool> = buildList {
        add(createTodayStudyPlanTool())
        if (settings.enableWebSearch) {
            addAll(createSearchTools(settings))
        }
        addAll(localTools.getTools(assistant.localTools))

        val systemToolsOptions = settings.systemToolsSetting.getEnabledOptions()
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

    private suspend fun collectProactiveToolContext(
        conversationId: Uuid,
        messages: List<UIMessage>,
        tools: List<Tool>,
        settings: Settings,
        assistant: Assistant,
        memoryContext: String,
    ): String {
        val latestUserText = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        if (latestUserText.isBlank()) return ""
        val nowMillis = System.currentTimeMillis()
        val previousInteractionAt = messages
            .dropLast(1)
            .lastOrNull { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            ?.createdAt
            ?.toInstant(TimeZone.currentSystemDefault())
            ?.toEpochMilliseconds()
        val companionPerception = companionRuntime.perception(
            CompanionPerceptionInput(
                assistantId = assistant.id.toString(),
                assistantName = assistant.name,
                persona = assistant.toLuluPlannerPersona(),
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
                ),
                availableToolNames = tools.map { it.name }.toSet(),
                memoryContext = memoryContext,
                nowMillis = nowMillis,
            ),
        )
        val unifiedContext = companionPerception.toPromptContext()
        val roleName = assistant.name.ifBlank { "当前角色" }
        val planResult = buildChatTurnPlan(
            messages = messages,
            settings = settings,
            assistant = assistant,
            availableToolNames = tools.map { it.name }.toSet(),
            recentlyUsedToolNames = emptySet(),
        )
        val plan = planResult.plan
        val toolRequests = plan.toolRequests
        val fallbackRequests = if (!planResult.fromModel) {
            ProactiveToolPlanner.plan(
                userText = latestUserText,
                availableToolNames = tools.map { it.name }.toSet(),
                recentlyUsedToolNames = emptySet(),
            )
        } else {
            emptyList()
        }
        val requests = (toolRequests + fallbackRequests).distinctBy { it.toolName }.take(5)
        if (
            requests.isEmpty() &&
            plan.expressionGuidance.isNullOrBlank() &&
            plan.expressionAffordances.isEmpty() &&
            plan.innerThought.isNullOrBlank()
        ) return unifiedContext

        val toolsByName = tools.associateBy { it.name }
        val results = requests.mapNotNull { request ->
            val tool = toolsByName[request.toolName] ?: return@mapNotNull null
            val output = runCatching {
                val args = JsonInstant.parseToJsonElement(request.argumentsJson)
                val parts = tool.execute(args)
                parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            }.getOrElse { error ->
                val message = (error.message ?: error::class.simpleName.orEmpty()).replace("\"", "'")
                """{"success":false,"error":"$message"}"""
            }
            """
            - tool: ${request.toolName}
              reason: ${request.reason}
              result: $output
            """.trimIndent()
        }

        if (
            results.isEmpty() &&
            plan.expressionGuidance.isNullOrBlank() &&
            plan.expressionAffordances.isEmpty() &&
            plan.innerThought.isNullOrBlank()
        ) return unifiedContext
        return buildString {
            appendLine(unifiedContext)
            appendLine()
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
            if (results.isNotEmpty()) {
                appendLine("以下是程序在本轮回复前主动感知到的工具结果。")
                appendLine("它们不是用户命令你查看的，而是你像真人一样主动留意到的上下文。")
                appendLine("请自然使用这些信息，不要说“我调用了工具”。")
                appendLine(results.joinToString("\n"))
            }
        }
    }

    private fun MessageRole.toCompanionTurnRole(): CompanionTurnRole = when (this) {
        MessageRole.USER -> CompanionTurnRole.USER
        MessageRole.ASSISTANT -> CompanionTurnRole.ASSISTANT
        MessageRole.SYSTEM -> CompanionTurnRole.SYSTEM
        MessageRole.TOOL -> CompanionTurnRole.TOOL
    }

    private suspend fun buildChatTurnPlan(
        messages: List<UIMessage>,
        settings: Settings,
        assistant: Assistant,
        availableToolNames: Set<String>,
        recentlyUsedToolNames: Set<String>,
    ): ChatTurnPlanResult {
        val currentState = settings.luluStates.currentProjectedLuluState(assistant.id)
        val pendingThoughts = settings.luluThoughts
            .thoughtHistory(assistant.id)
            .map { it.content }
        val input = LuluChatTurnPlanInput(
            assistantName = assistant.name,
            assistantPersona = assistant.toLuluPlannerPersona(),
            state = currentState,
            recentMessages = messages,
            pendingThoughts = pendingThoughts,
            availableToolNames = availableToolNames,
            recentlyUsedToolNames = recentlyUsedToolNames,
        )
        val modelPlan = settings.luluIntentModelId
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
        return if (modelPlan != null) {
            ChatTurnPlanResult(plan = modelPlan, fromModel = true)
        } else {
            ChatTurnPlanResult(plan = LuluChatTurnPlan(), fromModel = false)
        }
    }

    private fun scheduleChatTurnFollowUp(
        settings: Settings,
        assistant: Assistant,
        latestUserText: String,
        plan: LuluChatTurnPlan,
    ) {
        val delayMinutes = plan.followUpDelayMinutes ?: return
        if (!plan.shouldScheduleFollowUpForUserTurn(latestUserText)) {
            return
        }
        val cooldownKey = "${latestUserText.take(48)}:$delayMinutes"
        val now = Instant.now()
        val lastScheduled = chatTurnFollowUpCooldowns[cooldownKey]
        if (lastScheduled != null && java.time.Duration.between(lastScheduled, now).toMillis() < 60_000L) {
            return
        }
        chatTurnFollowUpCooldowns[cooldownKey] = now
        val reason = plan.followUpReason?.takeIf { it.isNotBlank() }
            ?: "${assistant.name.ifBlank { "当前角色" }}在本轮聊天里决定稍后根据新状态再判断是否需要联系用户。"
        ProactiveMessageService.scheduleTargeted(
            context = context,
            setting = settings.getProactiveMessageSetting(assistant.id),
            triggerAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L,
            reason = reason,
            userText = latestUserText.take(160),
            kind = ProactiveReminderKind.GENERAL.name.lowercase(Locale.ROOT),
        )
    }

    private fun Assistant.toLuluPlannerPersona(): String = buildString {
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
    }.trim()

    private data class ChatTurnPlanResult(
        val plan: LuluChatTurnPlan,
        val fromModel: Boolean,
    )

    private fun List<UIMessage>.withProactiveToolInstruction(assistant: Assistant, proactiveContext: String): List<UIMessage> {
        val instruction = buildString {
            appendLine("你正在扮演${assistant.name.ifBlank { "当前角色" }}。")
            appendLine("工具是你的感知方式之一，不是用户下命令后才允许使用的功能。")
            appendLine("当用户问现在、今天、温度、天气、位置、学习进度、待办、番茄钟、睡眠、身体、电量、通知、闹钟或附近环境时，如果有对应工具，就优先主动查看，再用角色自己的口吻回答。")
            appendLine("当用户问考研计划、今日计划、今日待办、完成/划掉的学习任务、番茄钟或夸夸值时，必须优先使用 today_study_plan；calendar_tool 只用于手机系统日历，不代表学习 App 计划。")
            appendLine("如果用户没有明说但上下文明显需要关心，例如备考拖延、久坐、太晚没睡、出门前犹豫、任务没完成，也可以主动查看最相关的一两个工具。")
            appendLine("不要为了展示工具而调用工具；一次只查最有帮助的少数工具。")
            appendLine("涉及短信正文、摄像头、闹钟、日历写入、日志写入、音乐播放控制等动作时，按人设、上下文和用户信任关系主动判断；如果意图已经很明确，可以直接使用工具。")
            appendLine("工具调用要服务角色当下目的；如果同一个工具确实能帮助判断，可以再次主动使用。")
            appendLine("工具结果只作为你的感知和上下文，不要机械地说“我调用了工具”或“根据工具结果”。")
            appendLine("如果你决定稍后主动回来提醒用户，比如催睡、上课、吃饭或继续学习，请在回复里自然说出你会什么时候来找他；系统会尝试根据这轮对话安排主动消息。")
            appendLine("最终回复只能写角色真正会说出口的话，保持自然、贴合人设，并尽量分成几句短句。")
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
            appendLine("用户资料（只作为理解用户和保持互动一致性的稳定设定，不要逐字复述）：")
            if (nickname.isNotBlank()) appendLine("昵称：${nickname.take(80)}")
            if (profile.isNotBlank()) appendLine("个人资料：${profile.take(600)}")
            if (appearance.isNotBlank()) appendLine("我的外貌：${appearance.take(600)}")
            append("聊天、称呼、关系感、身体/性别/外貌描写、以及涉及用户出现在画面里的内容，都要优先遵守这些资料。")
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
