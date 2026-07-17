package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Setting07
import me.rerere.rikkahub.R

@Composable
fun ConversationSystemPromptDialog(
    visible: Boolean,
    customSystemPrompt: String?,
    onSystemPromptChange: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return

    var editText by rememberSaveable(customSystemPrompt, visible) {
        mutableStateOf(customSystemPrompt ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = HugeIcons.Setting07,
                contentDescription = null,
            )
        },
        title = {
            Text(stringResource(R.string.chat_page_conversation_system_prompt))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.chat_page_conversation_system_prompt_hint)) },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSystemPromptChange(editText.ifBlank { null })
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.chat_page_conversation_system_prompt_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                if (!customSystemPrompt.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            editText = ""
                            onSystemPromptChange(null)
                            onDismissRequest()
                        },
                    ) {
                        Text(stringResource(R.string.chat_page_conversation_system_prompt_clear))
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
