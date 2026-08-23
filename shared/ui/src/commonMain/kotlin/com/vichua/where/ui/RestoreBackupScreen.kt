package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.BackupFormat
import com.vichua.where.core.model.ConflictResolution
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.RestoreMode
import com.vichua.where.core.model.RestoreSession
import com.vichua.where.core.platform.ManagedBackupFile

/** 原型冲突卡背景。 */
private val RestoreWarningFill = Color(0xFFF6E8C8)

/** 原型冲突卡描边。 */
private val RestoreWarningStroke = Color(0xFFD8B56B)

/** 原型冲突卡标题色。 */
private val RestoreWarningTitle = Color(0xFF8A651F)

/** 原型冲突卡正文色。 */
private val RestoreWarningBody = Color(0xFF6C5120)

/**
 * 独立恢复备份页：先选文件和密码，再按原型核对摘要、冲突和恢复方式。
 *
 * 格式版本只作为摘要里的兼容标记，不单独做成用户需要理解的字段。
 */
@Composable
fun RestoreBackupScreen(
    session: RestoreSession?,
    formattedBackupCreatedAt: String?,
    lastVerifiedBackupText: String?,
    currentSummary: HouseholdDataSummary?,
    resolutions: Map<String, ConflictResolution>,
    submitting: Boolean,
    progressText: String?,
    errorMessage: String?,
    managedBackups: List<ManagedBackupFile>,
    managedBackupsLoading: Boolean,
    formatBackupTime: (Long) -> String,
    onBack: () -> Unit,
    onPickAndPreview: (String, String?) -> Unit,
    onResolve: (String, ConflictResolution) -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    var replaceConfirmVisible by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var selectedBackupUri by remember { mutableStateOf<String?>(null) }
    var importFromElsewhere by remember { mutableStateOf(false) }
    val preview = session?.preview
    var selectedMode by remember(preview?.differentHousehold) {
        mutableStateOf(
            if (preview?.differentHousehold == true) {
                RestoreMode.REPLACE
            } else {
                RestoreMode.MERGE
            },
        )
    }
    val allConflictsResolved = preview?.conflicts?.all { conflict ->
        resolutions[conflict.key] != null
    } == true
    val canMerge = !submitting &&
        preview != null &&
        !preview.differentHousehold &&
        allConflictsResolved
    val canConfirm = !submitting &&
        preview != null &&
        when (selectedMode) {
            RestoreMode.MERGE -> canMerge
            RestoreMode.REPLACE -> true
        }

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
        if (preview == null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "先选出备份，再输入密码。密码对错要打开文件后才能判断。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            ManagedBackupList(
                backups = managedBackups,
                loading = managedBackupsLoading,
                selectedUri = if (importFromElsewhere) null else selectedBackupUri,
                formatTime = formatBackupTime,
                onSelect = { backup ->
                    selectedBackupUri = backup.opaqueDocumentUri
                    importFromElsewhere = false
                },
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 48.dp),
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = WhereSurfaceColor),
                onClick = {
                    selectedBackupUri = null
                    importFromElsewhere = true
                },
            ) {
                Text(
                    text = if (importFromElsewhere) "将从其他位置导入" else "从其他位置导入",
                    color = WherePrimaryColor,
                )
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = restorePassword,
                onValueChange = { value ->
                    restorePassword = value
                },
                enabled = !submitting,
                label = { Text("备份密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp),
                enabled = !submitting &&
                    restorePassword.length >= BackupFormat.MIN_PASSWORD_LENGTH &&
                    (selectedBackupUri != null || importFromElsewhere),
                colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
                onClick = {
                    onPickAndPreview(
                        restorePassword,
                        if (importFromElsewhere) null else selectedBackupUri,
                    )
                },
            ) {
                Text(if (importFromElsewhere) "导入并预览" else "预览选中备份")
            }
            if (submitting && progressText != null) {
                WorkingProgressCard(
                    modifier = Modifier.padding(top = 12.dp),
                    title = progressText,
                    message = "解密、核对和写入需要一些时间，请不要锁定屏幕。",
                )
            } else {
                progressText?.let { text ->
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = text,
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            errorMessage?.let { text ->
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = text,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@Column
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "先核对备份摘要和冲突，再决定合并或替换。未执行前不会改当前家庭。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        val backupTitle = buildString {
            append(session.envelope.snapshot.household.name)
            if (!formattedBackupCreatedAt.isNullOrBlank()) {
                append(" · ")
                append(formattedBackupCreatedAt)
            }
        }
        val photoCount = preview.itemPhotoCount + preview.locationPhotoCount
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = WhereSelectedContainerColor,
            border = BorderStroke(1.dp, WherePrimaryColor),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = backupTitle,
                        color = WherePrimaryTextColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WherePrimaryColor,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            text = "校验通过",
                            color = WhereSurfaceColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "格式 v${preview.manifest.formatVersion} · " +
                        "${visiblePackageSize(preview.packageSizeBytes)} · 加密数据包",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${preview.itemCount} 件物品 · ${photoCount} 张照片 · ${preview.locationCount} 个位置",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            text = "恢复预览",
            color = WhereSecondaryTextColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RestoreStatCard(
                modifier = Modifier.weight(1f),
                value = preview.addedCount.toString(),
                label = "新增",
                emphasized = true,
            )
            RestoreStatCard(
                modifier = Modifier.weight(1f),
                value = preview.updatedCount.toString(),
                label = "更新",
                emphasized = false,
            )
            RestoreStatCard(
                modifier = Modifier.weight(1f),
                value = preview.deletedCount.toString(),
                label = "删除",
                emphasized = false,
            )
            RestoreStatCard(
                modifier = Modifier.weight(1f),
                value = preview.conflictCount.toString(),
                label = "冲突",
                emphasized = false,
                warning = preview.conflictCount > 0,
            )
        }
        if (preview.differentHousehold) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "备份来自另一个家庭，只能替换，不能合并。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        preview.conflicts.forEach { conflict ->
            val selected = resolutions[conflict.key]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(15.dp),
                color = RestoreWarningFill,
                border = BorderStroke(1.dp, RestoreWarningStroke),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "发现冲突：${conflict.entityLabel}",
                        color = RestoreWarningTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "冲突字段：${conflict.conflictFields.joinToString("、")}\n" +
                            "本机：${conflict.localValue}\n" +
                            "备份：${conflict.incomingValue}",
                        color = RestoreWarningBody,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (ConflictResolution.KEEP_LOCAL in conflict.allowedResolutions) {
                            RestoreConflictChoice(
                                modifier = Modifier.weight(1f),
                                label = if (selected == ConflictResolution.KEEP_LOCAL) {
                                    "已保留本机"
                                } else {
                                    "保留本机"
                                },
                                selected = selected == ConflictResolution.KEEP_LOCAL,
                                enabled = !submitting,
                                onClick = {
                                    onResolve(conflict.key, ConflictResolution.KEEP_LOCAL)
                                },
                            )
                        }
                        if (ConflictResolution.USE_INCOMING in conflict.allowedResolutions) {
                            RestoreConflictChoice(
                                modifier = Modifier.weight(1f),
                                label = if (selected == ConflictResolution.USE_INCOMING) {
                                    "已采用备份"
                                } else {
                                    "采用备份"
                                },
                                selected = selected == ConflictResolution.USE_INCOMING,
                                enabled = !submitting,
                                onClick = {
                                    onResolve(conflict.key, ConflictResolution.USE_INCOMING)
                                },
                            )
                        }
                        if (ConflictResolution.KEEP_BOTH in conflict.allowedResolutions) {
                            RestoreConflictChoice(
                                modifier = Modifier.weight(1f),
                                label = if (selected == ConflictResolution.KEEP_BOTH) {
                                    "已保留双方"
                                } else {
                                    "保留双方"
                                },
                                selected = selected == ConflictResolution.KEEP_BOTH,
                                enabled = !submitting,
                                onClick = {
                                    onResolve(conflict.key, ConflictResolution.KEEP_BOTH)
                                },
                            )
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
        Text(
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            text = "恢复方式",
            color = WhereSecondaryTextColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RestoreModeCard(
                modifier = Modifier.weight(1f),
                title = "合并",
                description = "保留当前数据",
                icon = WhereIcons.Confirm,
                selected = selectedMode == RestoreMode.MERGE,
                enabled = !submitting && !preview.differentHousehold,
                onClick = {
                    selectedMode = RestoreMode.MERGE
                },
            )
            RestoreModeCard(
                modifier = Modifier.weight(1f),
                title = "替换",
                description = "覆盖当前家庭",
                icon = WhereIcons.Repeat,
                selected = selectedMode == RestoreMode.REPLACE,
                enabled = !submitting,
                onClick = {
                    selectedMode = RestoreMode.REPLACE
                },
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(13.dp),
            color = WhereSurfaceColor,
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = WhereIcons.Confirm,
                    contentDescription = null,
                    tint = WherePrimaryColor,
                )
                Text(
                    text = "恢复前将自动备份当前数据，失败时自动回滚。",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (submitting && progressText != null) {
            WorkingProgressCard(
                modifier = Modifier.padding(top = 12.dp),
                title = progressText,
                message = "解密、核对和写入需要一些时间，请不要锁定屏幕。",
            )
        } else if (progressText != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = progressText,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 52.dp),
            enabled = canConfirm,
            onClick = {
                when (selectedMode) {
                    RestoreMode.MERGE -> onMerge()
                    RestoreMode.REPLACE -> replaceConfirmVisible = true
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
        ) {
            Text("确认恢复")
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
    if (submitting && progressText != null) {
        WorkingProgressDialog(
            title = progressText,
            message = "解密、核对和写入需要一些时间，请不要锁定屏幕。",
        )
    }
}

/**
 * 恢复预览四个变化数量卡。
 */
@Composable
private fun RestoreStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    emphasized: Boolean,
    warning: Boolean = false,
) {
    val fill = when {
        warning -> RestoreWarningFill
        emphasized -> WhereSelectedContainerColor
        else -> Color(0xFFEEF1F4)
    }
    val content = when {
        warning -> RestoreWarningTitle
        emphasized -> WherePrimaryColor
        else -> WherePrimaryTextColor
    }
    Surface(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(14.dp),
        color = fill,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = content,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = label,
                color = content,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 冲突处理方式，选中后用实心底强调已决定。
 */
@Composable
private fun RestoreConflictChoice(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) WhereSurfaceColor else Color(0x80FFFFFF),
        border = BorderStroke(1.dp, if (selected) RestoreWarningTitle else RestoreWarningStroke),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            text = label,
            color = RestoreWarningBody,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * 合并或替换二选一，对齐原型的恢复方式卡。
 */
@Composable
private fun RestoreModeCard(
    modifier: Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(96.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) WherePrimaryColor else WhereOutlineColor,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) WherePrimaryColor else WhereSecondaryTextColor,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = title,
                color = if (selected) WherePrimaryColor else WherePrimaryTextColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
