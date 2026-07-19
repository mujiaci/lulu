package me.rerere.rikkahub.ui.pages.assistant.detail

import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.ProactiveCallFrequency
import me.rerere.rikkahub.data.datastore.ProactiveCallSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.voicecall.ProactiveCallManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantCallSettingsPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val conversationRepository = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val callSetting = assistant.proactiveCallSetting

    fun updateCallSetting(next: ProactiveCallSetting) {
        scope.launch {
            val current = settingsStore.settingsFlow.value
            val nextSettings = current.copy(
                proactiveMessageSetting = if (next.enabled) {
                    current.proactiveMessageSetting.copy(enabled = true)
                } else {
                    current.proactiveMessageSetting
                },
                assistants = current.assistants.map { item ->
                    if (item.id == assistant.id) item.copy(proactiveCallSetting = next) else item
                },
            )
            settingsStore.update(nextSettings)
            if (next.enabled) {
                ProactiveMessageService.scheduleNext(
                    context = context,
                    settings = nextSettings,
                    assistantId = assistant.id,
                )
            }
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val selected = result.data?.let { data ->
            IntentCompat.getParcelableExtra(
                data,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            )
        }
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            updateCallSetting(callSetting.copy(ringtoneUri = selected?.toString()))
        }
    }
    val ringtoneTitle = remember(callSetting.ringtoneUri) {
        runCatching {
            val uri = callSetting.ringtoneUri
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull().orEmpty().ifBlank { "系统默认铃声" }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("主动来电 · ${assistant.name.ifBlank { "当前角色" }}") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text("允许角色主动打电话") },
                        supportingContent = {
                            Text("是否联系、什么时候联系和第一句话都由该角色的人设与最近上下文决定。")
                        },
                        trailingContent = {
                            Switch(
                                checked = callSetting.enabled,
                                onCheckedChange = { updateCallSetting(callSetting.copy(enabled = it)) },
                            )
                        },
                    )
                }
            }

            if (callSetting.enabled) {
                item {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        item(
                            headlineContent = { Text("来电频率") },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        ProactiveCallFrequency.OCCASIONAL to "偶尔",
                                        ProactiveCallFrequency.NORMAL to "普通",
                                        ProactiveCallFrequency.FREQUENT to "经常",
                                    ).forEach { (frequency, label) ->
                                        FilterChip(
                                            selected = callSetting.frequency == frequency,
                                            onClick = {
                                                updateCallSetting(callSetting.copy(frequency = frequency))
                                            },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                            },
                        )
                        item(
                            headlineContent = { Text("来电铃声") },
                            supportingContent = { Text(ringtoneTitle) },
                            trailingContent = {
                                Button(
                                    onClick = {
                                        ringtonePicker.launch(
                                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    RingtoneManager.TYPE_RINGTONE,
                                                )
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                    callSetting.ringtoneUri
                                                        ?.takeIf(String::isNotBlank)
                                                        ?.let(Uri::parse)
                                                        ?: RingtoneManager.getDefaultUri(
                                                            RingtoneManager.TYPE_RINGTONE,
                                                        ),
                                                )
                                            },
                                        )
                                    },
                                ) {
                                    Text("选择")
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
                            supportingContent = { Text("系统允许时覆盖锁屏；否则显示普通来电通知。") },
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
                                            "系统已允许锁屏全屏来电"
                                        } else {
                                            "系统尚未允许，点击前往开启"
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
                            supportingContent = { Text("立即检查当前角色的铃声、来电页面与接听流程。") },
                            trailingContent = {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val conversationId = conversationRepository
                                                .getRecentConversations(assistant.id, limit = 1)
                                                .firstOrNull()
                                                ?.id
                                                ?: Uuid.random()
                                            ProactiveCallManager.offerIncomingCall(
                                                context = context,
                                                assistantId = assistant.id.toString(),
                                                assistantName = assistant.name.ifBlank { "当前角色" },
                                                conversationId = conversationId.toString(),
                                                reason = "用户在角色设置页主动触发了一次测试来电。",
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
        }
    }
}
