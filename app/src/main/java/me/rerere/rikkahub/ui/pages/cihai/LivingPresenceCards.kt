package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.service.LivingIntent
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.LivingIntentStatus
import java.util.concurrent.TimeUnit

data class LivingIntentCardModel(
    val id: String,
    val kind: LivingIntentKind,
    val title: String,
    val nextEvaluateText: String,
    val statusText: String,
    val stateLine: String,
    val appraisalLine: String,
    val hypothesesLine: String,
    val cadenceLine: String,
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
        .sortedBy { it.nextEvaluateAt }
        .take(8)
        .map { intent -> intent.toCardModel(nowMillis) }

private fun LivingIntent.toCardModel(nowMillis: Long): LivingIntentCardModel {
    val minutesUntil = ((nextEvaluateAt - nowMillis) / TimeUnit.MINUTES.toMillis(1)).coerceAtLeast(0L)
    return LivingIntentCardModel(
        id = id,
        kind = kind,
        title = kind.title(),
        nextEvaluateText = if (nextEvaluateAt <= nowMillis) {
            "现在该重新判断"
        } else {
            "下次判断：${minutesUntil.coerceAtLeast(1)} 分钟后"
        },
        statusText = when (status) {
            LivingIntentStatus.ACTIVE -> "挂在心里"
            LivingIntentStatus.RESTRAINED -> "克制观察"
            LivingIntentStatus.COMPLETED -> "已完成"
            LivingIntentStatus.CANCELLED -> "已取消"
        },
        stateLine = "信念：$belief\n长期动机：${traitMotive.ifBlank { motive }}\n情境动机：${situationalMotive.ifBlank { motive }}\n意图：$intention",
        appraisalLine = "意义评估：${appraisal.meaning}\n风险：${appraisal.risk}\n成本/资源：${appraisal.cost} / ${appraisal.resources}",
        hypothesesLine = "猜测：${hypotheses.joinToString(" / ")}",
        cadenceLine = "下次审议：${evaluationCadence.delaysMinutes.joinToString("/")} 分钟 · ${evaluationCadence.reason}",
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
    LivingIntentKind.HEALTH_SAFETY -> "身体安全挂心事"
    LivingIntentKind.ORDINARY_SILENCE -> "沉默回复预期"
    LivingIntentKind.STUDY_FOCUS -> "学习专注守护"
    LivingIntentKind.DEADLINE -> "DDL 进度照看"
    LivingIntentKind.WAKE_UP -> "起床唤醒计划"
}
