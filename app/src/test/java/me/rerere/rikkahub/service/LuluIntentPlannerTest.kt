package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluIntentPlannerTest {
    private val assistantId = Uuid.parse("11111111-2222-3333-4444-555555555555")

    @Test
    fun `study goodbye becomes delayed autonomous check instead of immediate message`() {
        val plan = LuluIntentPlanner.plan(
            input = LuluIntentInput(
                assistantName = "露露",
                state = LuluState(assistantId = assistantId, mood = LuluMood.CALM),
                userText = "我去写作业了，先不聊啦",
                assistantText = "好，我晚点轻轻看你还在不在状态里。",
                minutesSinceLastChat = 0,
                availableToolNames = setOf("get_app_usage", "control_music", "get_battery_info"),
            )
        )

        assertEquals(LuluIntent.STAY_NEAR, plan.intent)
        assertFalse(plan.shouldMessageNow)
        assertEquals(45, plan.delayMinutes)
        assertTrue(plan.toolNames.contains("get_app_usage"))
        assertTrue(plan.toolNames.contains("control_music"))
        assertTrue(plan.reason.contains("写作业"))
    }

    @Test
    fun `long silence with warm state can become immediate miss-you message`() {
        val plan = LuluIntentPlanner.plan(
            input = LuluIntentInput(
                assistantName = "露露",
                state = LuluState(assistantId = assistantId, mood = LuluMood.HAPPY, energy = LuluEnergy.HIGH),
                userText = "",
                assistantText = "刚才聊得很开心。",
                minutesSinceLastChat = 180,
                availableToolNames = setOf("get_battery_info", "get_app_usage", "camera_capture"),
            )
        )

        assertEquals(LuluIntent.REACH_OUT, plan.intent)
        assertTrue(plan.shouldMessageNow)
        assertEquals(null, plan.delayMinutes)
        assertTrue(plan.toolNames.contains("get_battery_info"))
        assertTrue(plan.reason.contains("很久"))
    }

    @Test
    fun `explicit sleep promise keeps a delayed care plan`() {
        val plan = LuluIntentPlanner.plan(
            input = LuluIntentInput(
                assistantName = "露露",
                state = LuluState(assistantId = assistantId, mood = LuluMood.SOFT, energy = LuluEnergy.SLEEPY),
                userText = "我再刷十分钟就睡",
                assistantText = "那我十分钟后叫你。",
                minutesSinceLastChat = 0,
                availableToolNames = setOf("get_gadgetbridge_data", "get_app_usage", "get_battery_info"),
            )
        )

        assertEquals(LuluIntent.CARE_REMINDER, plan.intent)
        assertFalse(plan.shouldMessageNow)
        assertEquals(10, plan.delayMinutes)
        assertTrue(plan.toolNames.contains("get_gadgetbridge_data"))
    }

    @Test
    fun `immediate reach out intent becomes near future proactive plan`() {
        val now = 1_700_000_000_000L
        val intentPlan = LuluIntentPlan(
            intent = LuluIntent.REACH_OUT,
            shouldMessageNow = true,
            delayMinutes = null,
            toolNames = listOf("get_battery_info", "get_app_usage"),
            reason = "露露想自然地找用户说句话。",
            tone = "自然、想念、不要像提醒",
        )

        val plan = intentPlan.toProactiveReminderPlan(
            userText = "",
            nowMillis = now,
        )

        assertEquals(ProactiveReminderKind.GENERAL, plan!!.kind)
        assertEquals(now + 60_000L, plan.triggerAtMillis)
        assertTrue(plan.preferredToolNames.contains("get_app_usage"))
        assertTrue(plan.reason.contains("自然"))
    }

    @Test
    fun `model planner parses and clamps autonomous json plan`() {
        val plan = LuluIntentModelPlanner.parsePlan(
            rawText = """
                ```json
                {
                  "intent": "check_context",
                  "shouldMessageNow": false,
                  "delayMinutes": 99999,
                  "toolNames": ["camera_capture", "unknown_tool", "get_app_usage", "camera_capture"],
                  "reason": "露露想先看一下用户现在的状态，再决定怎么开口。",
                  "tone": "轻一点",
                  "innerThought": "我先不急着说话，想先看清楚你现在是不是还好。"
                }
                ```
            """.trimIndent(),
            availableToolNames = setOf("camera_capture", "get_app_usage"),
        )

        assertEquals(LuluIntent.CHECK_CONTEXT, plan!!.intent)
        assertFalse(plan.shouldMessageNow)
        assertEquals(24 * 60, plan.delayMinutes)
        assertEquals(listOf("camera_capture", "get_app_usage"), plan.toolNames)
        assertTrue(plan.reason.contains("状态"))
        assertEquals("我先不急着说话，想先看清楚你现在是不是还好。", plan.innerThought)
        assertTrue(plan.fromModel)
    }

    @Test
    fun `model planner ignores invalid json plan`() {
        val plan = LuluIntentModelPlanner.parsePlan(
            rawText = "我觉得可以稍后看看，但这里不是 JSON",
            availableToolNames = setOf("get_app_usage"),
        )

        assertEquals(null, plan)
    }

    @Test
    fun `model planner parses chat turn action plan with executable and candidate tools`() {
        val plan = LuluIntentModelPlanner.parseChatTurnPlan(
            rawText = """
                {
                  "toolRequests": [
                    {
                      "toolName": "get_location",
                      "reason": "先看看用户是不是已经到上课地点",
                      "arguments": {"include_address": true},
                      "autoExecutable": true
                    },
                    {
                      "toolName": "set_alarm",
                      "reason": "如果时间明确就顺手提醒",
                      "arguments": {"hour": 19, "minute": 55, "label": "露露提醒上课"},
                      "autoExecutable": false
                    },
                    {
                      "toolName": "unknown_tool",
                      "reason": "应该被过滤",
                      "autoExecutable": true
                    }
                  ],
                  "followUpDelayMinutes": 25,
                  "followUpReason": "等一会儿确认他有没有准备好",
                  "expressionGuidance": "短一点，像自然想起来",
                  "innerThought": "我先看清楚他的状态，再决定这句话要不要轻轻递过去。"
                }
            """.trimIndent(),
            availableToolNames = setOf("get_location", "set_alarm", "get_app_usage"),
        )

        assertEquals(2, plan.toolRequests.size)
        assertEquals("get_location", plan.toolRequests[0].toolName)
        assertTrue(plan.toolRequests[0].autoExecutable)
        assertEquals("""{"include_address":true}""", plan.toolRequests[0].argumentsJson)
        assertEquals("set_alarm", plan.toolRequests[1].toolName)
        assertTrue(plan.toolRequests[1].autoExecutable)
        assertEquals(25, plan.followUpDelayMinutes)
        assertTrue(plan.followUpReason!!.contains("确认"))
        assertTrue(plan.expressionGuidance!!.contains("短一点"))
        assertEquals("我先看清楚他的状态，再决定这句话要不要轻轻递过去。", plan.innerThought)
    }

    @Test
    fun `chat turn prompt keeps state generation after judgment`() {
        val prompt = LuluIntentModelPlanner.buildChatTurnPrompt(
            LuluChatTurnPlanInput(
                assistantName = "露露",
                assistantPersona = "管家型角色，会认真照看用户的学习和身体状态。",
                state = LuluState(assistantId = assistantId),
                recentMessages = emptyList(),
                availableToolNames = setOf("get_app_usage", "set_alarm"),
            )
        )

        assertTrue(prompt.contains("状态生成放在行动后"))
        assertTrue(prompt.contains("只生成心情、身体状况、精神状况、亲密关系和第一人称没说出口"))
        assertTrue(prompt.contains("动态判断"))
        assertFalse(prompt.contains("State 只保存第一视角 belief"))
        assertFalse(prompt.contains("trait/situational motive"))
        assertFalse(prompt.contains("ReAct 属于审议层"))
    }

    @Test
    fun `model planner clamps chat turn action plan and limits tools`() {
        val plan = LuluIntentModelPlanner.parseChatTurnPlan(
            rawText = """
                {
                  "toolRequests": [
                    {"toolName": "a", "reason": "1"},
                    {"toolName": "b", "reason": "2"},
                    {"toolName": "c", "reason": "3"},
                    {"toolName": "d", "reason": "4"},
                    {"toolName": "e", "reason": "5"},
                    {"toolName": "f", "reason": "6"}
                  ],
                  "followUpDelayMinutes": 99999
                }
            """.trimIndent(),
            availableToolNames = setOf("a", "b", "c", "d", "e", "f"),
        )

        assertEquals(listOf("a", "b", "c", "d", "e"), plan.toolRequests.map { it.toolName })
        assertEquals(24 * 60, plan.followUpDelayMinutes)
    }

    @Test
    fun `model planner parses expression affordance pool`() {
        val plan = LuluIntentModelPlanner.parseChatTurnPlan(
            rawText = """
                {
                  "expressionGuidance": "short, warm, and not policy-making",
                  "expressionAffordances": ["TEXT", "KAOMOJI", "STATUS_BAR", "UNKNOWN", "TEXT"]
                }
            """.trimIndent(),
            availableToolNames = emptySet(),
        )

        assertEquals("short, warm, and not policy-making", plan.expressionGuidance)
        assertEquals(
            listOf(
                LuluExpressionAffordance.TEXT,
                LuluExpressionAffordance.KAOMOJI,
                LuluExpressionAffordance.STATUS_BAR,
            ),
            plan.expressionAffordances,
        )
    }

    @Test
    fun `chat turn follow up rejects ordinary return even when model asks for five minutes`() {
        val plan = LuluChatTurnPlan(
            followUpDelayMinutes = 5,
            followUpReason = "五分钟后确认他还在不在",
        )

        assertFalse(plan.shouldScheduleFollowUpForUserTurn("我回来了"))
    }

    @Test
    fun `check context plan does not become reminder after ordinary return`() {
        val plan = LuluIntentPlan(
            intent = LuluIntent.CHECK_CONTEXT,
            shouldMessageNow = false,
            delayMinutes = 10,
            toolNames = listOf("get_app_usage"),
            reason = "露露需要结合人设和当前感知，再决定要不要靠近。",
            tone = "轻一点",
        )

        assertEquals(null, plan.toProactiveReminderPlan(userText = "我回来了", nowMillis = 1_700_000_000_000L))
    }

    @Test
    fun `chat turn follow up keeps explicit safety or study follow ups`() {
        val health = LuluChatTurnPlan(
            followUpDelayMinutes = 5,
            followUpReason = "确认肚子疼有没有好一点",
        )
        val study = LuluChatTurnPlan(
            followUpDelayMinutes = 45,
            followUpReason = "确认专业课有没有开始",
        )

        assertTrue(health.shouldScheduleFollowUpForUserTurn("我肚子好疼"))
        assertTrue(study.shouldScheduleFollowUpForUserTurn("我去学习了，先不聊"))
    }

    @Test
    fun `local time context labels midnight as late night in twenty four hour format`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val nowMillis = java.time.LocalDateTime.of(2026, 7, 4, 0, 20)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val text = LocalTimeContextFormatter.format(nowMillis = nowMillis, zoneId = zone)

        assertTrue(text.contains("00:20"))
        assertTrue(text.contains("凌晨"))
        assertTrue(text.contains("24小时制"))
        assertFalse(text.contains("下午"))
    }
}
