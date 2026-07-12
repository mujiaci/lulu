package me.rerere.rikkahub.data.companion

internal fun CompanionState.sanitizedCompanionState(): CompanionState = copy(
    statusText = statusText.cleanTechnicalCompanionStateText(fallback = "安静留意"),
    innerThought = innerThought.cleanTechnicalCompanionStateText(fallback = ""),
    mindState = mindState.cleanTechnicalCompanionStateText(fallback = "安静留意着现在的变化"),
    selfScene = selfScene.cleanTechnicalCompanionStateText(
        fallback = "暂时没有开口，只把注意力留在接下来的变化上。",
    ),
)

internal fun String.cleanTechnicalCompanionStateText(fallback: String): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return ""
    return if (normalized.isTechnicalCompanionStateText()) fallback else normalized
}

internal fun String.isTechnicalCompanionStateText(): Boolean {
    val lower = trim().replace(Regex("\\s+"), " ").lowercase()
    return lower.isNotBlank() && TECHNICAL_STATE_MARKERS.any { marker -> marker in lower }
}

private val TECHNICAL_STATE_MARKERS = listOf(
    "后台判断",
    "后台判定",
    "副 api",
    "副api",
    "api 判断",
    "api判断",
    "本地规划",
    "规划兜底",
    "模型兜底",
    "状态栏留下",
    "planner",
    "fallback",
    "local planning",
    "sub api",
)
