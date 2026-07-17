package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.data.companion.CompanionConversationTurn
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchor
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchorKind
import me.rerere.rikkahub.data.companion.CompanionPerceptionAssembler
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.companion.CompanionState
import me.rerere.rikkahub.data.companion.CompanionTurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionIntentPlannerTest {
    @Test
    fun `foreground fallback waits without inventing role behavior`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(),
                mode = CompanionDecisionMode.FOREGROUND,
                minutesSinceLastChat = 0L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
        assertFalse(decision.shouldMessageNow)
        assertEquals(null, decision.delayMinutes)
        assertFalse(decision.reason.contains("露露"))
    }

    @Test
    fun `background fallback does not treat long silence as an event`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(availableTools = setOf("get_battery_info", "get_app_usage")),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 180L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
        assertFalse(decision.shouldMessageNow)
        assertTrue(decision.toolNames.isEmpty())
    }

    @Test
    fun `background fallback may choose a real private activity during quiet time`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(availableTools = setOf("play_companion_game")),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 60L,
            ),
        )

        assertEquals(CompanionIntent.SELF_ACTIVITY, decision.intent)
        assertFalse(decision.shouldMessageNow)
        assertEquals("play_companion_game", decision.actionToolName)
        assertTrue(decision.actionArgumentsJson.contains("curious"))
    }

    @Test
    fun `background fallback respects unresolved relationship tension`() {
        val tenseSnapshot = CompanionSnapshot.empty("assistant-a").copy(
            relationship = CompanionRelationshipState(unresolvedTension = 0.8f),
        )
        val packet = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "Role A",
                persona = "Any role persona",
                nowMillis = 1_000L,
            ),
            snapshot = tenseSnapshot,
        )

        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = packet,
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 180L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
    }

    @Test
    fun `model parser accepts legacy labels but returns role neutral intent`() {
        val decision = CompanionIntentModelPlanner.parsePlan(
            rawText = """
                {
                  "intent": "check_context",
                  "shouldMessageNow": false,
                  "delayMinutes": 99999,
                  "toolNames": ["camera_capture", "unknown", "camera_capture"],
                  "reason": "Recheck current context before deciding.",
                  "tone": "follow persona",
                  "innerThought": "I should look again before speaking."
                }
            """.trimIndent(),
            availableToolNames = setOf("camera_capture"),
        )

        assertEquals(CompanionIntent.OBSERVE, decision?.intent)
        assertEquals(24 * 60, decision?.delayMinutes)
        assertEquals(listOf("camera_capture"), decision?.toolNames)
        assertTrue(decision?.fromModel == true)
    }

    @Test
    fun `model prompt contains persona and unified runtime truth`() {
        val prompt = CompanionIntentModelPlanner.buildPrompt(
            CompanionIntentInput(
                perception = perception(
                    persona = "A restrained housekeeper persona",
                    memoryContext = "用户曾明确说过早晨很难醒。",
                ),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 45L,
            ),
        )

        assertTrue(prompt.contains("A restrained housekeeper persona"))
        assertTrue(prompt.contains("<companion_runtime"))
        assertTrue(prompt.contains("FOLLOW_UP, STAY_AVAILABLE, REACH_OUT, OBSERVE, SELF_ACTIVITY, WAIT"))
        assertTrue(prompt.contains("用户曾明确说过早晨很难醒。"))
        assertTrue(prompt.contains("Elapsed silence is context, not an event"))
        assertFalse(prompt.contains("study-supervisor"))
    }

    @Test
    fun `model self activity requires a real available action tool`() {
        val accepted = CompanionIntentModelPlanner.parsePlan(
            rawText = """{"intent":"SELF_ACTIVITY","actionToolName":"play_companion_game","actionArguments":{"strategy":"careful"}}""",
            availableToolNames = setOf("play_companion_game"),
        )
        val rejected = CompanionIntentModelPlanner.parsePlan(
            rawText = """{"intent":"SELF_ACTIVITY","actionToolName":"imaginary_game"}""",
            availableToolNames = setOf("play_companion_game"),
        )

        assertEquals(CompanionIntent.SELF_ACTIVITY, accepted?.intent)
        assertEquals("play_companion_game", accepted?.actionToolName)
        assertTrue(accepted?.actionArgumentsJson?.contains("careful") == true)
        assertEquals(null, rejected)
    }

    @Test
    fun `high relationship tension suppresses unsolicited reach out`() {
        val constrained = CompanionIntentDecision(
            intent = CompanionIntent.REACH_OUT,
            shouldMessageNow = true,
            delayMinutes = null,
            toolNames = listOf("get_notifications"),
            reason = "Say something casual.",
            tone = "warm",
            followUps = listOf(CompanionFollowUpPlan(10, "Try again")),
            fromModel = true,
        ).enforceRelationshipPolicy(
            CompanionRelationshipState(unresolvedTension = 0.8f),
        )

        assertEquals(CompanionIntent.WAIT, constrained.intent)
        assertFalse(constrained.shouldMessageNow)
        assertTrue(constrained.toolNames.isEmpty())
        assertTrue(constrained.followUps.isEmpty())
    }

    @Test
    fun `low boundary confidence blocks intrusive model tools unless user asked`() {
        val relationship = CompanionRelationshipState(boundaryConfidence = 0.2f)
        val plan = CompanionChatTurnPlan(
            toolRequests = listOf(
                ProactiveToolRequest("camera_capture", "Look around"),
                ProactiveToolRequest("get_weather", "Check weather"),
            ),
        )

        val unprompted = plan.enforceRelationshipPolicy(relationship, latestUserText = "今天怎么样")
        val requested = plan.enforceRelationshipPolicy(relationship, latestUserText = "打开摄像头看看周围")

        assertEquals(listOf("get_weather"), unprompted.toolRequests.map { it.toolName })
        assertEquals(listOf("camera_capture", "get_weather"), requested.toolRequests.map { it.toolName })
    }

    @Test
    fun `explicit alarm request survives low trust gate`() {
        val constrained = CompanionChatTurnPlan(
            toolRequests = listOf(ProactiveToolRequest("set_alarm", "Set requested alarm")),
        ).enforceRelationshipPolicy(
            relationship = CompanionRelationshipState(trust = 0.2f),
            latestUserText = "明早九点给我定个闹钟",
        )

        assertEquals(listOf("set_alarm"), constrained.toolRequests.map { it.toolName })
    }

    @Test
    fun `chat turn prompt uses unified perception without legacy thought state`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "阿澈",
                persona = "沉稳的男性朋友，尊重边界。",
                recentTurns = listOf(
                    CompanionConversationTurn(
                        role = CompanionTurnRole.USER,
                        content = "今天有点累",
                        createdAt = 900L,
                    ),
                ),
                contextFacts = listOf(
                    CompanionContextFact(
                        key = "minutes_since_previous_interaction",
                        value = "45",
                        observedAt = 1_000L,
                    ),
                ),
                availableToolNames = setOf("get_battery_info"),
                memoryContext = "用户不喜欢被连续追问。",
                nowMillis = 1_000L,
            ),
            snapshot = CompanionSnapshot.empty("assistant-a").copy(
                state = CompanionState(statusText = "安静留意", mindState = "专注"),
            ),
        )
        val prompt = CompanionChatTurnModelPlanner.buildChatTurnPrompt(
            CompanionChatTurnPlanInput(perception = packet),
        )

        assertTrue(prompt.contains("本轮聊天前的行动规划"))
        assertTrue(prompt.contains("如果当前角色决定稍后主动找用户"))
        assertTrue(prompt.contains("delayMinutes 永远是从 current_time 开始计算的相对分钟数"))
        assertTrue(prompt.contains("绝不能误排到第二天早晨"))
        assertTrue(prompt.contains("沉默时长本身不是事件"))
        assertTrue(prompt.contains("不能只写‘注意力还停在对话上’"))
        assertTrue(prompt.contains("<companion_runtime"))
        assertTrue(prompt.contains("安静留意"))
        assertTrue(prompt.contains("minutes_since_previous_interaction=45"))
        assertTrue(prompt.contains("用户不喜欢被连续追问。"))
        assertTrue(prompt.contains("USER: 今天有点累"))
        assertTrue(prompt.contains("get_battery_info"))
        assertFalse(prompt.contains("<pendingThoughts>"))
        assertFalse(prompt.contains("她现在"))
        assertFalse(prompt.contains("如果她决定"))
        assertFalse(prompt.contains("露露"))
    }

    @Test
    fun `chat turn parser accepts durable responsibility proposals and only known cancellations`() {
        val existing = CompanionAlwaysOnAnchor(
            id = "assistant-a:responsibility:water",
            assistantId = "assistant-a",
            kind = CompanionAlwaysOnAnchorKind.RESPONSIBILITY,
            statement = "用户希望我记得提醒喝水",
            responsibility = "在长时间学习后提醒补水",
            actions = listOf("查看学习时长并自然提醒"),
        )
        val plan = CompanionChatTurnModelPlanner.parseChatTurnPlan(
            rawText = """
                {
                  "responsibilityAnchorUpserts": [{
                    "stableKey": "water",
                    "kind": "RESPONSIBILITY",
                    "statement": "用户把规律喝水交给我照看",
                    "responsibility": "学习时间过长时提醒用户补水",
                    "triggers": ["连续学习超过两小时"],
                    "actions": ["读取学习时长", "发送轻提醒"],
                    "avoid": ["不要高频打扰"],
                    "importance": 4
                  }],
                  "cancelResponsibilityAnchorIds": ["assistant-a:responsibility:water", "unknown"]
                }
            """.trimIndent(),
            availableToolNames = emptySet(),
            activeResponsibilityAnchorIds = setOf(existing.id),
        )

        assertEquals(1, plan.responsibilityAnchorUpserts.size)
        assertEquals("water", plan.responsibilityAnchorUpserts.single().stableKey)
        assertEquals(listOf(existing.id), plan.cancelResponsibilityAnchorIds)
    }

    private fun perception(
        persona: String = "Any role persona",
        availableTools: Set<String> = emptySet(),
        memoryContext: String = "",
    ) = CompanionPerceptionAssembler.assemble(
        input = CompanionPerceptionInput(
            assistantId = "assistant-a",
            assistantName = "Role A",
            persona = persona,
            availableToolNames = availableTools,
            memoryContext = memoryContext,
            nowMillis = 1_000L,
        ),
        snapshot = CompanionSnapshot.empty("assistant-a"),
    )
}
