package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.platform.ManagedBackupFile

/**
 * 应用内选择固定目录中的备份，避免再打开系统文件界面。
 *
 * 「从其他位置导入」留给旧的 Downloads 备份，不作为日常入口。
 */
@Composable
fun ManagedBackupPickerDialog(
    backups: List<ManagedBackupFile>,
    loading: Boolean,
    formatTime: (Long) -> String,
    onSelect: (ManagedBackupFile) -> Unit,
    onImportFromElsewhere: () -> Unit,
    onDismiss: () -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "选择备份文件",
        confirmText = null,
        dismissText = "取消",
        onDismiss = onDismiss,
        neutralText = "从其他位置导入",
        onNeutral = onImportFromElsewhere,
    ) {
        ManagedBackupList(
            backups = backups,
            loading = loading,
            selectedUri = null,
            formatTime = formatTime,
            onSelect = onSelect,
        )
    }
}

/**
 * 固定备份目录列表，设置弹窗和恢复页共用，避免两套选文件交互。
 */
@Composable
fun ColumnScope.ManagedBackupList(
    backups: List<ManagedBackupFile>,
    loading: Boolean,
    selectedUri: String?,
    formatTime: (Long) -> String,
    onSelect: (ManagedBackupFile) -> Unit,
) {
    Text(
        text = "点选下面的备份即可。从其他位置导入只用于旧文件。",
        color = WhereSecondaryTextColor,
        style = MaterialTheme.typography.bodySmall,
    )
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 20.dp),
            color = WherePrimaryColor,
        )
        return
    }
    if (backups.isEmpty()) {
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = "还没有备份。先到设置里创建加密备份，或从其他位置导入旧文件。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    backups.forEach { backup ->
        val selected = backup.opaqueDocumentUri == selectedUri
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clickable(
                    role = Role.Button,
                    onClick = {
                        onSelect(backup)
                    },
                ),
            color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = WhereStrokeWidth,
                color = if (selected) WherePrimaryColor else WhereOutlineColor,
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = backup.displayName,
                    color = WherePrimaryTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(backup.lastModifiedMillis),
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = visiblePackageSize(backup.sizeBytes),
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
