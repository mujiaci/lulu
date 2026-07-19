package me.rerere.rikkahub.data.voicecall

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import kotlin.uuid.Uuid

class VoiceCallRepository(
    private val context: Context,
) {
    private val storageFile: File
        get() = File(context.filesDir, "voice_call_sessions.json")

    fun getSessions(): List<VoiceCallSession> {
        return runCatching {
            if (!storageFile.exists()) return emptyList()
            JsonInstant.decodeFromString<List<VoiceCallSession>>(storageFile.readText())
        }.getOrDefault(emptyList())
            .sortedByDescending { it.startedAt }
    }

    fun getSession(id: String): VoiceCallSession? {
        return getSessions().firstOrNull { it.id == id }
    }

    fun createSession(
        conversationId: String,
        assistantId: String,
        assistantName: String,
        initialLines: List<VoiceCallLine> = emptyList(),
        persistImmediately: Boolean = true,
    ): VoiceCallSession {
        val now = System.currentTimeMillis()
        val session = VoiceCallSession(
            id = Uuid.random().toString(),
            conversationId = conversationId,
            assistantId = assistantId,
            assistantName = assistantName,
            startedAt = now,
            transcript = initialLines,
        )
        if (persistImmediately) upsertSession(session)
        return session
    }

    fun upsertSession(session: VoiceCallSession) {
        val sessions = getSessions().filterNot { it.id == session.id } + session
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(JsonInstant.encodeToString(sessions.sortedByDescending { it.startedAt }))
    }

    fun appendLine(sessionId: String, line: VoiceCallLine) {
        val session = getSession(sessionId) ?: return
        upsertSession(session.copy(transcript = session.transcript + line))
    }

    fun appendLine(session: VoiceCallSession, line: VoiceCallLine): VoiceCallSession {
        val updated = session.copy(transcript = session.transcript + line)
        upsertSession(updated)
        return updated
    }

    fun replaceSession(session: VoiceCallSession): VoiceCallSession {
        upsertSession(session)
        return session
    }

    fun endSession(sessionId: String) {
        val session = getSession(sessionId) ?: return
        endSession(session)
    }

    fun endSession(session: VoiceCallSession): VoiceCallSession? {
        if (!session.hasUserFacingContent()) {
            deleteSession(session.id)
            return null
        }
        if (session.status == VoiceCallStatus.Ended) return session
        val updated = session.copy(
            status = VoiceCallStatus.Ended,
            endedAt = System.currentTimeMillis(),
        )
        upsertSession(updated)
        return updated
    }

    fun deleteSession(sessionId: String) {
        val sessions = getSessions().filterNot { it.id == sessionId }
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(JsonInstant.encodeToString(sessions.sortedByDescending { it.startedAt }))
    }

    fun deleteSessionsByAssistant(assistantId: String) {
        val sessions = getSessions().filterNot { it.assistantId == assistantId }
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(JsonInstant.encodeToString(sessions.sortedByDescending { it.startedAt }))
    }

    fun recentAssistantOpenings(
        conversationId: String,
        assistantId: String,
        limit: Int = 6,
    ): List<String> = selectRecentAssistantOpenings(
        sessions = getSessions(),
        conversationId = conversationId,
        assistantId = assistantId,
        limit = limit,
    )

    fun getSummary(): VoiceCallStatsSummary {
        return summarizeVoiceCallSessions(getSessions())
    }
}

internal fun selectRecentAssistantOpenings(
    sessions: List<VoiceCallSession>,
    conversationId: String,
    assistantId: String,
    limit: Int = 6,
): List<String> = sessions
    .asSequence()
    .filter { session ->
        session.conversationId == conversationId &&
            session.assistantId == assistantId
    }
    .sortedByDescending(VoiceCallSession::startedAt)
    .mapNotNull { session ->
        session.transcript
            .firstOrNull { line -> line.role == VoiceCallRole.Assistant && line.text.isNotBlank() }
            ?.text
            ?.trim()
    }
    .distinct()
    .take(limit.coerceAtLeast(0))
    .toList()

fun VoiceCallSession.hasUserFacingContent(): Boolean {
    return sleepMode || transcript.any { it.role == VoiceCallRole.User && it.text.isNotBlank() }
}

data class VoiceCallStatsSummary(
    val sessionCount: Int = 0,
    val visibleLineCount: Int = 0,
)

fun summarizeVoiceCallSessions(sessions: List<VoiceCallSession>): VoiceCallStatsSummary =
    VoiceCallStatsSummary(
        sessionCount = sessions.size,
        visibleLineCount = sessions.sumOf { session ->
            session.transcript.count { line -> line.role != VoiceCallRole.System && line.text.isNotBlank() }
        },
    )
