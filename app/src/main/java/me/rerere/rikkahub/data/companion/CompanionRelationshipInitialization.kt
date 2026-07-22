package me.rerere.rikkahub.data.companion

/** Initializes declared relationship facts once, without inventing shared history. */
internal fun initializeCompanionRelationshipFromCharacterCard(
    current: CompanionRelationshipState,
    characterCard: String,
    nowMillis: Long,
): CompanionRelationshipState {
    if (characterCard.isBlank() || current.initializationEvidence.isNotBlank()) return current
    val declaration = characterCard.relationshipDeclaration() ?: return current
    val hasInteractionEvidence = current.updatedAt > 0L
    return current.copy(
        roleLabel = current.roleLabel.ifBlank { declaration.label },
        knownDuration = characterCard.fieldValue("认识时长", "相识时长", "认识时间", "known for"),
        sharedExperiences = characterCard.fieldItems("共同经历", "共同记忆", "shared experiences"),
        stage = characterCard.fieldValue("关系阶段", "当前阶段", "stage").ifBlank { declaration.label },
        securityContext = characterCard.fieldValue("安全感", "信任基础", "security"),
        attachmentExpression = characterCard.fieldValue("依恋表达", "情感表达", "表达方式", "attachment"),
        interactionPatterns = characterCard.fieldItems("互动习惯", "相处方式", "互动方式", "interaction"),
        declaredBoundaries = characterCard.fieldItems("边界", "禁忌", "底线", "boundaries"),
        potentialTensions = characterCard.fieldItems("潜在矛盾", "未解矛盾", "冲突点", "tensions"),
        initializationEvidence = "character_card",
        lastChangeReason = if (hasInteractionEvidence) current.lastChangeReason else "由完整角色卡初始化",
        lastChangeConfidence = if (hasInteractionEvidence) current.lastChangeConfidence else 1f,
        lastEvidenceIds = if (hasInteractionEvidence) current.lastEvidenceIds else listOf("character_card"),
        trust = if (hasInteractionEvidence) current.trust else declaration.trust,
        closeness = if (hasInteractionEvidence) current.closeness else declaration.closeness,
        reliability = if (hasInteractionEvidence) current.reliability else declaration.reliability,
        boundaryConfidence = if (hasInteractionEvidence) current.boundaryConfidence else declaration.boundaryConfidence,
        unresolvedTension = if (hasInteractionEvidence) current.unresolvedTension else declaration.tension,
        updatedAt = maxOf(current.updatedAt, nowMillis),
    )
}

private data class RelationshipDeclaration(
    val label: String,
    val trust: Float,
    val closeness: Float,
    val reliability: Float,
    val boundaryConfidence: Float,
    val tension: Float,
)

private fun String.fieldValue(vararg labels: String): String {
    val segments = split(Regex("[\\n；;。]"))
    labels.forEach { label ->
        segments.firstOrNull { it.trim().startsWith(label, ignoreCase = true) }
            ?.substringAfterAny(':', '：')
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it.take(240) }
    }
    return ""
}

private fun String.fieldItems(vararg labels: String): List<String> =
    fieldValue(*labels).split(Regex("[、,，/|]"))
        .map(String::trim).filter(String::isNotBlank).distinct().take(12)

private fun String.substringAfterAny(vararg delimiters: Char): String {
    val index = delimiters.map(::indexOf).filter { it >= 0 }.minOrNull() ?: return ""
    return substring(index + 1)
}

private fun String.relationshipDeclaration(): RelationshipDeclaration? {
    val normalized = lowercase().replace(Regex("\\s+"), "")
    return RELATIONSHIP_DECLARATIONS.firstNotNullOfOrNull { (markers, declaration) ->
        markers.firstOrNull { marker -> marker in normalized && !normalized.isNegatedNear(marker) }?.let { declaration }
    }
}

private fun String.isNegatedNear(marker: String): Boolean {
    val index = indexOf(marker)
    if (index < 0) return false
    val prefix = substring(maxOf(0, index - 4), index)
    return NEGATION_MARKERS.any(prefix::endsWith)
}

private val NEGATION_MARKERS = listOf("不是", "并非", "不算", "非", "not")
private val RELATIONSHIP_DECLARATIONS = listOf(
    listOf("夫妻", "配偶", "丈夫", "妻子", "husband", "wife", "spouse") to
        RelationshipDeclaration("伴侣", 0.70f, 0.75f, 0.60f, 0.55f, 0f),
    listOf("恋人", "男朋友", "女朋友", "情侣", "boyfriend", "girlfriend", "lover") to
        RelationshipDeclaration("恋人", 0.65f, 0.70f, 0.55f, 0.50f, 0f),
    listOf("家人", "亲人", "兄妹", "姐弟", "姐妹", "兄弟", "family") to
        RelationshipDeclaration("家人", 0.65f, 0.60f, 0.60f, 0.55f, 0f),
    listOf("挚友", "至交", "最好的朋友", "bestfriend") to
        RelationshipDeclaration("挚友", 0.62f, 0.58f, 0.55f, 0.55f, 0f),
    listOf("朋友", "friend") to
        RelationshipDeclaration("朋友", 0.55f, 0.35f, 0.52f, 0.52f, 0f),
    listOf("宿敌", "死敌", "敌人", "enemy", "nemesis") to
        RelationshipDeclaration("敌对关系", 0.15f, 0.05f, 0.30f, 0.55f, 0.65f),
    listOf("竞争对手", "对手", "rival") to
        RelationshipDeclaration("竞争关系", 0.35f, 0.15f, 0.45f, 0.55f, 0.40f),
)
