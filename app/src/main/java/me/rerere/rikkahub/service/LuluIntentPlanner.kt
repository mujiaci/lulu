package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluState

data class LuluIntentInput(
    val assistantName: String,
    val state: LuluState,
    val userText: String,
    val assistantText: String,
    val minutesSinceLastChat: Long,
    val pendingThoughts: List<String> = emptyList(),
    val availableToolNames: Set<String> = emptySet(),
)

data class LuluIntentPlan(
    val intent: LuluIntent,
    val shouldMessageNow: Boolean,
    val delayMinutes: Int?,
    val toolNames: List<String>,
    val reason: String,
    val tone: String,
    val followUps: List<LuluFollowUpPlan> = emptyList(),
)

enum class LuluIntent {
    CARE_REMINDER,
    STAY_NEAR,
    REACH_OUT,
    CHECK_CONTEXT,
    DO_NOT_DISTURB,
}

object LuluIntentPlanner {
    fun plan(input: LuluIntentInput): LuluIntentPlan {
        val text = "${input.userText}\n${input.assistantText}\n${input.pendingThoughts.joinToString("\n")}".lowercase()
        val explicitDelay = findExplicitRelativeMinutes(text)
        val intent = chooseIntent(input, text, explicitDelay)
        val delayMinutes = when (intent) {
            LuluIntent.CARE_REMINDER -> explicitDelay ?: when {
                text.hasAny("睡", "困", "晚安") -> 30
                text.hasAny("吃饭", "没吃", "晚饭", "午饭", "早饭", "外卖") -> 20
                else -> 30
            }
            LuluIntent.STAY_NEAR -> explicitDelay ?: 45
            LuluIntent.REACH_OUT -> null
            LuluIntent.CHECK_CONTEXT -> 10
            LuluIntent.DO_NOT_DISTURB -> null
        }
        return LuluIntentPlan(
            intent = intent,
            shouldMessageNow = intent == LuluIntent.REACH_OUT,
            delayMinutes = delayMinutes,
            toolNames = chooseTools(input.availableToolNames, intent, text),
            reason = buildReason(input, text, intent),
            tone = chooseTone(input.state, intent),
        )
    }

    private fun chooseIntent(
        input: LuluIntentInput,
        text: String,
        explicitDelay: Int?,
    ): LuluIntent = when {
        explicitDelay != null || text.hasAny("提醒我", "叫我", "催我", "晚安", "睡", "吃饭", "没吃") ->
            LuluIntent.CARE_REMINDER
        text.hasAny("写作业", "学习", "复习", "刷题", "背书", "先不聊", "去学") ->
            LuluIntent.STAY_NEAR
        input.minutesSinceLastChat >= 120 && input.state.energy.canReachOut() ->
            LuluIntent.REACH_OUT
        input.minutesSinceLastChat >= 45 && input.pendingThoughts.isNotEmpty() ->
            LuluIntent.CHECK_CONTEXT
        else -> LuluIntent.DO_NOT_DISTURB
    }

    private fun chooseTools(
        availableToolNames: Set<String>,
        intent: LuluIntent,
        text: String,
    ): List<String> {
        val wanted = buildList {
            when (intent) {
                LuluIntent.CARE_REMINDER -> {
                    if (text.hasAny("睡", "困", "晚安")) add("get_gadgetbridge_data")
                    if (text.hasAny("吃饭", "没吃", "外卖")) {
                        add("get_location")
                        add("explore_nearby")
                    }
                    add("get_app_usage")
                    add("get_battery_info")
                }
                LuluIntent.STAY_NEAR -> {
                    add("get_app_usage")
                    add("control_music")
                    add("get_battery_info")
                }
                LuluIntent.REACH_OUT -> {
                    add("get_battery_info")
                    add("get_app_usage")
                }
                LuluIntent.CHECK_CONTEXT -> {
                    add("get_app_usage")
                    add("get_battery_info")
                    add("camera_capture")
                }
                LuluIntent.DO_NOT_DISTURB -> Unit
            }
        }
        return wanted.distinct().filter { it in availableToolNames }.take(5)
    }

    private fun buildReason(input: LuluIntentInput, text: String, intent: LuluIntent): String = when (intent) {
        LuluIntent.CARE_REMINDER -> "露露根据刚才的对话想继续照看用户：${input.userText.take(40)}"
        LuluIntent.STAY_NEAR -> "用户刚才像是要去学习/写作业，露露想晚点轻轻确认状态：${input.userText.take(40)}"
        LuluIntent.REACH_OUT -> "已经很久没有聊天了，露露状态还可以，想自然地找用户说句话。"
        LuluIntent.CHECK_CONTEXT -> "露露心里还有没完成的想法，想先看看用户状态再决定怎么开口。"
        LuluIntent.DO_NOT_DISTURB -> "现在没有足够自然的主动理由，先不打扰。"
    }

    private fun chooseTone(state: LuluState, intent: LuluIntent): String = when {
        intent == LuluIntent.DO_NOT_DISTURB -> "安静"
        state.energy == LuluEnergy.SLEEPY -> "轻、短、困倦一点"
        intent == LuluIntent.REACH_OUT -> "自然、想念、不要像提醒"
        intent == LuluIntent.STAY_NEAR -> "轻轻确认，不打断"
        else -> "温柔、具体、像顺手照看"
    }

    private fun findExplicitRelativeMinutes(text: String): Int? {
        val match = Regex("""([一二两三四五六七八九十\d]+)\s*(分钟|分|小时|个小时)\s*后""").find(text)
            ?: return null
        val amount = parseChineseNumber(match.groupValues[1]) ?: return null
        return if (match.groupValues[2].contains("小时")) amount * 60 else amount
    }

    private fun parseChineseNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val normalized = raw.replace("两", "二").replace("〇", "零")
        val digitMap = mapOf(
            '零' to 0,
            '一' to 1,
            '二' to 2,
            '三' to 3,
            '四' to 4,
            '五' to 5,
            '六' to 6,
            '七' to 7,
            '八' to 8,
            '九' to 9,
        )
        if ('十' in normalized) {
            val parts = normalized.split("十", limit = 2)
            val tens = parts.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.singleOrNull()
                ?.let { digitMap[it] }
                ?: 1
            val ones = parts.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.singleOrNull()
                ?.let { digitMap[it] }
                ?: 0
            return tens * 10 + ones
        }
        return normalized.mapNotNull { digitMap[it] }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("")
            ?.toIntOrNull()
    }

    private fun String.hasAny(vararg words: String): Boolean =
        words.any { it in this }

    private fun LuluEnergy.canReachOut(): Boolean = this == LuluEnergy.NORMAL || this == LuluEnergy.HIGH
}
