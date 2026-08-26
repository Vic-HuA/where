package com.vichua.where.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 录制语音名称：先说明用途，再提供开始、停止、试听和保存。
 */
@Composable
fun VoiceLabelRecordDialog(
    title: String,
    recording: Boolean,
    hasPreview: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = if (hasPreview) "保存" else null,
        onConfirm = if (hasPreview) onSave else ({}),
        dismissText = "取消",
        confirmEnabled = hasPreview && !recording && !submitting,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "这段录音只用来称呼当前位置或物品，不会拿去做识别。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = if (recording) onStop else onStart,
                enabled = !submitting,
            ) {
                Text(if (recording) "停止录音" else "开始录音")
            }
            if (hasPreview) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !recording && !submitting,
                ) {
                    Text("试听")
                }
            }
        }
    }
}
