package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.ConflictResolution
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.RestoreSession

/**
 * 独立恢复备份页：先看预览和冲突，再选择合并或替换。
 *
 * 从设置进入，返回时取消尚未执行的恢复会话，避免设置页被预览对话框挡住。
 */
@Composable
fun RestoreBackupScreen(
    session: RestoreSession?,
    lastVerifiedBackupText: String?,
    currentSummary: HouseholdDataSummary?,
    resolutions: Map<String, ConflictResolution>,
    submitting: Boolean,
    progressText: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onResolve: (String, ConflictResolution) -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    var replaceConfirmVisible by remember { mutableStateOf(false) }
    val preview = session?.preview
    val allConflictsResolved = preview?.conflicts?.all { conflict ->
        resolutions[conflict.key] != null
    } == true
    val canMerge = !submitting &&
        preview != null &&
        !preview.differentHousehold &&
        allConflictsResolved

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(12.dp),
                imageVector = WhereIcons.Back,
                contentDescription = "返回",
            )
            Text("恢复备份", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "先核对备份摘要和冲突，再决定合并或替换。未执行前不会改当前家庭。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (preview == null) {
            Text(
                modifier = Modifier.padding(top = 24.dp),
                text = errorMessage ?: "暂时无法读取恢复预览。",
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = WhereSurfaceColor,
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "备份摘要",
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "格式版本 ${preview.manifest.formatVersion}",
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("加密：${preview.manifest.encryptionAlgorithm}")
                Text("物品 ${preview.itemCount} 件，位置 ${preview.locationCount} 个")
                Text("物品照片 ${preview.itemPhotoCount} 张")
                Text(
                    "新增 ${preview.addedCount}，更新 ${preview.updatedCount}，删除 ${preview.deletedCount}，冲突 ${preview.conflictCount}",
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = lastVerifiedBackupText ?: "尚未成功备份。密码丢失后无法恢复。",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                currentSummary?.let { summary ->
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "当前家庭：物品 ${summary.itemCount}，位置 ${summary.locationCount}，照片 ${summary.photoCount}。",
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (preview.differentHousehold) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "备份来自另一个家庭，只能替换，不能合并。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        preview.conflicts.forEach { conflict ->
            val selected = resolutions[conflict.key]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = WhereSurfaceColor,
                border = BorderStroke(1.dp, WhereOutlineColor),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${conflict.entityLabel} · ${conflict.conflictFields.joinToString("、")}",
                        fontWeight = FontWeight.Bold,
                        color = WherePrimaryTextColor,
                    )
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "当前：${conflict.localValue}",
                    )
                    Text("备份：${conflict.incomingValue}")
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        if (ConflictResolution.KEEP_LOCAL in conflict.allowedResolutions) {
                            TextButton(
                                enabled = !submitting,
                                onClick = {
                                    onResolve(conflict.key, ConflictResolution.KEEP_LOCAL)
                                },
                            ) {
                                Text(if (selected == ConflictResolution.KEEP_LOCAL) "已保留本机" else "保留本机")
                            }
                        }
                        if (ConflictResolution.USE_INCOMING in conflict.allowedResolutions) {
                            TextButton(
                                enabled = !submitting,
                                onClick = {
                                    onResolve(conflict.key, ConflictResolution.USE_INCOMING)
                                },
                            ) {
                                Text(if (selected == ConflictResolution.USE_INCOMING) "已采用备份" else "采用备份")
                            }
                        }
                        if (ConflictResolution.KEEP_BOTH in conflict.allowedResolutions) {
                            TextButton(
                                enabled = !submitting,
                                onClick = {
                                    onResolve(conflict.key, ConflictResolution.KEEP_BOTH)
                                },
                            ) {
                                Text(if (selected == ConflictResolution.KEEP_BOTH) "已保留双方" else "保留双方")
                            }
                        }
                    }
                }
            }
        }
        if (preview.conflicts.isNotEmpty() && !allConflictsResolved) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "未处理的冲突不能执行合并。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (progressText != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = progressText,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .heightIn(min = 52.dp),
            enabled = !submitting,
            onClick = { replaceConfirmVisible = true },
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("替换当前家庭")
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(min = 52.dp),
            enabled = canMerge,
            onClick = onMerge,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
        ) {
            Text("合并到当前家庭")
        }
    }

    if (replaceConfirmVisible && session != null) {
        WhereDialog(
            onDismissRequest = {
                if (!submitting) {
                    replaceConfirmVisible = false
                }
            },
            title = "确认替换当前家庭",
            confirmText = "确认替换",
            onConfirm = {
                replaceConfirmVisible = false
                onReplace()
            },
            confirmEnabled = !submitting,
            confirmDestructive = true,
            dismissText = "取消",
            onDismiss = { replaceConfirmVisible = false },
            dismissEnabled = !submitting,
        ) {
            Text("替换会用备份覆盖当前家庭数据，本机设置和草稿文字会保留。")
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = lastVerifiedBackupText ?: "尚未成功备份。密码丢失后无法恢复。",
            )
            currentSummary?.let { summary ->
                Text("当前家庭：物品 ${summary.itemCount}，位置 ${summary.locationCount}，照片 ${summary.photoCount}。")
            }
        }
    }
}
