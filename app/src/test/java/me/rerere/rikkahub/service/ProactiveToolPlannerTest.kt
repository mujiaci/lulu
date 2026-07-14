package me.rerere.rikkahub.service

import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveToolPlannerTest {
    @Test
    fun `tired message should request health context`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我今天真的好累，感觉没精神",
            availableToolNames = setOf("get_gadgetbridge_data", "get_battery_info", "get_app_usage"),
        )

        assertTrue(plan.any { it.toolName == "get_gadgetbridge_data" && it.argumentsJson.contains("data_type") })
    }

    @Test
    fun `going outside should request location context`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我想出去走走，附近有什么地方吗",
            availableToolNames = setOf("get_location", "explore_nearby", "get_battery_info"),
        )

        assertTrue(plan.any { it.toolName == "get_location" && it.argumentsJson.contains("force_refresh") })
        assertTrue(plan.any { it.toolName == "explore_nearby" && it.argumentsJson.contains("force_refresh") })
    }

    @Test
    fun `class time should request state context and direct action hints`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我八点有课，七点五十五记得叫我一下",
            availableToolNames = setOf("get_location", "get_app_usage", "set_alarm", "calendar_tool"),
        )

        assertTrue(plan.any { it.toolName == "get_app_usage" })
        assertTrue(plan.any { it.toolName == "get_location" && it.argumentsJson.contains("force_refresh") })
        assertTrue(plan.any { it.toolName == "set_alarm" && it.autoExecutable })
        assertTrue(plan.any { it.toolName == "set_alarm" && it.argumentsJson.contains("\"hour\":7") })
        assertTrue(plan.any { it.toolName == "set_alarm" && it.argumentsJson.contains("\"minute\":55") })
        assertTrue(plan.any { it.toolName == "set_alarm" && !it.argumentsJson.contains("露露") })
        assertTrue(plan.any { it.toolName == "calendar_tool" && it.autoExecutable })
    }

    @Test
    fun `half past clock keeps thirty minute alarm`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "明天早上八点半叫我起床",
            availableToolNames = setOf("set_alarm"),
        )

        val alarm = plan.single { it.toolName == "set_alarm" }
        assertTrue(alarm.autoExecutable)
        assertTrue(alarm.argumentsJson.contains("\"hour\":8"))
        assertTrue(alarm.argumentsJson.contains("\"minute\":30"))
    }

    @Test
    fun `rest reminder without period defaults to evening`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "十点半提醒我休息",
            availableToolNames = setOf("set_alarm"),
        )

        val alarm = plan.single { it.toolName == "set_alarm" }
        assertTrue(alarm.autoExecutable)
        assertTrue(alarm.argumentsJson.contains("\"hour\":22"))
        assertTrue(alarm.argumentsJson.contains("\"minute\":30"))
    }

    @Test
    fun `explicit morning period is not shifted to evening`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "上午十点半提醒我休息",
            availableToolNames = setOf("set_alarm"),
        )

        val alarm = plan.single { it.toolName == "set_alarm" }
        assertTrue(alarm.autoExecutable)
        assertTrue(alarm.argumentsJson.contains("\"hour\":10"))
        assertTrue(alarm.argumentsJson.contains("\"minute\":30"))
    }

    @Test
    fun `period from an earlier clause does not override an evening rest reminder`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "上午八点学习，十点半提醒我休息",
            availableToolNames = setOf("set_alarm"),
        )

        val alarm = plan.single { it.toolName == "set_alarm" }
        assertTrue(alarm.argumentsJson.contains("\"hour\":22"))
        assertTrue(alarm.argumentsJson.contains("\"minute\":30"))
    }

    @Test
    fun `date words keep their morning and evening meaning`() {
        val morningPlan = ProactiveToolPlanner.plan(
            userText = "明早十点半提醒我休息",
            availableToolNames = setOf("set_alarm"),
        )
        val morningAlarm = morningPlan.single { it.toolName == "set_alarm" }
        assertTrue(morningAlarm.argumentsJson.contains("\"hour\":10"))
        assertTrue(morningAlarm.argumentsJson.contains("\"minute\":30"))

        val eveningPlan = ProactiveToolPlanner.plan(
            userText = "明晚十点叫我",
            availableToolNames = setOf("set_alarm"),
        )
        val eveningAlarm = eveningPlan.single { it.toolName == "set_alarm" }
        assertTrue(eveningAlarm.argumentsJson.contains("\"hour\":22"))
    }

    @Test
    fun `music mention should inspect current playback without changing it`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我现在听歌写作业，有点静不下来",
            availableToolNames = setOf("control_music", "get_app_usage"),
        )

        assertTrue(plan.any { it.toolName == "control_music" && it.argumentsJson.contains("get_now_playing") })
    }

    @Test
    fun `sms mention should prefer notifications before sms`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "好像有人给我发短信了，你帮我看看",
            availableToolNames = setOf("get_notifications", "read_sms"),
        )

        assertTrue(plan.any { it.toolName == "get_notifications" })
        assertTrue(plan.any { it.toolName == "read_sms" && it.autoExecutable })
    }

    @Test
    fun `camera and journal can be automatic when user asks to look and record`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "等下可以帮我看一下桌面，然后把今天这件事记进日志吗",
            availableToolNames = setOf("camera_capture", "write_lulu_journal"),
        )

        assertTrue(plan.any { it.toolName == "camera_capture" && it.autoExecutable })
        assertTrue(plan.none { it.reason.contains("露露") })
        assertTrue(plan.any { it.toolName == "write_lulu_journal" && it.autoExecutable })
    }

    @Test
    fun `meal mention should inspect phone state and nearby food context`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我还没吃饭，等会儿点外卖吧",
            availableToolNames = setOf("get_app_usage", "get_battery_info", "get_location", "explore_nearby"),
        )

        assertTrue(plan.any { it.toolName == "get_app_usage" })
        assertTrue(plan.any {
            it.toolName == "explore_nearby" &&
                it.argumentsJson.contains("force_refresh") &&
                it.argumentsJson.contains("keyword")
        })
    }

    @Test
    fun `study mention should inspect focus context`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我去写作业了，先不聊啦",
            availableToolNames = setOf("get_app_usage", "control_music", "get_battery_info"),
        )

        assertTrue(plan.any { it.toolName == "get_app_usage" })
        assertTrue(plan.any { it.toolName == "control_music" })
    }

    @Test
    fun `today study plan should prefer study store tool over calendar`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "今天我的考研计划是什么，待办还剩哪些？",
            availableToolNames = setOf("today_study_plan", "calendar_tool", "get_app_usage"),
        )

        assertTrue(plan.any { it.toolName == "today_study_plan" && it.autoExecutable })
        assertTrue(plan.none { it.toolName == "calendar_tool" && it.autoExecutable })
    }

    @Test
    fun `sleep reward report should gather health usage and battery evidence`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我昨晚一点二十睡的，今天九点二十起，早睡早起奖励可以给吗",
            availableToolNames = setOf("today_study_plan", "get_gadgetbridge_data", "get_app_usage", "get_battery_info"),
        )

        assertTrue(plan.any { it.toolName == "today_study_plan" })
        assertTrue(plan.any { it.toolName == "get_gadgetbridge_data" && it.argumentsJson.contains("sleep") })
        assertTrue(plan.any { it.toolName == "get_app_usage" })
        assertTrue(plan.any { it.toolName == "get_battery_info" })
    }
}
