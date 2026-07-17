package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantTTSProvider
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.message.buildSpeakableMessageText
import me.rerere.rikkahub.ui.context.LocalTTSState
import kotlin.uuid.Uuid

@Composable
fun TTSAutoPlay(setting: Settings, conversation: Conversation, loading: Boolean) {
    val assistant = remember(setting.assistants, conversation.assistantId) {
        setting.assistants.firstOrNull { it.id == conversation.assistantId }
    }
    if (assistant?.autoPlayVoice != true) return

    val tts = LocalTTSState.current
    val isAvailable by tts.isAvailable.collectAsState()
    var spokenMessageIds by remember(conversation.id) {
        mutableStateOf(emptySet<Uuid>())
    }
    var readyForNewReplies by remember(conversation.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(conversation.id, conversation.messageNodes.isNotEmpty()) {
        if (!readyForNewReplies && conversation.messageNodes.isNotEmpty()) {
            spokenMessageIds = conversation.messageNodes.finishedAssistantMessageIdsForAutoPlay()
            readyForNewReplies = true
        }
    }

    val target = remember(conversation.messageNodes, spokenMessageIds, readyForNewReplies, loading) {
        if (readyForNewReplies && !loading) {
            findAutoPlayTTSMessages(
                nodes = conversation.messageNodes,
                spokenMessageIds = spokenMessageIds,
            )
        } else {
            emptyList()
        }
    }

    LaunchedEffect(target.map { it.id }, isAvailable, setting.displaySetting.ttsOnlyReadQuoted) {
        if (!isAvailable || target.isEmpty()) return@LaunchedEffect
        delay(450)
        val text = buildAutoPlayTTSBatchText(
            messages = target,
            onlyReadQuoted = setting.displaySetting.ttsOnlyReadQuoted,
        )
        spokenMessageIds = spokenMessageIds + target.map { it.id }
        if (text == null) return@LaunchedEffect
        tts.speak(text, providerOverride = setting.getAssistantTTSProvider(conversation.assistantId))
    }
}

internal fun List<MessageNode>.finishedAssistantMessageIdsForAutoPlay(): Set<Uuid> =
    map { it.currentMessage }
        .filter { it.isFinishedSpeakableAssistantMessage() }
        .map { it.id }
        .toSet()

internal fun findAutoPlayTTSMessages(
    nodes: List<MessageNode>,
    spokenMessageIds: Set<Uuid>,
): List<UIMessage> {
    return nodes
        .map { it.currentMessage }
        .filter { it.isFinishedSpeakableAssistantMessage() && it.id !in spokenMessageIds }
}

internal fun buildAutoPlayTTSBatchText(
    messages: List<UIMessage>,
    onlyReadQuoted: Boolean,
): String? {
    val seen = LinkedHashSet<String>()
    messages.forEach { message ->
        buildSpeakableMessageText(
            message = message,
            onlyReadQuoted = onlyReadQuoted,
        )
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach { seen += it }
    }
    return seen.joinToString("\n").takeIf { it.isNotBlank() }
}

private fun UIMessage.isFinishedSpeakableAssistantMessage(): Boolean =
    role == MessageRole.ASSISTANT &&
        finishedAt != null &&
        toText().isNotBlank()
