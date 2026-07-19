package me.rerere.rikkahub.ui.pages.setting

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.data.datastore.ProactiveCallFrequency
import me.rerere.rikkahub.data.datastore.ProactiveCallSetting
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.voicecall.ProactiveCallManager
import me.rerere.rikkahub.data.service.ProactiveMessageWorker
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingProactiveMessagePage(vm: SettingVM = koinInject()) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentAssistant = settings.getCurrentAssistant()
    val callSetting = currentAssistant.proactiveCallSetting
    val conversationRepository = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun updateCallSetting(next: ProactiveCallSetting) {
        vm.updateSettings(
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == currentAssistant.id) {
                        assistant.copy(proactiveCallSetting = next)
                    } else {
                        assistant
                    }
                },
            ),
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("主动消息") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                CardGroup {
                    // Enable switch
                    item(
                        headlineContent = { Text("启用主动消息") },
                        supportingContent = { Text("开启后按角色挂心、承诺、静默时长和关系状态自然判断是否联系你") },
                        trailingContent = {
                            Switch(
                                checked = settings.proactiveMessageSetting.enabled,
                                onCheckedChange = { enabled ->
                                    val newSetting = settings.proactiveMessageSetting.copy(enabled = enabled)
                                    vm.updateSettings(settings.copy(proactiveMessageSetting = newSetting))
                                    if (enabled) {
                                        ProactiveMessageService.scheduleNext(context, newSetting)
                                    } else {
                                        ProactiveMessageService.cancel(context)
                                    }
                                }
                            )
                        }
                    )
                    // Next trigger time
                    if (settings.proactiveMessageSetting.enabled) {
                        var nextTime by remember { mutableStateOf(ProactiveMessageService.getNextTriggerTime(context)) }
                        LaunchedEffect(settings.proactiveMessageSetting) {
                            nextTime = ProactiveMessageService.getNextTriggerTime(context)
                            while (true) {
                                kotlinx.coroutines.delay(10_000L)
                                nextTime = ProactiveMessageService.getNextTriggerTime(context)
                            }
                        }
                        item(
                            headlineContent = { Text("下次情境判断") },
                            supportingContent = {
                                val currentTime = System.currentTimeMillis()
                                val triggerTime = nextTime
                                if (triggerTime != null && triggerTime > currentTime) {
                                    val remaining = triggerTime - currentTime
                                    val remainMinutes = remaining / 60_000
                                    val remainSeconds = (remaining % 60_000) / 1000
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    Text("🕐 ${sdf.format(java.util.Date(triggerTime))}（剩余 ${remainMinutes}分${remainSeconds}秒）")
                                } else {
                                    Text("等待调度中...")
                                }
                            }
                        )
                    }
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("角色主动来电") },
                        supportingContent = {
                            Text("角色先按人设和最近上下文决定是否联系，再按下方频率选择电话或消息。移动数据默认可用。")
                        },
                        trailingContent = {
                            Switch(
                                checked = callSetting.enabled,
                                onCheckedChange = { enabled ->
                                    val proactive = if (enabled) {
                                        settings.proactiveMessageSetting.copy(enabled = true)
                                    } else {
                                        settings.proactiveMessageSetting
                                    }
                                    vm.updateSettings(
                                        settings.copy(
                                            proactiveMessageSetting = proactive,
                                            assistants = settings.assistants.map { assistant ->
                                                if (assistant.id == currentAssistant.id) {
                                                    assistant.copy(
                                                        proactiveCallSetting = callSetting.copy(enabled = enabled),
                                                    )
                                                } else {
                                                    assistant
                                                }
                                            },
                                        ),
                                    )
                                    if (enabled) {
                                        ProactiveMessageService.scheduleNext(context, proactive)
                                    }
                                },
                            )
                        },
                    )
                    if (callSetting.enabled) {
                        item(
                            headlineContent = { Text("来电频率") },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        ProactiveCallFrequency.OCCASIONAL to "偶尔",
                                        ProactiveCallFrequency.NORMAL to "普通",
                                        ProactiveCallFrequency.FREQUENT to "经常",
                                    ).forEach { (frequency, label) ->
                                        FilterChip(
                                            selected = callSetting.frequency == frequency,
                                            onClick = { updateCallSetting(callSetting.copy(frequency = frequency)) },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                            },
                        )
                        item(
                            headlineContent = { Text("允许移动数据") },
                            supportingContent = { Text("关闭后只有连接 Wi-Fi 时才会发起来电。") },
                            trailingContent = {
                                Switch(
                                    checked = callSetting.allowMobileData,
                                    onCheckedChange = {
                                        updateCallSetting(callSetting.copy(allowMobileData = it))
                                    },
                                )
                            },
                        )
                        item(
                            headlineContent = { Text("锁屏全屏来电") },
                            supportingContent = { Text("系统允许时覆盖锁屏；解锁使用手机时通常显示悬浮来电通知。") },
                            trailingContent = {
                                Switch(
                                    checked = callSetting.fullScreenWhenAllowed,
                                    onCheckedChange = {
                                        updateCallSetting(callSetting.copy(fullScreenWhenAllowed = it))
                                    },
                                )
                            },
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            item(
                                headlineContent = { Text("全屏来电系统权限") },
                                supportingContent = {
                                    val manager = context.getSystemService(NotificationManager::class.java)
                                    Text(
                                        if (manager.canUseFullScreenIntent()) {
                                            "✅ 系统已允许锁屏全屏来电"
                                        } else {
                                            "⚠️ 系统尚未允许；仍会使用普通来电通知"
                                        },
                                    )
                                },
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            },
                                        )
                                    }
                                },
                            )
                        }
                        item(
                            headlineContent = { Text("最短来电间隔（小时）") },
                            supportingContent = {
                                OutlinedTextField(
                                    value = callSetting.minIntervalHours.toString(),
                                    onValueChange = { value ->
                                        value.toIntOrNull()?.takeIf { it in 1..168 }?.let {
                                            updateCallSetting(callSetting.copy(minIntervalHours = it))
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            },
                        )
                        item(
                            headlineContent = { Text("安静时段") },
                            supportingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = callSetting.quietStartHour.toString(),
                                        onValueChange = { value ->
                                            value.toIntOrNull()?.takeIf { it in 0..23 }?.let {
                                                updateCallSetting(callSetting.copy(quietStartHour = it))
                                            }
                                        },
                                        label = { Text("开始小时") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = callSetting.quietEndHour.toString(),
                                        onValueChange = { value ->
                                            value.toIntOrNull()?.takeIf { it in 0..23 }?.let {
                                                updateCallSetting(callSetting.copy(quietEndHour = it))
                                            }
                                        },
                                        label = { Text("结束小时") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            },
                        )
                        item(
                            headlineContent = { Text("测试来电") },
                            supportingContent = { Text("立即用当前角色检查来电页面、通知和接听流程。") },
                            trailingContent = {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val conversationId = conversationRepository
                                                .getRecentConversations(currentAssistant.id, limit = 1)
                                                .firstOrNull()
                                                ?.id
                                                ?: Uuid.random()
                                            ProactiveCallManager.offerIncomingCall(
                                                context = context,
                                                assistantId = currentAssistant.id.toString(),
                                                assistantName = currentAssistant.name.ifBlank { "当前角色" },
                                                conversationId = conversationId.toString(),
                                                reason = "用户在设置页主动触发了一次测试来电。",
                                                setting = callSetting,
                                                force = true,
                                            )
                                        }
                                    },
                                ) {
                                    Text("现在测试")
                                }
                            },
                        )
                    }
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("自然节奏") },
                        supportingContent = { Text("开启后不按固定分钟循环；后台触发只是重新感知和判断，不一定每次都发消息。") },
                        trailingContent = {
                            Switch(
                                checked = settings.proactiveMessageSetting.naturalScheduling,
                                onCheckedChange = { enabled ->
                                    vm.updateSettings(
                                        settings.copy(
                                            proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                naturalScheduling = enabled,
                                            ),
                                        ),
                                    )
                                },
                            )
                        },
                    )
                    if (!settings.proactiveMessageSetting.naturalScheduling) {
                        // Min interval
                        item(
                            headlineContent = { Text("最小间隔 (分钟)") },
                            supportingContent = {
                                OutlinedTextField(
                                    value = settings.proactiveMessageSetting.minIntervalMinutes.toString(),
                                    onValueChange = { value ->
                                        val minutes = value.toIntOrNull()
                                        if (minutes != null && minutes > 0) {
                                            vm.updateSettings(
                                                settings.copy(
                                                    proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                        minIntervalMinutes = minutes
                                                    )
                                                )
                                            )
                                        }
                                    },
                                    placeholder = { Text("30") },
                                    singleLine = true,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            },
                        )

                        // Max interval
                        item(
                            headlineContent = { Text("最大间隔 (分钟)") },
                            supportingContent = {
                                OutlinedTextField(
                                    value = settings.proactiveMessageSetting.maxIntervalMinutes.toString(),
                                    onValueChange = { value ->
                                        val minutes = value.toIntOrNull()
                                        if (minutes != null && minutes >= settings.proactiveMessageSetting.minIntervalMinutes) {
                                            vm.updateSettings(
                                                settings.copy(
                                                    proactiveMessageSetting = settings.proactiveMessageSetting.copy(
                                                        maxIntervalMinutes = minutes
                                                    )
                                                )
                                            )
                                        }
                                    },
                                    placeholder = { Text("90") },
                                    singleLine = true,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            },
                        )
                    }
                }
            }

            // Exact alarm permission check (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    val hasExactAlarm = ProactiveMessageWorker.canScheduleExactAlarms(context)
                    CardGroup {
                        item(
                            headlineContent = { Text("精确闹钟权限") },
                            supportingContent = {
                                if (hasExactAlarm) {
                                    Text("✅ 已授予精确闹钟权限，定时触发将更准确")
                                } else {
                                    Text("⚠️ 未授予精确闹钟权限，触发时间可能不精确。已自动使用 WorkManager 作为备用方案。")
                                }
                            },
                            onClick = if (!hasExactAlarm) {
                                {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback to app settings
                                        val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            } else null
                        )
                    }
                }
            }

            // Battery optimization bypass
            item {
                val isIgnoring = ProactiveMessageWorker.isIgnoringBatteryOptimizations(context)
                CardGroup {
                    item(
                        headlineContent = { Text("电池优化") },
                        supportingContent = {
                            if (isIgnoring) {
                                Text("✅ 已忽略电池优化，后台触发更稳定")
                            } else {
                                Text("⚠️ 未忽略电池优化，系统可能限制后台活动导致消息无法准时触发。建议关闭电池优化。")
                            }
                        },
                        onClick = if (!isIgnoring) {
                            {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        } else null
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("说明") },
                        supportingContent = { Text("启用后，AI 会在设定的最小和最大间隔之间随机一个时间点主动给你发消息。" +
                            "你回复后计时器重置，重新开始随机计时；不回复则继续循环发消息。" +
                            "AI 可以自己思考选择要不要回复，如果觉得没什么好说的可以跳过。\n\n" +
                            "提示：同时使用 AlarmManager + WorkManager 双重调度，确保消息能准时触发。") },
                    )
                }
            }
        }
    }
}
