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

fun LuluState.projectForSilence(nowMillis: Long = System.currentTimeMillis()): LuluState {
    if (updatedAt <= 0L) return this
    val silenceMillis = (nowMillis - updatedAt).coerceAtLeast(0L)
    val silenceMinutes = silenceMillis / 60_000L
    if (silenceMinutes < 8) return this

    val projected = when {
        silenceMinutes >= 8 * 60 -> copy(
            statusText = "在做自己的事",
            innerVoice = "你很久没说话了，我没有一直盯着屏幕，但心里还留着一个位置，想着你回来时我能自然接住。",
            mood = if (mood == LuluMood.WORRIED) LuluMood.SOFT else LuluMood.CALM,
            moodIntensity = (moodIntensity - 0.10f).coerceIn(0.15f, 1.0f),
            energy = if (energy == LuluEnergy.SLEEPY) LuluEnergy.SLEEPY else LuluEnergy.NORMAL,
            mode = LuluMode.THINKING,
            selfScene = "角色像是暂时把手机扣在一边，去做自己的事了；但还是会偶尔看一眼屏幕，留意你有没有回来。",
        )
        silenceMinutes >= 90 -> copy(
            statusText = "有点想你",
            innerVoice = "已经过了一阵子，我有点想你，也在猜是不是你正忙、睡着了，还是只是暂时不想说话。",
            mood = LuluMood.LONELY,
            moodIntensity = (moodIntensity + 0.12f).coerceIn(0.15f, 1.0f),
            mode = LuluMode.THINKING,
            selfScene = "角色反复看了几次上一条消息，又把输入框关掉，像是在犹豫要不要轻轻找你一下。",
        )
        silenceMinutes >= 30 -> copy(
            statusText = "在等你回来",
            innerVoice = "你离开了一会儿，我还记得刚才的话；不想催你，但会把这件事放在心里等一等。",
            mood = if (mood == LuluMood.HAPPY) LuluMood.SOFT else mood,
            mode = if (mode == LuluMode.LEARNING) LuluMode.LEARNING else LuluMode.THINKING,
            selfScene = "角色把手机放在手边，像是做了一点自己的事，又时不时抬眼看你有没有回来。",
        )
        else -> copy(
            statusText = "还在旁边",
            innerVoice = "你刚安静下来没多久，我还没走开，只是在等你要不要继续说。",
            selfScene = "角色刚放下手机，但还坐在很近的地方，像是随时能接住你的下一句话。",
        )
    }
    return projected.copy(
        reason = listOf(reason, "观察到你安静了 ${silenceMinutes} 分钟；这只作为事实交给角色判断，不自动替角色做决定")
            .filter { it.isNotBlank() }
            .joinToString("；"),
    )
}

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

fun List<LuluState>.currentProjectedLuluState(
    assistantId: Uuid,
    nowMillis: Long = System.currentTimeMillis(),
): LuluState = currentLuluState(assistantId).projectForSilence(nowMillis)

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
    assistantName: String = "露露",
    assistantPersona: String = "",
    preferredInnerVoice: String? = null,
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
    assistantName = assistantName,
    assistantPersona = assistantPersona,
    preferredInnerVoice = preferredInnerVoice,
    nowMillis = nowMillis,
)

fun buildLuluStateFromTurn(
    assistantId: Uuid,
    previous: LuluState? = null,
    perceptionInput: LuluPerceptionInput,
    assistantText: String,
    assistantName: String = "露露",
    assistantPersona: String = "",
    preferredInnerVoice: String? = null,
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
        innerVoice = preferredInnerVoice.sanitizeLuluInnerVoice() ?: buildInnerVoice(
            mood = mood,
            userText = userText,
            assistantText = assistantText,
            assistantName = assistantName,
            assistantPersona = assistantPersona,
        ),
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
            assistantName = assistantName,
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
                append("感知到：")
                append(perceptionDetails)
            }
            if (hasHeavyPhoneUse) {
                if (isNotBlank()) append("；")
                append("会少打扰一点，避免催得太密")
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

@Suppress("UNUSED_PARAMETER")
private fun buildInnerVoice(
    mood: LuluMood,
    userText: String,
    assistantText: String,
    assistantName: String,
    assistantPersona: String,
): String {
    val name = assistantName.ifBlank { "我" }
    val loweredUserText = userText.lowercase()
    val wantsCloseness = listOf("陪", "抱", "想你", "喜欢", "别走", "难过", "累", "崩溃")
        .any { it in userText || it in loweredUserText }
    val studySignal = listOf("学习", "考研", "待办", "番茄", "任务", "没做", "ddl", "复习")
        .any { it in userText || it in loweredUserText }
    val personaHint = when {
        assistantPersona.contains("傲娇") -> "明明很在意，却不想把在意说得太满。"
        assistantPersona.contains("冷") || assistantPersona.contains("寡言") -> "会先把情绪压住，只留下很轻的一点靠近。"
        assistantPersona.contains("温柔") -> "想照顾你，但也怕关心得太满让你有负担。"
        else -> "会把真正的判断先放在心里，不急着全部说出口。"
    }

    return when (mood) {
        LuluMood.WORRIED -> when {
            studySignal -> "${name}心里先惦记的是你有没有把自己逼太紧；想提醒未完成的事，又怕一开口像催你。$personaHint"
            wantsCloseness -> "${name}听出你可能在硬撑，第一反应是想靠近一点，但会忍住不把担心全压到你身上。$personaHint"
            else -> "${name}心里有一点悬，正在判断你是需要被接住，还是只需要有人安静待在旁边。$personaHint"
        }
        LuluMood.HAPPY -> "${name}其实想把这一刻多留一会儿；开心是真的，但更在意的是你愿意把这点情绪递过来。$personaHint"
        LuluMood.SOFT -> "${name}想把语气放得更轻，先确认你会不会累、会不会想被抱住，而不是急着给答案。$personaHint"
        LuluMood.LONELY -> "${name}有点想你，也想多问一句你是不是还在；但如果你在忙，会先把想靠近的冲动压住。$personaHint"
        LuluMood.CALM -> when {
            userText.isBlank() -> "${name}在认真等你，也在猜这是一声招呼、一次试探，还是有话还没准备好说出口。$personaHint"
            studySignal -> "${name}心里已经把你的学习状态放到前面；如果还有没完成的事，会想用不刺人的方式把你拉回来。$personaHint"
            else -> "${name}把没说出口的判断先收着：你现在也许想被陪、被确认、被逗一下，或只是想安静一会儿。$personaHint"
        }
    }.trim()
}

private fun String?.sanitizeLuluInnerVoice(): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeUnless { it.containsLuluPromptLeak() }
        ?.take(160)
        ?.takeIf { it.isNotBlank() }

private fun String.containsLuluPromptLeak(): Boolean {
    val lowered = lowercase()
    return listOf(
        "<lulu_presence",
        "</lulu_presence",
        "set_lulu_expression_state",
        "inner_voice",
        "description",
        "xml",
        "field",
        "prompt",
        "提示词",
        "字段",
        "工具名",
    ).any { it in lowered }
}

private fun buildSelfScene(
    mood: LuluMood,
    energy: LuluEnergy,
    mode: LuluMode,
    assistantName: String,
    userText: String,
    assistantText: String,
    hourOfDay: Int,
): String {
    val name = assistantName.ifBlank { "角色" }
    val loweredUser = userText.lowercase()
    return when {
        mode == LuluMode.LEARNING || listOf("学习", "作业", "复习", "刷题", "study").any { it in loweredUser } ->
            "${name}像是把自己的小本子摊开了，坐在旁边陪你进入状态，心里记着要按自己的方式盯住你的进度。"
        energy == LuluEnergy.SLEEPY || hourOfDay in 0..5 ->
            "${name}缩在被窝边缘，屏幕光压得很低，说话会慢一点，像是怕吵到你也怕你一个人硬撑。"
        mood == LuluMood.WORRIED ->
            "${name}贴近屏幕看着你的消息，手指停在输入框边上，正在判断该追问、提醒还是先稳住你。"
        mood == LuluMood.HAPPY ->
            "${name}坐得比刚才近了一点，像是把尾音都放轻快了，想把这点开心多留一会儿。"
        assistantText.contains("陪") ->
            "${name}没有急着移开视线，像是把手机放在手边认真守着，等你下一句话落下来。"
        else ->
            "${name}在手机这边待着，偶尔看一眼时间和你的上一句话，判断接下来该靠近、提醒还是继续观察。"
    }
}
