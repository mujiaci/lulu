package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionToolExecution
import me.rerere.rikkahub.data.companion.buildToolLifeEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.ZoneId

/**
 * Final deterministic guardrail for digital-life claims.
 *
 * Prompt instructions reduce mistakes, but they are not proof. This transformer
 * checks explicit first-person claims about App activities against completed
 * persisted events or successful tool results from the current generation.
 */
object CompanionLifeClaimOutputTransformer : OutputMessageTransformer, KoinComponent {
    private val companionRuntime: CompanionRuntime by inject()

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val assistantId = ctx.assistant.id.toString()
        val nowMillis = System.currentTimeMillis()
        val currentToolEvents = messages
            .flatMap(UIMessage::getTools)
            .filter { it.isExecuted }
            .mapNotNull { tool ->
                buildToolLifeEvent(
                    assistantId = assistantId,
                    execution = CompanionToolExecution(
                        toolCallId = tool.toolCallId,
                        toolName = tool.toolName,
                        inputJson = tool.input,
                        outputText = tool.output
                            .filterIsInstance<UIMessagePart.Text>()
                            .joinToString("\n") { it.text },
                    ),
                    source = CompanionLifeEventSource.TOOL,
                    nowMillis = nowMillis,
                )
            }
        return sanitizeUnsupportedCompanionLifeClaims(
            messages = messages,
            lifeEvents = companionRuntime.snapshot(assistantId).lifeEvents + currentToolEvents,
            nowMillis = nowMillis,
        )
    }
}

internal fun sanitizeUnsupportedCompanionLifeClaims(
    messages: List<UIMessage>,
    lifeEvents: List<CompanionLifeEvent>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<UIMessage> {
    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    return messages.mapIndexed { index, message ->
        if (index <= lastUserIndex || message.role != MessageRole.ASSISTANT) return@mapIndexed message
        val unsupportedTypes = linkedSetOf<CompanionLifeEventType>()
        val sanitizedParts = message.parts.map { part ->
            if (part !is UIMessagePart.Text) return@map part
            val result = sanitizeUnsupportedDigitalLifeClaims(
                text = part.text,
                lifeEvents = lifeEvents,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )
            unsupportedTypes += result.unsupportedTypes
            part.copy(text = result.text)
        }
        if (unsupportedTypes.isEmpty()) {
            message
        } else {
            message.copy(
                parts = sanitizedParts,
                annotations = message.annotations + UIMessageAnnotation.Metadata(
                    type = COMPANION_FACT_CORRECTION_METADATA_TYPE,
                    data = buildJsonObject {
                        put("unsupported_types", unsupportedTypes.joinToString(",") { it.name })
                    },
                ),
            )
        }
    }
}

internal data class CompanionLifeClaimSanitization(
    val text: String,
    val unsupportedTypes: Set<CompanionLifeEventType> = emptySet(),
)

internal fun sanitizeUnsupportedDigitalLifeClaims(
    text: String,
    lifeEvents: List<CompanionLifeEvent>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CompanionLifeClaimSanitization {
    if (text.isBlank()) return CompanionLifeClaimSanitization(text)
    val unsupported = linkedSetOf<CompanionLifeEventType>()
    var correctionWritten = false
    val sanitized = text
        .split(SENTENCE_BOUNDARY_REGEX)
        .joinToString("") { sentence ->
            val claimType = sentence.detectCompletedDigitalLifeClaim() ?: return@joinToString sentence
            if (lifeEvents.supportClaim(claimType, sentence, nowMillis, zoneId)) return@joinToString sentence
            unsupported += claimType
            if (correctionWritten) {
                ""
            } else {
                correctionWritten = true
                "等等，这件事还没有在 App 里真实发生；我不把设想说成经历。"
            }
        }
        .trim()
    return CompanionLifeClaimSanitization(
        text = sanitized.ifBlank { "这件事还没有在 App 里真实发生，我先不把设想说成经历。" },
        unsupportedTypes = unsupported,
    )
}

private fun String.detectCompletedDigitalLifeClaim(): CompanionLifeEventType? {
    val compact = trim()
    if (compact.isBlank() || FIRST_PERSON_REGEX.find(compact) == null) return null
    if (NON_COMPLETED_CLAIM_MARKERS.any { marker -> marker in compact }) return null
    return DIGITAL_LIFE_CLAIM_RULES.firstOrNull { rule -> rule.pattern.containsMatchIn(compact) }?.type
}

private fun List<CompanionLifeEvent>.supportClaim(
    type: CompanionLifeEventType,
    claimText: String,
    nowMillis: Long,
    zoneId: ZoneId,
): Boolean {
    val range = claimText.claimedTimeRange(nowMillis, zoneId)
    return asSequence()
        .filter { it.type == type && it.status == CompanionLifeEventStatus.COMPLETED }
        .map { it.endedAt ?: it.startedAt }
        .any { occurredAt -> range == null || occurredAt in range }
}

private fun String.claimedTimeRange(nowMillis: Long, zoneId: ZoneId): LongRange? {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val todayStart = now.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    return when {
        "昨天" in this -> {
            val start = now.toLocalDate().minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            start until todayStart
        }
        listOf("今天", "今早", "今晚", "今夜").any(this::contains) -> todayStart..nowMillis
        listOf("刚刚", "刚才", "方才").any(this::contains) ->
            (nowMillis - RECENT_CLAIM_WINDOW_MILLIS).coerceAtLeast(0L)..nowMillis
        else -> null
    }
}

private data class DigitalLifeClaimRule(
    val type: CompanionLifeEventType,
    val pattern: Regex,
)

private val DIGITAL_LIFE_CLAIM_RULES = listOf(
    DigitalLifeClaimRule(
        CompanionLifeEventType.GAME,
        Regex("(?:玩了|玩过|打了|开了|通关了).{0,16}(?:游戏|一局|一把)|(?:游戏|一局|一把).{0,12}(?:玩了|打完|通关)"),
    ),
    DigitalLifeClaimRule(
        CompanionLifeEventType.JOURNAL,
        Regex("(?:写了|写完|记了|保存了).{0,12}(?:日记|辞海)"),
    ),
    DigitalLifeClaimRule(
        CompanionLifeEventType.MUSIC,
        Regex("(?:听了|放了|播了|切了).{0,12}(?:歌|音乐|歌曲)|(?:歌|音乐|歌曲).{0,12}(?:听完|播完)"),
    ),
    DigitalLifeClaimRule(
        CompanionLifeEventType.MEMORY_REVIEW,
        Regex("(?:翻了|看了|整理了|回顾了).{0,12}(?:记忆|回忆|收藏)"),
    ),
    DigitalLifeClaimRule(
        CompanionLifeEventType.STUDY_REVIEW,
        Regex("(?:看了|整理了|检查了|调整了).{0,12}(?:学习计划|考研计划|今日计划)"),
    ),
)

private val FIRST_PERSON_REGEX = Regex("(?:^|[，。！？!?\\s])(我|咱|人家|本小姐|本少爷)")
private val SENTENCE_BOUNDARY_REGEX = Regex("(?<=[。！？!?\n])")
private val NON_COMPLETED_CLAIM_MARKERS = listOf("没玩", "没有玩", "还没", "想玩", "想写", "想听", "打算", "准备", "如果", "假如", "可能会", "梦见")
private const val RECENT_CLAIM_WINDOW_MILLIS = 6L * 60L * 60L * 1_000L
const val COMPANION_FACT_CORRECTION_METADATA_TYPE = "companion_fact_correction"
