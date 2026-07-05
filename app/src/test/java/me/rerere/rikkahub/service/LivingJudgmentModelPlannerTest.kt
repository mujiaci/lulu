package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingJudgmentModelPlannerTest {
    @Test
    fun `prompt asks model to choose next evaluation delay`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我会自己判断下次什么时候想你。",
            nowMillis = NOW,
        )
        val prompt = LivingJudgmentModelPlanner.buildPrompt(
            LivingJudgmentModelInput(
                assistantName = "露露",
                persona = "小管家。",
                intent = intent,
                observation = LivingObservation(summary = "No risk signals.", createdAt = NOW),
                recentConversation = emptyList(),
            )
        )

        assertTrue(prompt.contains("nextEvaluateDelayMinutes"))
        assertTrue(prompt.contains("情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀"))
        assertTrue(prompt.contains("traitMotive"))
        assertTrue(prompt.contains("situationalMotive"))
        assertTrue(prompt.contains("emotionLabel"))
        assertTrue(prompt.contains("ReAct 属于这里"))
        assertTrue(prompt.contains("不要照抄固定表"))
        assertTrue(prompt.contains("不等于多久后发消息"))
    }

    @Test
    fun `parse trace extracts fenced json judgment`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我肚子疼，先不回你",
            assistantText = "我会先看一下情况。",
            nowMillis = NOW,
        )
        val observation = LivingObservation(
            summary = "Runtime observation before judgment.",
            signals = listOf("missing_tool=get_gadgetbridge_data"),
            createdAt = NOW,
        )
        val input = LivingJudgmentModelInput(
            assistantName = "露露",
            persona = "小管家，关心用户但会克制。",
            intent = intent,
            observation = observation,
            recentConversation = emptyList(),
        )

        val trace = LivingJudgmentModelPlanner.parseTrace(
            rawText = """
                ```json
                {
                  "belief": "用户身体不舒服，而且暂时没有回复。",
                  "traitMotive": "露露长期想保护用户。",
                  "situationalMotive": "这次因为用户肚子疼才特别在意。",
                  "motive": "确认安全，同时不要制造恐慌。",
                  "emotion": {
                    "emotionLabel": "担心但压低声音",
                    "feltSense": "心口发紧",
                    "impulse": "想马上确认安全",
                    "restraint": "压住连环追问",
                    "intensity": 9
                  },
                  "intention": "先观察工具线索，再决定是否轻轻确认。",
                  "thought": "我没有足够健康线索，所以不能假装知道。",
                  "action": "TOOL_CHECK, MESSAGE, SCHEDULE_NEXT_TICK",
                  "observation": "get_gadgetbridge_data is missing.",
                  "decision": "可以准备轻声确认，但允许后续生成时 PASS。",
                  "nextEvaluateDelayMinutes": 17,
                  "appraisal": {
                    "meaning": "身体安全优先。",
                    "value": "确认用户安全。",
                    "risk": "漏判身体风险。",
                    "cost": "一次轻量工具观察。",
                    "consequence": "线索不足时短周期复查。",
                    "resources": "健康和电量工具。"
                  },
                  "consolidation": {
                    "episodicTrace": "记录身体不适线索。",
                    "affectiveResidue": "留下担心但克制的余温。",
                    "semanticMemory": "身体不适时更需要主动照看。",
                    "policyLearning": "短周期复查。"
                  },
                  "historyNote": "第一次静默判断。"
                }
                ```
            """.trimIndent(),
            input = input,
        )

        assertEquals(LivingJudgmentSource.MAIN_API_STRUCTURED_JUDGMENT, trace?.source)
        assertEquals("TOOL_CHECK, MESSAGE, SCHEDULE_NEXT_TICK", trace?.action)
        assertEquals(17, trace?.nextEvaluateDelayMinutes)
        assertTrue(trace?.thought?.contains("不能假装知道") == true)
        assertTrue(trace?.motive?.contains("确认安全") == true)
        assertTrue(trace?.traitMotive?.contains("保护用户") == true)
        assertTrue(trace?.situationalMotive?.contains("肚子疼") == true)
        assertTrue(trace?.emotion?.emotionLabel?.contains("担心") == true)
        assertEquals(9, trace?.emotion?.intensity)
        assertTrue(trace?.appraisal?.risk?.contains("身体风险") == true)
        assertTrue(trace?.consolidation?.policyLearning?.contains("短周期") == true)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
