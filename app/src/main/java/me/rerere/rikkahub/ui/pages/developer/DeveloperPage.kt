package me.rerere.rikkahub.ui.pages.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileScript
import me.rerere.rikkahub.data.ai.AILogging
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeveloperPage(vm: DeveloperVM = koinViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "API 控制台",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = vm::clearLogs) {
                        Icon(HugeIcons.Delete01, contentDescription = "清空日志")
                    }
                },
            )
        },
    ) { innerPadding ->
        LoggingPaging(
            vm = vm,
            contentPadding = innerPadding,
        )
    }
}

@Composable
fun LoggingPaging(
    vm: DeveloperVM,
    contentPadding: PaddingValues,
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.FileScript,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "还没有 API 调用",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "发一条消息后，这里会显示模型、输入/输出 token 和调用状态。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(logs.asReversed(), key = { log ->
                when (log) {
                    is AILogging.Generation -> log.id.toString()
                }
            }) { log ->
                when (log) {
                    is AILogging.Generation -> {
                        GenerationLogCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationLogCard(log: AILogging.Generation) {
    val usage = log.usage
    val duration = log.finishedAtMillis?.let { it - log.createdAtMillis }
    val status = when {
        log.error != null -> "失败"
        log.finishedAtMillis != null -> "完成"
        else -> "进行中"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (log.error == null) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = log.params.model.displayName.ifBlank { log.params.model.modelId },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${log.providerSetting.name} · ${if (log.stream) "流式" else "非流式"} · ${formatTime(log.createdAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (log.error == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TokenStat("输入", usage?.promptTokens, Modifier.weight(1f))
                TokenStat("输出", usage?.completionTokens, Modifier.weight(1f))
                TokenStat("缓存", usage?.cachedTokens, Modifier.weight(1f))
                TokenStat("总计", usage?.totalTokens, Modifier.weight(1f))
            }

            log.breakdown?.let { breakdown ->
                val realPromptTokens = usage?.promptTokens
                val estimatedTotal = breakdown.estimatedTokens.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "输入 token 拆账",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (realPromptTokens != null) {
                            "模型真实输入 ${realPromptTokens.formatNumber()}；下方按内容长度估算并折算到真实总数。"
                        } else {
                            "模型还没返回真实输入 token；下方先显示本地粗略估算。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    breakdown.sections.forEach { section ->
                        val scaledTokens = realPromptTokens?.let {
                            (section.estimatedTokens.toDouble() / estimatedTotal * it).toInt()
                        } ?: section.estimatedTokens
                        DetailLine(
                            section.label,
                            buildString {
                                append("约 ${scaledTokens.formatNumber()} token")
                                if (section.messageCount > 0) append(" · ${section.messageCount} 条/个")
                                append(" · ${section.charCount.formatNumber()} 字符")
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailLine("聊天消息", "${log.messages.size} 条（变换前）")
                DetailLine("实际发送消息", "${log.sentMessages.size} 条")
                DetailLine("工具定义", "${log.params.tools.size} 个（发给模型可选，不等于实际调用）")
                log.breakdown?.toolNames?.takeIf { it.isNotEmpty() }?.let { toolNames ->
                    DetailLine("工具名", toolNames.take(12).joinToString("、") + if (toolNames.size > 12) " 等 ${toolNames.size} 个" else "")
                }
                DetailLine("温度", log.params.temperature?.toString() ?: "默认")
                DetailLine("Top P", log.params.topP?.toString() ?: "默认")
                DetailLine("最大输出", log.params.maxTokens?.toString() ?: "默认")
                if (duration != null) {
                    DetailLine("耗时", "${duration}ms")
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Clock02,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "等待模型返回 token 用量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                log.error?.let {
                    DetailLine("错误", it)
                }
            }
        }
    }
}

@Composable
private fun TokenStat(label: String, value: Int?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.formatNumber() ?: "-",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
