package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.service.LivingIntent
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.LivingIntentStatus
import java.util.concurrent.TimeUnit

data class LivingIntentCardModel(
    val id: String,
    val kind: LivingIntentKind,
    val title: String,
    val nextPerceptionText: String,
    val statusText: String,
    val eventLine: String,
    val goalLine: String,
    val stateLine: String,
    val appraisalLine: String,
    val hypothesesLine: String,
    val perceptionLine: String,
    val countLine: String,
    val emotionLine: String,
    val consolidationLine: String,
    val capabilityLine: String?,
)

fun buildLivingIntentCards(
    intents: List<LivingIntent>,
    selectedAssistantId: String,
    nowMillis: Long = System.currentTimeMillis(),
): List<LivingIntentCardModel> =
    intents
        .filter { intent ->
            intent.status != LivingIntentStatus.COMPLETED &&
                intent.status != LivingIntentStatus.CANCELLED &&
                (intent.assistantId.isBlank() || intent.assistantId == selectedAssistantId)
        }
        .sortedBy { it.nextPerceptionAt }
        .take(8)
        .map { intent -> intent.toCardModel(nowMillis) }

private fun LivingIntent.toCardModel(nowMillis: Long): LivingIntentCardModel {
    val minutesUntil = ((nextPerceptionAt - nowMillis) / TimeUnit.MINUTES.toMillis(1)).coerceAtLeast(0L)
    return LivingIntentCardModel(
        id = id,
        kind = kind,
        title = kind.title(),
        nextPerceptionText = if (nextPerceptionAt <= nowMillis) {
            "现在该重新感知"
        } else {
            "下次感知：${minutesUntil.coerceAtLeast(1)} 分钟后"
        },
        statusText = when (status) {
            LivingIntentStatus.ACTIVE -> "挂心中"
            LivingIntentStatus.RESTRAINED -> "克制观察"
            LivingIntentStatus.COMPLETED -> "已完成"
            LivingIntentStatus.CANCELLED -> "已取消"
        },
        eventLine = "事件：$concernEvent",
        goalLine = "目标：$concernGoal",
        stateLine = "本轮判断：$intention",
        appraisalLine = "意义：${appraisal.meaning}\n风险：${appraisal.risk}\n资源：${appraisal.resources}",
        hypothesesLine = "可能情况：${hypotheses.joinToString(" / ")}",
        perceptionLine = "下次感知由本轮判断动态决定；到点后重新从感知层开始。",
        countLine = "默默判断 $silentEvaluationCount 次 · 开口 $spokenCount 次 · 克制 $restraint/10",
        emotionLine = "情绪：${emotion.emotionLabel} · ${emotion.feltSense}\n冲动：${emotion.impulse}\n克制：${emotion.restraintText}",
        consolidationLine = "沉淀：${consolidation.episodicTrace}\n策略：${consolidation.policyLearning}",
        capabilityLine = capabilityRequests
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { request ->
                "能力请求：${request.capability} · ${request.reason}"
            },
    )
}

private fun LivingIntentKind.title(): String = when (this) {
    LivingIntentKind.HEALTH_SAFETY -> "身体安全挂心"
    LivingIntentKind.ORDINARY_SILENCE -> "沉默回复挂心"
    LivingIntentKind.STUDY_FOCUS -> "学习节奏挂心"
    LivingIntentKind.DEADLINE -> "任务节点挂心"
    LivingIntentKind.WAKE_UP -> "起床时间挂心"
}
