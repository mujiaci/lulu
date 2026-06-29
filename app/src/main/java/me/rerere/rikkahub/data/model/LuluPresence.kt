package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid

private const val LULU_THOUGHT_LIMIT_PER_ASSISTANT = 3
private const val LULU_THOUGHT_TTL_MILLIS = 24L * 60L * 60L * 1000L

@Serializable
data class LuluThought(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val content: String,
    val source: LuluThoughtSource = LuluThoughtSource.CHAT,
    val category: LuluThoughtCategory = LuluThoughtCategory.SHORT_TERM,
    val importance: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + LULU_THOUGHT_TTL_MILLIS,
    val expressed: Boolean = false,
    val canSurface: Boolean = true,
)

@Serializable
enum class LuluThoughtCategory(val label: String) {
    @SerialName("short_term")
    SHORT_TERM("当前想法"),

    @SerialName("concern")
    CONCERN("心事"),

    @SerialName("pending_action")
    PENDING_ACTION("未完成动作"),
}

@Serializable
enum class LuluThoughtSource {
    @SerialName("chat")
    CHAT,

    @SerialName("memory")
    MEMORY,

    @SerialName("state")
    STATE,
}

@Serializable
data class LuluPerception(
    val timeLabel: LuluTimeLabel,
    val sceneLabel: LuluSceneLabel,
    val userSignals: Set<LuluUserSignal> = emptySet(),
    val summary: String,
)

data class LuluPerceptionInput(
    val userText: String,
    val hourOfDay: Int = LocalDateTime.now().hour,
    val deviceState: LuluDeviceState? = null,
    val healthState: LuluHealthState? = null,
    val appUsageState: LuluAppUsageState? = null,
    val locationState: LuluLocationState? = null,
    val musicState: LuluMusicState? = null,
    val actionCues: Set<LuluActionCue> = emptySet(),
)

data class LuluDeviceState(
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val networkType: String? = null,
)

data class LuluHealthState(
    val sleepMinutes: Int? = null,
    val heartRate: Int? = null,
    val stepCount: Int? = null,
)

data class LuluAppUsageState(
    val topApps: List<String> = emptyList(),
    val screenMinutesToday: Int? = null,
)

data class LuluLocationState(
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class LuluMusicState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean? = null,
    val appName: String? = null,
)

@Serializable
enum class LuluActionCue(val label: String) {
    @SerialName("location_context")
    LOCATION_CONTEXT("位置线索"),

    @SerialName("nearby_context")
    NEARBY_CONTEXT("周边探索"),

    @SerialName("message_context")
    MESSAGE_CONTEXT("消息线索"),

    @SerialName("alarm_candidate")
    ALARM_CANDIDATE("可提醒"),

    @SerialName("calendar_candidate")
    CALENDAR_CANDIDATE("可写日程"),

    @SerialName("music_context")
    MUSIC_CONTEXT("音乐状态"),

    @SerialName("camera_candidate")
    CAMERA_CANDIDATE("可看环境"),

    @SerialName("journal_candidate")
    JOURNAL_CANDIDATE("可写日志"),
}

@Serializable
enum class LuluTimeLabel(val label: String) {
    @SerialName("late_night")
    LATE_NIGHT("深夜"),

    @SerialName("morning")
    MORNING("清晨"),

    @SerialName("daytime")
    DAYTIME("白天"),

    @SerialName("evening")
    EVENING("傍晚"),
}

@Serializable
enum class LuluSceneLabel(val label: String) {
    @SerialName("chatting")
    CHATTING("陪聊"),

    @SerialName("studying")
    STUDYING("学习中"),

    @SerialName("resting")
    RESTING("休息中"),
}

@Serializable
enum class LuluUserSignal(val label: String) {
    @SerialName("tired")
    TIRED("疲惫"),

    @SerialName("happy")
    HAPPY("开心"),

    @SerialName("sad")
    SAD("低落"),

    @SerialName("studying")
    STUDYING("学习"),

    @SerialName("low_battery")
    LOW_BATTERY("电量低"),

    @SerialName("sleep_debt")
    SLEEP_DEBT("睡眠偏少"),

    @SerialName("elevated_heart_rate")
    ELEVATED_HEART_RATE("心率偏高"),

    @SerialName("heavy_phone_use")
    HEAVY_PHONE_USE("屏幕时间偏长"),

    @SerialName("location_context")
    LOCATION_CONTEXT("有位置线索"),

    @SerialName("message_context")
    MESSAGE_CONTEXT("有消息线索"),

    @SerialName("music_context")
    MUSIC_CONTEXT("有音乐线索"),
}

@Serializable
data class LuluExpressionPlan(
    val length: LuluExpressionLength,
    val typingDelayMillis: Long,
    val bubbleCountHint: Int,
    val guidance: String,
)

@Serializable
enum class LuluExpressionLength(val label: String) {
    @SerialName("short")
    SHORT("短句"),

    @SerialName("normal")
    NORMAL("正常"),

    @SerialName("warm")
    WARM("柔和"),
}

fun List<LuluThought>.thoughtHistory(assistantId: Uuid, nowMillis: Long = System.currentTimeMillis()): List<LuluThought> =
    filter { thought ->
        thought.assistantId == assistantId &&
            thought.canSurface &&
            !thought.expressed &&
            (thought.expiresAt > nowMillis || thought.category == LuluThoughtCategory.CONCERN)
    }
        .sortedWith(compareByDescending<LuluThought> { it.importance }.thenByDescending { it.createdAt })
        .take(LULU_THOUGHT_LIMIT_PER_ASSISTANT)

fun List<LuluThought>.normalizedLuluThoughts(
    validAssistantIds: Set<Uuid>,
    nowMillis: Long = System.currentTimeMillis(),
): List<LuluThought> =
    filter { thought ->
        thought.assistantId in validAssistantIds &&
            thought.content.isNotBlank() &&
            (thought.expiresAt > nowMillis || thought.category == LuluThoughtCategory.CONCERN)
    }
        .groupBy { it.assistantId }
        .values
        .flatMap { thoughts ->
            thoughts.sortedWith(
                compareByDescending<LuluThought> { it.importance }
                    .thenByDescending { it.createdAt }
            ).take(LULU_THOUGHT_LIMIT_PER_ASSISTANT)
        }

fun List<LuluThought>.appendLuluThoughts(
    thoughts: List<LuluThought>,
    validAssistantIds: Set<Uuid>,
    nowMillis: Long = System.currentTimeMillis(),
): List<LuluThought> =
    (this + thoughts).normalizedLuluThoughts(validAssistantIds, nowMillis)

fun List<LuluThought>.markResolvedLuluThoughts(
    assistantId: Uuid,
    userText: String,
    nowMillis: Long = System.currentTimeMillis(),
): List<LuluThought> {
    if (!userText.hasPendingActionResolutionSignal()) return this
    return map { thought ->
        if (
            thought.assistantId == assistantId &&
            thought.category == LuluThoughtCategory.PENDING_ACTION &&
            !thought.expressed
        ) {
            thought.copy(expressed = true, expiresAt = nowMillis)
        } else {
            thought
        }
    }
}

fun buildLuluPerception(
    userText: String,
    hourOfDay: Int = LocalDateTime.now().hour,
): LuluPerception = buildLuluPerception(
    LuluPerceptionInput(
        userText = userText,
        hourOfDay = hourOfDay,
    )
)

fun buildLuluPerception(input: LuluPerceptionInput): LuluPerception {
    val userText = input.userText
    val hourOfDay = input.hourOfDay
    val lowered = userText.lowercase()
    val signals = mutableSetOf<LuluUserSignal>()
    if (listOf("累", "困", "睡", "tired", "sleepy").any { it in lowered }) signals += LuluUserSignal.TIRED
    if (listOf("开心", "哈哈", "喜欢", "happy").any { it in lowered }) signals += LuluUserSignal.HAPPY
    if (listOf("难过", "烦", "崩溃", "哭", "sad").any { it in lowered }) signals += LuluUserSignal.SAD
    if (listOf("学习", "作业", "考试", "study").any { it in lowered }) signals += LuluUserSignal.STUDYING
    if (input.deviceState?.isLowBattery() == true) signals += LuluUserSignal.LOW_BATTERY
    if (input.healthState?.hasSleepDebt() == true) signals += LuluUserSignal.SLEEP_DEBT
    if (input.healthState?.hasElevatedHeartRate() == true) signals += LuluUserSignal.ELEVATED_HEART_RATE
    if (input.appUsageState?.hasHeavyPhoneUse() == true) signals += LuluUserSignal.HEAVY_PHONE_USE
    if (input.locationState != null) signals += LuluUserSignal.LOCATION_CONTEXT
    if (input.musicState != null) signals += LuluUserSignal.MUSIC_CONTEXT
    if (input.actionCues.contains(LuluActionCue.MESSAGE_CONTEXT)) signals += LuluUserSignal.MESSAGE_CONTEXT
    val timeLabel = when (hourOfDay) {
        in 0..5 -> LuluTimeLabel.LATE_NIGHT
        in 6..10 -> LuluTimeLabel.MORNING
        in 18..23 -> LuluTimeLabel.EVENING
        else -> LuluTimeLabel.DAYTIME
    }
    val sceneLabel = when {
        LuluUserSignal.STUDYING in signals -> LuluSceneLabel.STUDYING
        timeLabel == LuluTimeLabel.LATE_NIGHT || LuluUserSignal.TIRED in signals -> LuluSceneLabel.RESTING
        else -> LuluSceneLabel.CHATTING
    }
    return LuluPerception(
        timeLabel = timeLabel,
        sceneLabel = sceneLabel,
        userSignals = signals,
        summary = buildString {
            append(timeLabel.label)
            append(" / ")
            append(sceneLabel.label)
            if (signals.isNotEmpty()) {
                append(" / 用户信号：")
                append(signals.joinToString("、") { it.label })
            }
            input.deviceState?.summaryText()?.let { append(" / $it") }
            input.healthState?.summaryText()?.let { append(" / $it") }
            input.appUsageState?.summaryText()?.let { append(" / $it") }
            input.locationState?.summaryText()?.let { append(" / $it") }
            input.musicState?.summaryText()?.let { append(" / $it") }
            input.actionCues.summaryText()?.let { append(" / $it") }
        },
    )
}

private fun LuluDeviceState.isLowBattery(): Boolean =
    batteryPercent != null && batteryPercent <= 15 && isCharging != true

private fun LuluDeviceState.summaryText(): String? = buildList {
    batteryPercent?.let { battery ->
        add(if (isLowBattery()) "电量低：$battery%" else "电量：$battery%")
    }
    networkType?.takeIf { it.isNotBlank() }?.let { network -> add("网络：$network") }
}.joinToString("，").takeIf { it.isNotBlank() }

private fun LuluHealthState.hasSleepDebt(): Boolean =
    sleepMinutes != null && sleepMinutes < 6 * 60

private fun LuluHealthState.hasElevatedHeartRate(): Boolean =
    heartRate != null && heartRate >= 95

private fun LuluHealthState.summaryText(): String? = buildList {
    sleepMinutes?.let { minutes ->
        val hours = minutes / 60
        val restMinutes = minutes % 60
        add(if (hasSleepDebt()) "睡眠偏少：${hours}小时${restMinutes}分" else "睡眠：${hours}小时${restMinutes}分")
    }
    heartRate?.let { rate ->
        add(if (hasElevatedHeartRate()) "心率偏高：$rate" else "心率：$rate")
    }
    stepCount?.let { steps -> add("步数：$steps") }
}.joinToString("，").takeIf { it.isNotBlank() }

private fun LuluAppUsageState.hasHeavyPhoneUse(): Boolean =
    screenMinutesToday != null && screenMinutesToday >= 6 * 60

private fun LuluAppUsageState.summaryText(): String? = buildList {
    screenMinutesToday?.let { minutes ->
        val hours = minutes / 60
        val restMinutes = minutes % 60
        add(if (hasHeavyPhoneUse()) "屏幕时间偏长：${hours}小时${restMinutes}分" else "屏幕时间：${hours}小时${restMinutes}分")
    }
    topApps.take(3).takeIf { it.isNotEmpty() }?.let { apps -> add("常用：${apps.joinToString("、")}") }
}.joinToString("，").takeIf { it.isNotBlank() }

private fun LuluLocationState.summaryText(): String? = buildList {
    address?.takeIf { it.isNotBlank() }?.let { add("位置：$it") }
    if (address.isNullOrBlank() && latitude != null && longitude != null) {
        add("坐标：$latitude,$longitude")
    }
}.joinToString("，").takeIf { it.isNotBlank() }

private fun LuluMusicState.summaryText(): String? = buildList {
    val song = buildString {
        title?.takeIf { it.isNotBlank() }?.let { append(it) }
        artist?.takeIf { it.isNotBlank() }?.let {
            if (isNotBlank()) append(" - ")
            append(it)
        }
    }
    if (song.isNotBlank()) {
        add(if (isPlaying == true) "正在听：$song" else "音乐：$song")
    }
    appName?.takeIf { it.isNotBlank() }?.let { add("音乐应用：$it") }
}.joinToString("，").takeIf { it.isNotBlank() }

private fun Set<LuluActionCue>.summaryText(): String? =
    takeIf { it.isNotEmpty() }
        ?.joinToString("、") { it.label }
        ?.let { "动作线索：$it" }

fun buildLuluExpressionPlan(
    state: LuluState,
    reply: String,
): LuluExpressionPlan {
    val sleepy = state.energy == LuluEnergy.SLEEPY || state.energy == LuluEnergy.LOW
    val length = when {
        sleepy -> LuluExpressionLength.SHORT
        state.mood == LuluMood.SOFT || state.mood == LuluMood.WORRIED -> LuluExpressionLength.WARM
        else -> LuluExpressionLength.NORMAL
    }
    val bubbleCount = when {
        reply.length > 120 -> 3
        reply.length > 48 -> 2
        else -> 1
    }
    return LuluExpressionPlan(
        length = length,
        typingDelayMillis = ((reply.length * if (sleepy) 45L else 28L) + 500L).coerceIn(600L, 4_000L),
        bubbleCountHint = bubbleCount,
        guidance = when (length) {
            LuluExpressionLength.SHORT -> "露露现在精力偏低，优先用短句、轻声、少解释，不要硬撑长回复。"
            LuluExpressionLength.WARM -> "露露现在更柔软，回复可以慢一点、贴近一点，但不要直接复述状态。"
            LuluExpressionLength.NORMAL -> "自然表达即可；必要时按语义拆成 $bubbleCount 个气泡。"
        },
    )
}

private fun String.hasPendingActionResolutionSignal(): Boolean {
    val lowered = lowercase()
    return listOf(
        "我回来了",
        "回来了",
        "我来了",
        "下课了",
        "学完了",
        "写完了",
        "做完了",
        "忙完了",
        "回来啦",
        "back",
        "finished",
        "done",
    ).any { signal -> signal in lowered }
}

fun buildLuluThoughtFromTurn(
    assistantId: Uuid,
    userText: String,
    state: LuluState,
    nowMillis: Long = System.currentTimeMillis(),
): LuluThought? {
    val perception = buildLuluPerception(userText)
    val hasReturnPromise = listOf("等下回来", "一会儿回来", "待会回来", "回来再说").any { it in userText }
    val hasStudy = LuluUserSignal.STUDYING in perception.userSignals
    val category = when {
        hasReturnPromise || hasStudy -> LuluThoughtCategory.PENDING_ACTION
        LuluUserSignal.SAD in perception.userSignals -> LuluThoughtCategory.CONCERN
        else -> LuluThoughtCategory.SHORT_TERM
    }
    val content = when {
        hasReturnPromise && hasStudy -> "他去学习了，我想等他回来时轻轻接一下。"
        hasReturnPromise -> "他说等下回来，我想记得等他回来。"
        hasStudy -> "他现在要学习，我先别打扰太多。"
        LuluUserSignal.TIRED in perception.userSignals -> "他现在听起来很累，我想先让他缓一缓。"
        LuluUserSignal.SAD in perception.userSignals -> "他好像不太舒服，我想多留意一点。"
        state.mood == LuluMood.HAPPY -> "刚才的气氛很好，我想把这种轻快记住。"
        else -> return null
    }
    return LuluThought(
        assistantId = assistantId,
        content = content,
        category = category,
        importance = when (category) {
            LuluThoughtCategory.CONCERN -> 5
            LuluThoughtCategory.PENDING_ACTION -> 4
            LuluThoughtCategory.SHORT_TERM -> 3
        },
        createdAt = nowMillis,
        expiresAt = if (category == LuluThoughtCategory.CONCERN) {
            nowMillis + LULU_THOUGHT_TTL_MILLIS * 7
        } else {
            nowMillis + LULU_THOUGHT_TTL_MILLIS
        },
    )
}
