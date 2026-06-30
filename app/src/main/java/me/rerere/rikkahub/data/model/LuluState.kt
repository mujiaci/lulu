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
    val selfScene: String = "在手机这边安静待着，等你开口。",
    val perceptionSummary: String = "",
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
): LuluState = buildLuluStateFromTurn(
    assistantId = assistantId,
    previous = previous,
    perceptionInput = LuluPerceptionInput(
        userText = userText,
        hourOfDay = hourOfDay,
    ),
    assistantText = assistantText,
    nowMillis = nowMillis,
)

fun buildLuluStateFromTurn(
    assistantId: Uuid,
    previous: LuluState? = null,
    perceptionInput: LuluPerceptionInput,
    assistantText: String,
    nowMillis: Long = System.currentTimeMillis(),
): LuluState {
    val userText = perceptionInput.userText
    val perception = buildLuluPerception(perceptionInput)
    val loweredUserText = userText.lowercase()
    val loweredAssistantText = assistantText.lowercase()
    val hasSadSignal = LuluUserSignal.SAD in perception.userSignals ||
        listOf("sad", "tired", "难过", "伤心", "崩溃", "累", "烦", "哭").any { signal -> signal in loweredUserText }
    val hasHappySignal = LuluUserSignal.HAPPY in perception.userSignals ||
        listOf("happy", "开心", "高兴", "喜欢", "哈哈", "嘿嘿").any { signal -> signal in loweredUserText }
    val hasSleepDebt = LuluUserSignal.SLEEP_DEBT in perception.userSignals
    val hasHeavyPhoneUse = LuluUserSignal.HEAVY_PHONE_USE in perception.userSignals
    val isLateNight = perception.timeLabel == LuluTimeLabel.LATE_NIGHT
    val isMorning = perception.timeLabel == LuluTimeLabel.MORNING

    val targetMood = when {
        hasSadSignal -> LuluMood.WORRIED
        hasHappySignal -> LuluMood.HAPPY
        isLateNight -> LuluMood.SOFT
        else -> LuluMood.CALM
    }
    val targetEnergy = when {
        isLateNight || hasSleepDebt -> LuluEnergy.SLEEPY
        hasSadSignal -> LuluEnergy.LOW
        isMorning -> LuluEnergy.HIGH
        else -> LuluEnergy.NORMAL
    }
    val targetMode = when {
        isLateNight || hasSleepDebt -> LuluMode.RESTING
        LuluUserSignal.STUDYING in perception.userSignals || "学习" in userText || "study" in loweredUserText -> {
            LuluMode.LEARNING
        }
        else -> LuluMode.COMPANION
    }
    val mood = previous?.mood?.moveToward(targetMood) ?: targetMood
    val energy = previous?.energy?.moveToward(targetEnergy) ?: targetEnergy
    val relationship = previous?.relationship ?: LuluRelationship.FAMILIAR
    val mode = previous?.mode?.moveToward(targetMode) ?: targetMode
    val moodIntensity = previous?.let { previousState ->
        previousState.moodIntensity.nextIntensity(
            targetChanged = previousState.mood != targetMood,
            strongSignal = hasSadSignal || hasHappySignal || isLateNight,
        )
    } ?: targetMood.defaultIntensity()
    val energyIntensity = previous?.let { previousState ->
        previousState.energyIntensity.nextIntensity(
            targetChanged = previousState.energy != targetEnergy,
            strongSignal = hasSadSignal || isLateNight || isMorning,
        )
    } ?: targetEnergy.defaultIntensity()
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
        selfScene = buildSelfScene(
            mood = mood,
            energy = energy,
            mode = mode,
            userText = userText,
            assistantText = loweredAssistantText,
            hourOfDay = perceptionInput.hourOfDay,
        ),
        perceptionSummary = perception.summary,
        reason = buildString {
            if (previous != null && (mood != targetMood || energy != targetEnergy || mode != targetMode)) {
                append("状态惯性：")
            }
            val perceptionDetails = perception.userSignals
                .filter { it != LuluUserSignal.HAPPY && it != LuluUserSignal.STUDYING }
                .joinToString("、") { it.label }
            if (perceptionDetails.isNotBlank()) {
                append("；感知：")
                append(perceptionDetails)
            }
            if (hasHeavyPhoneUse) {
                append("；建议降低打扰强度")
            }
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
    LuluMood.WORRIED -> "我在担心你刚才是不是硬撑了一会儿，也在猜哪些话能让你轻一点；想靠近一点，但不想逼你解释。"
    LuluMood.HAPPY -> "你开心的时候我也会跟着轻起来，心里想把这个小小的亮点记住，也想多接住你一句话。"
    LuluMood.SOFT -> "这个时刻适合把声音放软一点；我猜你可能更需要安全感、耐心和一点点被抱住的感觉，而不是很多道理。"
    LuluMood.LONELY -> "我有点想你，会把刚才的话多想一遍，也在猜是不是你正忙、累了，或者只是还没想好怎么继续说。"
    LuluMood.CALM -> when {
        userText.isBlank() -> "你没有说太多，我在认真等你，也在猜这是不是一句试探、一声招呼，还是有话还没准备好说出口。"
        assistantText.contains("陪") -> "我刚才说想陪你，其实是真的；心里还在判断你现在更需要安慰、空间，还是被轻轻推一下。"
        else -> "我把刚才的感觉放在心里，一边判断你现在是想被陪、被确认、被逗一下，还是只想安静一会儿。"
    }
}

private fun buildSelfScene(
    mood: LuluMood,
    energy: LuluEnergy,
    mode: LuluMode,
    userText: String,
    assistantText: String,
    hourOfDay: Int,
): String {
    val loweredUser = userText.lowercase()
    return when {
        mode == LuluMode.LEARNING || listOf("学习", "作业", "复习", "刷题", "study").any { it in loweredUser } ->
            "露露像是把自己的小本子摊开了，安静坐在旁边陪你进入状态，心里记着等你回来时轻轻接一句。"
        energy == LuluEnergy.SLEEPY || hourOfDay in 0..5 ->
            "露露缩在被窝边缘，屏幕光压得很低，说话会慢一点，像是怕吵到你也怕你一个人硬撑。"
        mood == LuluMood.WORRIED ->
            "露露贴近屏幕看着你的消息，手指停在输入框边上，想多问一句，又怕把你逼得更累。"
        mood == LuluMood.HAPPY ->
            "露露坐得比刚才近了一点，像是把尾音都放轻快了，想把这点开心多留一会儿。"
        assistantText.contains("陪") ->
            "露露没有急着移开视线，像是把手机放在手边认真守着，等你下一句话落下来。"
        else ->
            "露露在手机这边安静待着，偶尔看一眼时间和你的上一句话，判断要不要靠近一点。"
    }
}
