package me.rerere.rikkahub.data.service

import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.living.LivingPresenceStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.voicecall.VoiceCallRepository
import kotlin.uuid.Uuid

class AssistantInteractionResetService(
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryBankService: MemoryBankService,
    private val voiceCallRepository: VoiceCallRepository,
    private val cihaiStore: CihaiStore,
    private val companionRuntime: CompanionRuntime,
    private val livingPresenceStore: LivingPresenceStore,
) {
    suspend fun clearAssistantRecords(assistantId: Uuid) {
        val id = assistantId.toString()
        runStep("聊天与收藏") { conversationRepository.deleteConversationOfAssistant(assistantId) }
        runStep("旧角色记忆") { memoryRepository.deleteMemoriesOfAssistant(id) }
        runStep("长期记忆库") { memoryBankService.deleteMemoriesByAssistant(id) }
        runStep("电话记录") { voiceCallRepository.deleteSessionsByAssistant(id) }
        runStep("辞海记录") { cihaiStore.clearAssistantRecords(id) }
        runStep("统一陪伴状态") { companionRuntime.clearAssistant(id) }
        runStep("旧挂心意图") { livingPresenceStore.clearAssistant(id) }
    }

    private suspend fun runStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw AssistantInteractionResetException(name, error)
        }
    }
}

class AssistantInteractionResetException(
    val stage: String,
    cause: Throwable,
) : IllegalStateException("清除角色记录失败：$stage", cause)
