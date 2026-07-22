package me.rerere.rikkahub.data.companion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class CompanionDigitalActivityKind {
    PRIVATE_JOURNAL, UNSENT_NOTE, ORGANIZE_FAVORITES, REVIEW_EXPERIENCES,
    ORGANIZE_CONCERNS, PLAY_MINIGAME, WATCH_REPLAY, SHARED_PLAN,
    REVIEW_COMMITMENTS, ORGANIZE_STATE,
}

enum class CompanionDigitalStorage { CIHAI_AND_LIFE_EVENT, LIFE_EVENT, FAVORITES_AND_LIFE_EVENT }
enum class CompanionMemoryWriteRule { NEVER, WHEN_MEANINGFUL, LINK_EXISTING_ONLY }

data class CompanionDigitalActivityDefinition(
    val kind: CompanionDigitalActivityKind,
    val trigger: String,
    val cooldownMillis: Long,
    val expectedDurationMillis: Long,
    val usesModel: Boolean,
    val storage: CompanionDigitalStorage,
    val memoryWriteRule: CompanionMemoryWriteRule,
    val followUpRule: String,
    val requiresEvidenceReference: Boolean = false,
)

object CompanionDigitalActivityRegistry {
    val definitions: Map<CompanionDigitalActivityKind, CompanionDigitalActivityDefinition> = listOf(
        definition(CompanionDigitalActivityKind.PRIVATE_JOURNAL, "有新的真实感受或判断值得留下", 6 * HOUR, 8 * MINUTE, true, CompanionDigitalStorage.CIHAI_AND_LIFE_EVENT, CompanionMemoryWriteRule.WHEN_MEANINGFUL, "只在相关话题自然出现时提及"),
        definition(CompanionDigitalActivityKind.UNSENT_NOTE, "有想保留但此刻不适合发送的话", 3 * HOUR, 3 * MINUTE, true, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.WHEN_MEANINGFUL, "不冒充已发送消息"),
        definition(CompanionDigitalActivityKind.ORGANIZE_FAVORITES, "存在尚未回顾的自主或手动收藏", 12 * HOUR, 2 * MINUTE, false, CompanionDigitalStorage.FAVORITES_AND_LIFE_EVENT, CompanionMemoryWriteRule.LINK_EXISTING_ONLY, "以后只按收藏理由自然引用"),
        definition(CompanionDigitalActivityKind.REVIEW_EXPERIENCES, "存在可追溯的共同经历或记忆", 12 * HOUR, 5 * MINUTE, true, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.LINK_EXISTING_ONLY, "不得把总结扩写成未发生经历"),
        definition(CompanionDigitalActivityKind.ORGANIZE_CONCERNS, "关注事项有新增、到期或状态变化", 2 * HOUR, 2 * MINUTE, false, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.NEVER, "后续只追问仍有效的关注"),
        definition(CompanionDigitalActivityKind.PLAY_MINIGAME, "角色有空且游戏执行器可用", 2 * HOUR, 10 * MINUTE, false, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.WHEN_MEANINGFUL, "必须引用真实对局记录", true),
        definition(CompanionDigitalActivityKind.WATCH_REPLAY, "存在尚未回看的真实对局", 4 * HOUR, 6 * MINUTE, false, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.WHEN_MEANINGFUL, "必须引用真实回放记录", true),
        definition(CompanionDigitalActivityKind.SHARED_PLAN, "用户与角色已明确形成共同计划", 2 * HOUR, 4 * MINUTE, true, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.WHEN_MEANINGFUL, "只跟进仍有效且未取消的计划", true),
        definition(CompanionDigitalActivityKind.REVIEW_COMMITMENTS, "承诺到期、执行或结果发生变化", HOUR, 3 * MINUTE, false, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.NEVER, "只陈述真实履约历史", true),
        definition(CompanionDigitalActivityKind.ORGANIZE_STATE, "状态、关注或待续念头发生有意义变化", 2 * HOUR, 2 * MINUTE, false, CompanionDigitalStorage.LIFE_EVENT, CompanionMemoryWriteRule.NEVER, "过期状态不再作为当前事实"),
    ).associateBy(CompanionDigitalActivityDefinition::kind)

    fun requireRegistered(kind: CompanionDigitalActivityKind): CompanionDigitalActivityDefinition =
        requireNotNull(definitions[kind]) { "Unregistered digital activity: $kind" }

    private fun definition(
        kind: CompanionDigitalActivityKind,
        trigger: String,
        cooldownMillis: Long,
        expectedDurationMillis: Long,
        usesModel: Boolean,
        storage: CompanionDigitalStorage,
        memoryWriteRule: CompanionMemoryWriteRule,
        followUpRule: String,
        requiresEvidenceReference: Boolean = false,
    ) = CompanionDigitalActivityDefinition(kind, trigger, cooldownMillis, expectedDurationMillis, usesModel, storage, memoryWriteRule, followUpRule, requiresEvidenceReference)

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}

data class CompanionDigitalActivityCandidate(
    val kind: CompanionDigitalActivityKind,
    val triggerSatisfied: Boolean,
    val evidenceReference: String? = null,
    val priority: Int = 0,
)

object CompanionDigitalActivitySelector {
    fun select(
        candidates: List<CompanionDigitalActivityCandidate>,
        recentEvents: List<CompanionLifeEvent>,
        nowMillis: Long,
    ): CompanionDigitalActivityCandidate? = candidates.asSequence()
        .filter(CompanionDigitalActivityCandidate::triggerSatisfied)
        .filter { candidate ->
            val definition = CompanionDigitalActivityRegistry.requireRegistered(candidate.kind)
            !definition.requiresEvidenceReference || !candidate.evidenceReference.isNullOrBlank()
        }
        .filter { candidate ->
            val definition = CompanionDigitalActivityRegistry.requireRegistered(candidate.kind)
            val lastStartedAt = recentEvents.filter { it.type == candidate.kind.lifeEventType() }
                .maxOfOrNull(CompanionLifeEvent::startedAt)
            lastStartedAt == null || nowMillis - lastStartedAt >= definition.cooldownMillis
        }
        .sortedWith(compareByDescending<CompanionDigitalActivityCandidate> { it.priority }.thenBy { it.kind.ordinal })
        .firstOrNull()
}

@Serializable
data class CompanionDigitalActivityRequest(
    val assistantId: String,
    val kind: CompanionDigitalActivityKind,
    val title: String,
    val summary: String,
    val evidenceReference: String? = null,
    val details: String = "",
    val relatedMemoryIds: List<String> = emptyList(),
    val source: CompanionLifeEventSource = CompanionLifeEventSource.AGENT,
    val requestedAt: Long = System.currentTimeMillis(),
)

class CompanionDigitalLifeActivityService(private val store: CompanionStore) {
    fun favorites(assistantId: String): List<CompanionFavorite> = store.snapshot(assistantId).favorites
    fun activities(assistantId: String): List<CompanionLifeEvent> =
        store.snapshot(assistantId).lifeEvents.filter { it.type.isDigitalActivityType() }

    suspend fun favoriteMessage(
        assistantId: String,
        messageId: String,
        reason: String,
        feeling: String,
        source: CompanionFavoriteSource = CompanionFavoriteSource.AUTONOMOUS,
        nowMillis: Long = System.currentTimeMillis(),
    ): CompanionFavorite? {
        if (assistantId.isBlank() || !shouldAutonomouslyFavorite(messageId, reason, feeling)) return null
        val favorite = CompanionFavorite(
            assistantId = assistantId,
            messageId = messageId,
            reason = reason.trim(),
            feeling = feeling.trim(),
            source = source,
            createdAt = nowMillis,
        )
        store.updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(
                favorites = listOf(favorite) + snapshot.favorites.filterNot { it.messageId == messageId },
                updatedAt = maxOf(snapshot.updatedAt, nowMillis),
            )
        }
        return favorite
    }

    suspend fun execute(request: CompanionDigitalActivityRequest): CompanionLifeEvent {
        val definition = CompanionDigitalActivityRegistry.requireRegistered(request.kind)
        val validationError = validate(request, definition)
        val event = CompanionLifeEvent(
            assistantId = request.assistantId,
            type = request.kind.lifeEventType(),
            status = if (validationError == null) CompanionLifeEventStatus.COMPLETED else CompanionLifeEventStatus.FAILED,
            title = request.title.ifBlank { request.kind.defaultTitle() },
            summary = validationError ?: request.summary,
            source = request.source,
            evidenceReference = request.evidenceReference,
            detailsJson = buildJsonObject {
                put("activity_kind", request.kind.name)
                put("trigger", definition.trigger)
                put("cooldown_millis", definition.cooldownMillis)
                put("expected_duration_millis", definition.expectedDurationMillis)
                put("uses_model", definition.usesModel)
                put("storage", definition.storage.name)
                put("memory_write_rule", definition.memoryWriteRule.name)
                put("follow_up_rule", definition.followUpRule)
                put("artifact", JsonPrimitive(request.details))
            }.toString(),
            relatedMemoryIds = request.relatedMemoryIds,
            startedAt = request.requestedAt,
            endedAt = request.requestedAt + if (validationError == null) definition.expectedDurationMillis else 0L,
            createdAt = request.requestedAt,
        )
        if (request.assistantId.isNotBlank()) {
            store.updateSnapshot(request.assistantId) { snapshot ->
                snapshot.copy(
                    lifeEvents = snapshot.lifeEvents + event,
                    updatedAt = maxOf(snapshot.updatedAt, request.requestedAt),
                )
            }
        }
        return event
    }

    fun canClaimCompleted(assistantId: String, eventId: String): Boolean =
        activities(assistantId).any { it.id == eventId && it.status == CompanionLifeEventStatus.COMPLETED }

    private fun validate(request: CompanionDigitalActivityRequest, definition: CompanionDigitalActivityDefinition): String? = when {
        request.assistantId.isBlank() -> "Activity was not saved: missing assistant identity."
        request.summary.isBlank() -> "Activity was not completed: no real artifact or result was produced."
        definition.requiresEvidenceReference && request.evidenceReference.isNullOrBlank() ->
            "Activity was not completed: required execution evidence is missing."
        request.kind == CompanionDigitalActivityKind.ORGANIZE_FAVORITES && favorites(request.assistantId).isEmpty() ->
            "Activity was not completed: there are no saved messages to organize."
        else -> null
    }
}

fun shouldAutonomouslyFavorite(messageId: String, reason: String, feeling: String): Boolean =
    messageId.isNotBlank() && reason.trim().length >= 4 && feeling.trim().isNotBlank()

fun CompanionDigitalActivityKind.lifeEventType(): CompanionLifeEventType = when (this) {
    CompanionDigitalActivityKind.PRIVATE_JOURNAL -> CompanionLifeEventType.JOURNAL
    CompanionDigitalActivityKind.UNSENT_NOTE -> CompanionLifeEventType.UNSENT_NOTE
    CompanionDigitalActivityKind.ORGANIZE_FAVORITES -> CompanionLifeEventType.FAVORITE_ORGANIZATION
    CompanionDigitalActivityKind.REVIEW_EXPERIENCES -> CompanionLifeEventType.EXPERIENCE_REVIEW
    CompanionDigitalActivityKind.ORGANIZE_CONCERNS -> CompanionLifeEventType.CONCERN_ORGANIZATION
    CompanionDigitalActivityKind.PLAY_MINIGAME -> CompanionLifeEventType.GAME
    CompanionDigitalActivityKind.WATCH_REPLAY -> CompanionLifeEventType.REPLAY_REVIEW
    CompanionDigitalActivityKind.SHARED_PLAN -> CompanionLifeEventType.SHARED_PLAN
    CompanionDigitalActivityKind.REVIEW_COMMITMENTS -> CompanionLifeEventType.COMMITMENT_REVIEW
    CompanionDigitalActivityKind.ORGANIZE_STATE -> CompanionLifeEventType.STATE_REVIEW
}

fun CompanionLifeEventType.isDigitalActivityType(): Boolean = CompanionDigitalActivityKind.values()
    .any { it.lifeEventType() == this }

private fun CompanionDigitalActivityKind.defaultTitle(): String = when (this) {
    CompanionDigitalActivityKind.PRIVATE_JOURNAL -> "写下私人日记"
    CompanionDigitalActivityKind.UNSENT_NOTE -> "留下一张未发送便签"
    CompanionDigitalActivityKind.ORGANIZE_FAVORITES -> "整理收藏"
    CompanionDigitalActivityKind.REVIEW_EXPERIENCES -> "回顾共同经历"
    CompanionDigitalActivityKind.ORGANIZE_CONCERNS -> "整理关注"
    CompanionDigitalActivityKind.PLAY_MINIGAME -> "玩了一局小游戏"
    CompanionDigitalActivityKind.WATCH_REPLAY -> "看了一次回放"
    CompanionDigitalActivityKind.SHARED_PLAN -> "整理共同计划"
    CompanionDigitalActivityKind.REVIEW_COMMITMENTS -> "复盘承诺"
    CompanionDigitalActivityKind.ORGANIZE_STATE -> "整理状态与待续念头"
}
