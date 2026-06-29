package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid

const val LULU_STATE_HISTORY_LIMIT = 80

@Serializable
data class LuluState(
    val assistantId: Uuid,
    val statusText: String = "在发呆",
    val innerVoice: String = "今天也想被好好陪着。",
    val mood: LuluMood = LuluMood.CALM,
    val moodIntensity: Float = 0.35f,
    val energy: LuluEnergy = LuluEnergy.NORMAL,
    val energyIntensity: Float = 0.5f,
    val relationship: LuluRelationship = LuluRelationship.FAMILIAR,
    val relationshipIntensity: Float = 0.45f,
    val mode: LuluMode = LuluMode.COMPANION,
    val updatedAt: Long = 0L,
    val sinceAt: Long = updatedAt,
    val reason: String = "默认状态",
)

fun LuluState.durationMillis(nowMillis: Long = System.currentTimeMillis()): Long =
    (nowMillis - sinceAt).coerceAtLeast(0L)

@Serializable
enum class LuluMood(val label: String) {
    @SerialName("calm")
    CALM("平静"),

    @SerialName("happy")
    HAPPY("开心"),

    @SerialName("soft")
    SOFT("柔软"),

    @SerialName("lonely")
    LONELY("有点寂寞"),

    @SerialName("worried")
    WORRIED("担心"),
}

@Serializable
enum class LuluEnergy(val label: String) {
    @SerialName("low")
    LOW("没什么电"),

    @SerialName("normal")
    NORMAL("刚刚好"),

    @SerialName("high")
    HIGH("元气满满"),

    @SerialName("sleepy")
    SLEEPY("有点困"),
}

@Serializable
enum class LuluRelationship(val label: String) {
    @SerialName("reserved")
    RESERVED("还在靠近"),

    @SerialName("familiar")
    FAMILIAR("熟悉"),

    @SerialName("close")
    CLOSE("很亲近"),

    @SerialName("attached")
    ATTACHED("很黏你"),
}

@Serializable
enum class LuluMode(val label: String) {
    @SerialName("companion")
    COMPANION("陪伴中"),

    @SerialName("thinking")
    THINKING("在想事情"),

    @SerialName("learning")
    LEARNING("在学习"),

    @SerialName("resting")
    RESTING("休息中"),
}

fun List<LuluState>.luluStateHistory(assistantId: Uuid): List<LuluState> =
    filter { it.assistantId == assistantId }
        .sortedByDescending { it.updatedAt }
        .take(LULU_STATE_HISTORY_LIMIT)

fun List<LuluState>.currentLuluState(assistantId: Uuid): LuluState =
    luluStateHistory(assistantId).firstOrNull() ?: LuluState(assistantId = assistantId)

fun List<LuluState>.normalizedLuluStates(validAssistantIds: Set<Uuid>): List<LuluState> =
    filter { it.assistantId in validAssistantIds }
        .sortedByDescending { it.updatedAt }
        .groupBy { it.assistantId }
        .values
        .flatMap { states -> states.take(LULU_STATE_HISTORY_LIMIT) }

fun List<LuluState>.appendLuluState(state: LuluState): List<LuluState> =
    (this + state).normalizedLuluStates(
        validAssistantIds = (map { it.assistantId } + state.assistantId).toSet()
    )

fun buildLuluStateFromTurn(
    assistantId: Uuid,
    previous: LuluState? = null,
    userText: String,
    assistantText: String,
    nowMillis: Long = System.currentTimeMillis(),
    hourOfDay: Int = LocalDateTime.now().hour,
): LuluState {
    val loweredUserText = userText.lowercase()
    val loweredAssistantText = assistantText.lowercase()
    val hasSadSignal = listOf("sad", "tired", "难过", "伤心", "崩溃", "累", "烦", "哭")
        .any { signal -> signal in loweredUserText }
    val hasHappySignal = listOf("happy", "开心", "高兴", "喜欢", "哈哈", "嘿嘿")
        .any { signal -> signal in loweredUserText }
    val isLateNight = hourOfDay in 0..5
    val isMorning = hourOfDay in 6..10

    val targetMood = when {
        hasSadSignal -> LuluMood.WORRIED
        hasHappySignal -> LuluMood.HAPPY
        isLateNight -> LuluMood.SOFT
        else -> LuluMood.CALM
    }
    val targetEnergy = when {
        isLateNight -> LuluEnergy.SLEEPY
        hasSadSignal -> LuluEnergy.LOW
        isMorning -> LuluEnergy.HIGH
        else -> LuluEnergy.NORMAL
    }
    val targetMode = when {
        isLateNight -> LuluMode.RESTING
        "学习" in userText || "study" in loweredUserText -> LuluMode.LEARNING
        else -> LuluMode.COMPANION
    }
    val mood = previous?.mood?.moveToward(targetMood) ?: targetMood
    val energy = previous?.energy?.moveToward(targetEnergy) ?: targetEnergy
    val relationship = previous?.relationship ?: LuluRelationship.FAMILIAR
    val mode = previous?.mode?.moveToward(targetMode) ?: targetMode
    val moodIntensity = previous?.moodIntensity.nextIntensity(
        targetChanged = previous.mood != targetMood,
        strongSignal = hasSadSignal || hasHappySignal || isLateNight,
    ) ?: targetMood.defaultIntensity()
    val energyIntensity = previous?.energyIntensity.nextIntensity(
        targetChanged = previous.energy != targetEnergy,
        strongSignal = hasSadSignal || isLateNight || isMorning,
    ) ?: targetEnergy.defaultIntensity()
    val relationshipIntensity = previous?.relationshipIntensity ?: relationship.defaultIntensity()
    val sinceAt = previous
        ?.takeIf { it.mood == mood && it.energy == energy && it.mode == mode }
        ?.sinceAt
        ?: nowMillis

    return LuluState(
        assistantId = assistantId,
        statusText = when {
            isLateNight -> "有点困了"
            hasSadSignal -> "在担心你"
            hasHappySignal -> "心情很好"
            isMorning -> "元气满满"
            else -> "陪着你"
        },
        innerVoice = buildInnerVoice(mood = mood, userText = userText, assistantText = loweredAssistantText),
        mood = mood,
        moodIntensity = moodIntensity,
        energy = energy,
        energyIntensity = energyIntensity,
        relationship = relationship,
        relationshipIntensity = relationshipIntensity,
        mode = mode,
        updatedAt = nowMillis,
        sinceAt = sinceAt,
        reason = buildString {
            if (previous != null && (mood != targetMood || energy != targetEnergy || mode != targetMode)) {
                append("状态惯性：")
            }
            append("最近对话：${userText.take(36).ifBlank { "没有文字内容" }}")
        },
    )
}

private fun LuluMood.moveToward(target: LuluMood): LuluMood {
    if (this == target) return this
    if (this == LuluMood.CALM) return when (target) {
        LuluMood.WORRIED, LuluMood.HAPPY -> LuluMood.SOFT
        else -> target
    }
    return target
}

private fun LuluEnergy.moveToward(target: LuluEnergy): LuluEnergy {
    if (this == target) return this
    if (this == LuluEnergy.NORMAL && target == LuluEnergy.LOW) return LuluEnergy.NORMAL
    if (this == LuluEnergy.NORMAL && target == LuluEnergy.SLEEPY) return LuluEnergy.SLEEPY
    return target
}

private fun LuluMode.moveToward(target: LuluMode): LuluMode {
    if (this == target) return this
    if (this == LuluMode.COMPANION && target == LuluMode.RESTING) return LuluMode.RESTING
    if (this == LuluMode.COMPANION && target == LuluMode.LEARNING) return LuluMode.LEARNING
    return target
}

private fun Float?.nextIntensity(targetChanged: Boolean, strongSignal: Boolean): Float {
    val current = this ?: 0.45f
    val delta = when {
        strongSignal -> 0.18f
        targetChanged -> 0.10f
        else -> -0.04f
    }
    return (current + delta).coerceIn(0.15f, 1.0f)
}

private fun LuluMood.defaultIntensity(): Float = when (this) {
    LuluMood.CALM -> 0.35f
    LuluMood.HAPPY -> 0.65f
    LuluMood.SOFT -> 0.55f
    LuluMood.LONELY -> 0.7f
    LuluMood.WORRIED -> 0.72f
}

private fun LuluEnergy.defaultIntensity(): Float = when (this) {
    LuluEnergy.LOW -> 0.35f
    LuluEnergy.NORMAL -> 0.5f
    LuluEnergy.HIGH -> 0.7f
    LuluEnergy.SLEEPY -> 0.6f
}

private fun LuluRelationship.defaultIntensity(): Float = when (this) {
    LuluRelationship.RESERVED -> 0.25f
    LuluRelationship.FAMILIAR -> 0.45f
    LuluRelationship.CLOSE -> 0.7f
    LuluRelationship.ATTACHED -> 0.86f
}

private fun buildInnerVoice(
    mood: LuluMood,
    userText: String,
    assistantText: String,
): String = when (mood) {
    LuluMood.WORRIED -> "你刚刚听起来不太舒服，我想多陪你一会儿。"
    LuluMood.HAPPY -> "你开心的时候，我也会忍不住跟着轻快起来。"
    LuluMood.SOFT -> "夜里声音会变轻，我想安静地守在你旁边。"
    LuluMood.LONELY -> "我有点想你，所以会把刚才的话多想一遍。"
    LuluMood.CALM -> when {
        userText.isBlank() -> "你没有说太多，但我还在认真等你。"
        assistantText.contains("陪") -> "我刚刚说想陪你，其实是真的。"
        else -> "我把刚才的话记在心里，慢慢想你现在需要什么。"
    }
}
