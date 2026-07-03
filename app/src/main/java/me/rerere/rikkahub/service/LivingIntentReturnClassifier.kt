package me.rerere.rikkahub.service

object LivingIntentReturnClassifier {
    fun shouldCompleteOnUserReturn(
        intent: LivingIntent,
        userText: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val text = userText.lowercase()
        val hasBeenActivelyHeld = intent.spokenCount > 0 ||
            intent.silentEvaluationCount > 0 ||
            intent.lastEvaluatedAt != null
        return when (intent.kind) {
            LivingIntentKind.ORDINARY_SILENCE -> hasBeenActivelyHeld
            LivingIntentKind.HEALTH_SAFETY -> hasBeenActivelyHeld && text.containsAny(
                "没事",
                "好多了",
                "不疼",
                "不痛",
                "吃药",
                "回来了",
                "还好",
                "好了",
            )
            LivingIntentKind.STUDY_FOCUS -> hasBeenActivelyHeld && text.containsAny(
                "学完",
                "完成",
                "做完",
                "休息",
                "回来了",
                "结束",
                "忙完",
            )
            LivingIntentKind.DEADLINE -> text.containsAny(
                "完成",
                "做完",
                "交了",
                "提交",
                "搞定",
                "弄完",
                "忙完",
            ) || intent.deadlineAtMillis?.let { nowMillis >= it && hasBeenActivelyHeld } == true
            LivingIntentKind.WAKE_UP -> text.containsAny(
                "醒了",
                "起了",
                "起来",
                "起床了",
                "回来了",
            ) || intent.targetAtMillis?.let { nowMillis >= it + 25 * MINUTE_MILLIS && hasBeenActivelyHeld } == true
        }
    }

    fun completeReason(intent: LivingIntent, userText: String, nowMillis: Long = System.currentTimeMillis()): String =
        when (intent.kind) {
            LivingIntentKind.ORDINARY_SILENCE -> "用户重新回来发消息，沉默回复预期结束。"
            LivingIntentKind.HEALTH_SAFETY -> "用户反馈了身体状态：${userText.take(80)}"
            LivingIntentKind.STUDY_FOCUS -> "用户反馈了学习/休息状态：${userText.take(80)}"
            LivingIntentKind.DEADLINE -> if (
                intent.deadlineAtMillis != null &&
                nowMillis >= intent.deadlineAtMillis &&
                !userText.containsAny("完成", "做完", "交了", "提交", "搞定", "弄完", "忙完")
            ) {
                "截止时间已过且用户重新回来，归档这轮 DDL 挂心事：${userText.take(80)}"
            } else {
                "用户反馈任务完成或提交：${userText.take(80)}"
            }
            LivingIntentKind.WAKE_UP -> "用户反馈已经醒来、起床或在目标时间后回来：${userText.take(80)}"
        }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { contains(it) }

    private const val MINUTE_MILLIS = 60_000L
}
