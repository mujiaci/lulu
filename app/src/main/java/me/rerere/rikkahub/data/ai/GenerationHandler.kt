package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.time.Clock

private const val TAG = "GenerationHandler"

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        pluginPromptInjections: List<String> = emptyList(),
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            val toolsInternal = buildList {
                Log.i(TAG, "generateInternal: build tools($assistant)")
                // Only caller-provided tools are enabled for normal chat.
                addAll(tools)
            }

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    pluginPromptInjections = pluginPromptInjections,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                )
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = toolsInternal,
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                )
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                val finishedAt = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                messages = messages.markTrailingAssistantMessagesFinished(finishedAt)
                emit(GenerationChunk.Messages(messages))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = tools.map { tool ->
                    val toolDef = toolsInternal.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef?.needsApproval == true && tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != tools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            // Handle tools (execute approved tools, handle denied tools)
            val executedTools = arrayListOf<UIMessagePart.Tool>()
            toolsToProcess.forEach { tool ->
                when (tool.approvalState) {
                    is ToolApprovalState.Denied -> {
                        // Tool was denied by user
                        val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put(
                                                "error",
                                                JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                            )
                                        }
                                    )
                                )
                            )
                        )
                    }

                    is ToolApprovalState.Answered -> {
                        // Tool was answered by user (e.g., ask_user tool)
                        val answer = (tool.approvalState as ToolApprovalState.Answered).answer
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(answer)
                            )
                        )
                    }

                    is ToolApprovalState.Pending -> {
                        // Should not reach here, but just in case
                    }

                    else -> {
                        // Auto or Approved - execute the tool
                        runCatching {
                            val toolDef = toolsInternal.find { toolDef -> toolDef.name == tool.toolName }
                                ?: error("Tool ${tool.toolName} not found")
                            val args = runCatching {
                                json.parseToJsonElement(tool.input.ifBlank { "{}" })
                            }.getOrElse {
                                error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}")
                            }
                            Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: $args")
                            val result = toolDef.execute(args)
                            executedTools += tool.copy(output = result)
                        }.onFailure {
                            it.printStackTrace()
                            executedTools += tool.copy(
                                output = listOf(
                                    UIMessagePart.Text(
                                        json.encodeToString(
                                            buildJsonObject {
                                                put(
                                                    "error",
                                                    JsonPrimitive(buildString {
                                                        append("[${it.javaClass.name}] ${it.message}")
                                                        append("\n${it.stackTraceToString()}")
                                                    })
                                                )
                                            }
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }

            if (executedTools.isEmpty()) {
                // No results to add (all tools were pending)
                break
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    )
                )
            )
        }

    }.flowOn(Dispatchers.IO)

    private fun List<UIMessage>.markTrailingAssistantMessagesFinished(
        finishedAt: kotlinx.datetime.LocalDateTime,
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

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        pluginPromptInjections: List<String> = emptyList(),
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
    ) {
        val effectiveSystemPrompt =
            if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                conversationSystemPrompt
            } else {
                assistant.systemPrompt
            }
        val recentChatsPrompt = if (assistant.enableRecentChatsReference) {
            buildRecentChatsPrompt(assistant, conversationRepo)
        } else {
            ""
        }
        val toolSystemPrompts = tools.map { tool -> tool.name to tool.systemPrompt(model, messages) }
        val skipReplyPrompt = if (assistant.allowSkipReply) {
            "## Skip Reply\n" +
                "If you determine that no reply is needed (e.g., the user's message doesn't require a response, or you have nothing meaningful to add), you may reply with exactly `[SKIP]` (without any other text). This message will be hidden from the user. Use this sparingly and only when truly appropriate."
        } else {
            ""
        }
        val system = buildString {
            if (effectiveSystemPrompt.isNotBlank()) {
                append(effectiveSystemPrompt)
            }

            // Recent chat reference is optional. Vector memory is injected before this handler.
            if (recentChatsPrompt.isNotBlank()) {
                appendLine()
                append(recentChatsPrompt)
            }

            // Tool prompts
            toolSystemPrompts.forEach { (_, prompt) ->
                appendLine()
                append(prompt)
            }

            // Plugin prompt injections
            if (pluginPromptInjections.isNotEmpty()) {
                pluginPromptInjections.forEach { injection ->
                    appendLine()
                    appendLine()
                    append(injection)
                }
            }

            // Allow skip reply
            if (skipReplyPrompt.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine(skipReplyPrompt)
            }
        }
        val limitedMessages = messages.limitContext(assistant.contextMessageSize)
        val preTransformMessages = buildList {
            if (system.isNotBlank()) add(UIMessage.system(prompt = system))
            addAll(limitedMessages)
        }
        val internalMessages = preTransformMessages.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            processingStatus = processingStatus,
        )
        val breakdown = buildGenerationTokenBreakdown(
            effectiveSystemPrompt = effectiveSystemPrompt,
            recentChatsPrompt = recentChatsPrompt,
            toolSystemPrompts = toolSystemPrompts,
            pluginPromptInjections = pluginPromptInjections,
            skipReplyPrompt = skipReplyPrompt,
            tools = tools,
            limitedMessages = limitedMessages,
            preTransformMessages = preTransformMessages,
            internalMessages = internalMessages,
        )

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
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
        if (stream) {
            val apiLog = AILogging.Generation(
                params = params,
                messages = messages,
                sentMessages = internalMessages,
                breakdown = breakdown,
                providerSetting = provider,
                stream = true
            )
            aiLoggingManager.addLog(apiLog)
            try {
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect {
                    messages = messages.handleMessageChunk(chunk = it, model = model)
                    it.usage?.let { usage ->
                        aiLoggingManager.updateGenerationUsage(apiLog.id, usage)
                        messages = messages.mapIndexed { index, message ->
                            if (index == messages.lastIndex) {
                                message.copy(usage = message.usage.merge(usage))
                            } else {
                                message
                            }
                        }
                    }
                    onUpdateMessages(messages)
                }
                aiLoggingManager.finishGeneration(apiLog.id)
            } catch (e: Throwable) {
                aiLoggingManager.finishGeneration(apiLog.id, e.message ?: e::class.simpleName)
                throw e
            }
        } else {
            val apiLog = AILogging.Generation(
                params = params,
                messages = messages,
                sentMessages = internalMessages,
                breakdown = breakdown,
                providerSetting = provider,
                stream = false
            )
            aiLoggingManager.addLog(apiLog)
            try {
                val chunk = providerImpl.generateText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params,
                )
                messages = messages.handleMessageChunk(chunk = chunk, model = model)
                chunk.usage?.let { usage ->
                    aiLoggingManager.updateGenerationUsage(apiLog.id, usage)
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex) {
                            message.copy(
                                usage = message.usage.merge(usage)
                            )
                        } else {
                            message
                        }
                    }
                }
                onUpdateMessages(messages)
                aiLoggingManager.finishGeneration(apiLog.id)
            } catch (e: Throwable) {
                aiLoggingManager.finishGeneration(apiLog.id, e.message ?: e::class.simpleName)
                throw e
            }
        }
    }

    private fun buildGenerationTokenBreakdown(
        effectiveSystemPrompt: String,
        recentChatsPrompt: String,
        toolSystemPrompts: List<Pair<String, String>>,
        pluginPromptInjections: List<String>,
        skipReplyPrompt: String,
        tools: List<Tool>,
        limitedMessages: List<UIMessage>,
        preTransformMessages: List<UIMessage>,
        internalMessages: List<UIMessage>,
    ): GenerationTokenBreakdown {
        val preTransformText = preTransformMessages.joinToTokenEstimateText()
        val internalText = internalMessages.joinToTokenEstimateText()
        val toolDefinitionsText = tools.joinToString("\n\n") { tool ->
            buildString {
                appendLine("name: ${tool.name}")
                appendLine("description: ${tool.description}")
                tool.parameters()?.let { appendLine("parameters: $it") }
            }
        }
        val toolSystemPromptText = toolSystemPrompts.joinToString("\n\n") { (name, prompt) ->
            if (prompt.isBlank()) "" else "systemPrompt[$name]:\n$prompt"
        }
        val toolText = listOf(toolDefinitionsText, toolSystemPromptText)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val sections = listOf(
            GenerationTokenSection(
                label = "人设/基础系统提示词",
                estimatedTokens = estimateTokens(effectiveSystemPrompt),
                messageCount = if (effectiveSystemPrompt.isBlank()) 0 else 1,
                charCount = effectiveSystemPrompt.length,
            ),
            GenerationTokenSection(
                label = "近期会话引用",
                estimatedTokens = estimateTokens(recentChatsPrompt),
                messageCount = if (recentChatsPrompt.isBlank()) 0 else 1,
                charCount = recentChatsPrompt.length,
            ),
            GenerationTokenSection(
                label = "工具定义与工具提示词",
                estimatedTokens = estimateTokens(toolText),
                messageCount = tools.size,
                charCount = toolText.length,
            ),
            GenerationTokenSection(
                label = "插件提示词注入",
                estimatedTokens = estimateTokens(pluginPromptInjections.joinToString("\n\n")),
                messageCount = pluginPromptInjections.size,
                charCount = pluginPromptInjections.sumOf { it.length },
            ),
            GenerationTokenSection(
                label = "跳过回复规则",
                estimatedTokens = estimateTokens(skipReplyPrompt),
                messageCount = if (skipReplyPrompt.isBlank()) 0 else 1,
                charCount = skipReplyPrompt.length,
            ),
            GenerationTokenSection(
                label = "记忆/主动提醒等额外系统上下文",
                estimatedTokens = estimateTokens(limitedMessages.filter { it.role == MessageRole.SYSTEM }.joinToTokenEstimateText()),
                messageCount = limitedMessages.count { it.role == MessageRole.SYSTEM },
                charCount = limitedMessages.filter { it.role == MessageRole.SYSTEM }.sumOf { it.estimateTextLength() },
            ),
            GenerationTokenSection(
                label = "历史对话上下文",
                estimatedTokens = estimateTokens(limitedMessages.filter { it.role != MessageRole.SYSTEM }.joinToTokenEstimateText()),
                messageCount = limitedMessages.count { it.role != MessageRole.SYSTEM },
                charCount = limitedMessages.filter { it.role != MessageRole.SYSTEM }.sumOf { it.estimateTextLength() },
            ),
            GenerationTokenSection(
                label = "状态栏/学习状态/模板等变换器增量",
                estimatedTokens = estimateTokensFromCharCount((internalText.length - preTransformText.length).coerceAtLeast(0)),
                messageCount = (internalMessages.size - preTransformMessages.size).coerceAtLeast(0),
                charCount = (internalText.length - preTransformText.length).coerceAtLeast(0),
            ),
        ).filter { it.estimatedTokens > 0 || it.messageCount > 0 || it.charCount > 0 }
        return GenerationTokenBreakdown(
            sections = sections,
            toolNames = tools.map { it.name },
        )
    }

    private fun List<UIMessage>.joinToTokenEstimateText(): String = joinToString("\n\n") { message ->
        buildString {
            appendLine(message.role.name)
            message.parts.forEach { part ->
                appendLine(
                    when (part) {
                        is UIMessagePart.Text -> part.text
                        is UIMessagePart.Reasoning -> part.reasoning
                        is UIMessagePart.Tool -> buildString {
                            appendLine("tool: ${part.toolName}")
                            appendLine("input: ${part.input}")
                            if (part.output.isNotEmpty()) {
                                appendLine("output:")
                                part.output.forEach { output ->
                                    appendLine(
                                        when (output) {
                                            is UIMessagePart.Text -> output.text
                                            else -> output.toString()
                                        }
                                    )
                                }
                            }
                        }
                        else -> part.toString()
                    }
                )
            }
        }
    }

    private fun UIMessage.estimateTextLength(): Int = parts.sumOf { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.length
            is UIMessagePart.Reasoning -> part.reasoning.length
            is UIMessagePart.Tool -> part.toolName.length + part.input.length + part.output.sumOf { output ->
                when (output) {
                    is UIMessagePart.Text -> output.text.length
                    else -> output.toString().length
                }
            }
            else -> part.toString().length
        }
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val cjkChars = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val otherChars = text.length - cjkChars
        return kotlin.math.ceil(cjkChars / 1.6 + otherChars / 4.0).toInt().coerceAtLeast(1)
    }

    private fun estimateTokensFromCharCount(charCount: Int): Int {
        if (charCount <= 0) return 0
        return kotlin.math.ceil(charCount / 3.0).toInt().coerceAtLeast(1)
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""
            val params = TextGenerationParams(
                model = model,
                reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
            )
            val apiLog = AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = true,
            )
            aiLoggingManager.addLog(apiLog)

            try {
                providerHandler.streamText(
                    providerSetting = provider,
                    messages = messages,
                    params = params,
                ).collect { chunk ->
                    messages = messages.handleMessageChunk(chunk)
                    chunk.usage?.let { usage ->
                        aiLoggingManager.updateGenerationUsage(apiLog.id, usage)
                    }
                    translatedText = messages.lastOrNull()?.toText() ?: ""

                    if (translatedText.isNotBlank()) {
                        onStreamUpdate?.invoke(translatedText)
                        emit(translatedText)
                    }
                }
                aiLoggingManager.finishGeneration(apiLog.id)
            } catch (e: Throwable) {
                aiLoggingManager.finishGeneration(apiLog.id, e.message ?: e::class.simpleName)
                throw e
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val params = TextGenerationParams(
                model = model,
                temperature = 0.3f,
                topP = 0.95f,
                customBody = listOf(
                    CustomBody(
                        key = "translation_options",
                        value = buildJsonObject {
                            put("source_lang", JsonPrimitive("auto"))
                            put(
                                "target_lang",
                                JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                            )
                        }
                    )
                )
            )
            val apiLog = AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = false,
            )
            aiLoggingManager.addLog(apiLog)
            try {
                val chunk = providerHandler.generateText(
                    providerSetting = provider,
                    messages = messages,
                    params = params,
                )
                chunk.usage?.let { usage ->
                    aiLoggingManager.updateGenerationUsage(apiLog.id, usage)
                }
                val translatedText = chunk.choices.firstOrNull()?.message?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
                aiLoggingManager.finishGeneration(apiLog.id)
            } catch (e: Throwable) {
                aiLoggingManager.finishGeneration(apiLog.id, e.message ?: e::class.simpleName)
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)
}
