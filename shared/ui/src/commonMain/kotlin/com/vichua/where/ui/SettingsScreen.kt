package com.vichua.where.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.AiAssistanceDisclosure
import com.vichua.where.core.model.BackupFormat
import com.vichua.where.core.model.BackupVerificationResult
import com.vichua.where.core.model.CloudSpeechDisclosure
import com.vichua.where.core.model.ConflictResolution
import com.vichua.where.core.model.ExportDestination
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.core.model.LocalAppPreferences
import com.vichua.where.core.model.RestoreMode
import com.vichua.where.core.model.RestoreSession

/**
 * 设置与数据页：适老偏好、创建加密备份和只读验证。
 *
 * @param preferences 当前设备辅助偏好；加载中或失败时为空。
 * @param loading 是否正在读取偏好。
 * @param submitting 是否正在保存偏好或执行备份。
 * @param errorMessage 可展示的中文错误。
 * @param lastVerifiedBackupText 最近成功备份时间；尚未验证时为空。
 * @param backupProgressText 备份或验证进行中的英文进度，界面展示中文包装。
 * @param verificationResult 只读验证成功后的摘要。
 * @param onBack 返回首页。
 * @param onRetry 重新读取偏好。
 * @param appPreferences 当前设备应用开关；加载失败时为空。
 * @param onElderFriendlyChange 切换适老展示。
 * @param onHighContrastChange 单独切换高对比度。
 * @param onAutoReadConfirmationChange 切换确认保存后是否自动朗读。
 * @param onHapticFeedbackChange 切换主要操作和危险确认是否震动。
 * @param onAiAssistanceChange 在确认披露后开启或关闭 AI 辅助。
 * @param onCloudSpeechChange 在确认披露后开启或关闭云端语音识别。
 * @param onBackupReminderChange 切换尚未成功备份时是否在首页提醒。
 * @param onCreateBackup 使用密码创建加密备份。
 * @param onExportHousehold 使用密码导出完整家庭数据。
 * @param onVerifyBackup 使用密码只读验证备份。
 * @param onDismissVerification 关闭验证摘要。
 * @param householdSummary 当前家庭摘要，供恢复和清除二次确认使用。
 * @param restoreSession 校验通过后的恢复预览会话。
 * @param conflictResolutions 用户已选择的冲突处理方式。
 * @param onRestoreBackup 打开备份并生成恢复预览。
 * @param onDismissRestore 取消恢复会话。
 * @param onResolveConflict 为一条冲突选择处理方式。
 * @param onApplyRestore 在二次确认后执行合并或替换。
 * @param onClearHousehold 二次确认后清除家庭数据。
 */
@Composable
fun SettingsScreen(
    preferences: LocalAccessibilityPreferences?,
    appPreferences: LocalAppPreferences?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    lastVerifiedBackupText: String?,
    backupProgressText: String?,
    verificationResult: BackupVerificationResult?,
    householdSummary: HouseholdDataSummary?,
    restoreSession: RestoreSession?,
    conflictResolutions: Map<String, ConflictResolution>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onElderFriendlyChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onAutoReadConfirmationChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onAiAssistanceChange: (Boolean) -> Unit,
    onCloudSpeechChange: (Boolean) -> Unit,
    onBackupReminderChange: (Boolean) -> Unit,
    onCreateBackup: (String, String) -> Unit,
    onExportHousehold: (String, String, ExportDestination) -> Unit,
    onVerifyBackup: (String) -> Unit,
    onDismissVerification: () -> Unit,
    onRestoreBackup: (String) -> Unit,
    onDismissRestore: () -> Unit,
    onResolveConflict: (String, ConflictResolution) -> Unit,
    onApplyRestore: (RestoreMode) -> Unit,
    onClearHousehold: () -> Unit,
) {
    var aiAssistanceDisclosureVisible by remember { mutableStateOf(false) }
    var cloudSpeechDisclosureVisible by remember { mutableStateOf(false) }
    var createPasswordDialogVisible by remember { mutableStateOf(false) }
    var exportPasswordDialogVisible by remember { mutableStateOf(false) }
    var exportDestinationDialogVisible by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingExportConfirmation by remember { mutableStateOf<String?>(null) }
    var verifyPasswordDialogVisible by remember { mutableStateOf(false) }
    var restorePasswordDialogVisible by remember { mutableStateOf(false) }
    var replaceConfirmVisible by remember { mutableStateOf(false) }
    var clearFirstConfirmVisible by remember { mutableStateOf(false) }
    var clearSecondConfirmVisible by remember { mutableStateOf(false) }

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
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(12.dp),
                imageVector = WhereIcons.Back,
                contentDescription = "返回",
                tint = WherePrimaryTextColor,
            )
            Text(
                text = "设置与数据",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (loading && preferences == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 48.dp),
            )
            return@Column
        }
        if (errorMessage != null && preferences == null) {
            Text(
                modifier = Modifier.padding(top = 32.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
            return@Column
        }

        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (backupProgressText != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = backupProgressText,
                color = WherePrimaryColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsSectionTitle("常用设置")
        SettingsSwitchRow(
            title = "AI 辅助",
            description = "仅在你主动选择识别后才会处理这次选中的照片或文字，不会后台自动上传",
            checked = appPreferences?.canUseAiAssistance == true,
            enabled = appPreferences != null,
            onCheckedChange = { enabled ->
                if (enabled) {
                    aiAssistanceDisclosureVisible = true
                } else {
                    onAiAssistanceChange(false)
                }
            },
        )
        SettingsSwitchRow(
            title = "云端语音识别",
            description = "仅在你主动说话后才会把这次录音交给系统识别，不会后台持续听",
            checked = appPreferences?.canUseCloudSpeech == true,
            enabled = appPreferences != null,
            onCheckedChange = { enabled ->
                if (enabled) {
                    cloudSpeechDisclosureVisible = true
                } else {
                    onCloudSpeechChange(false)
                }
            },
        )

        SettingsSectionTitle("辅助能力")
        SettingsSwitchRow(
            title = "适老模式",
            description = "首页改为两大入口，并切换录入、搜索、详情和更新位置的适老展示",
            checked = preferences?.elderFriendly == true,
            enabled = preferences != null,
            onCheckedChange = onElderFriendlyChange,
        )
        SettingsSwitchRow(
            title = "高对比度",
            description = "独立设备偏好；启用适老模式时默认开启，可单独关闭",
            checked = preferences?.highContrastEnabled == true,
            enabled = preferences != null,
            onCheckedChange = onHighContrastChange,
        )
        SettingsSwitchRow(
            title = "自动朗读确认结果",
            description = "确认保存物品后朗读名称、位置和更新时间；关闭后仍可在详情页主动朗读",
            checked = preferences?.autoReadConfirmationEnabled == true,
            enabled = preferences != null,
            onCheckedChange = onAutoReadConfirmationChange,
        )
        SettingsSwitchRow(
            title = "触觉反馈",
            description = "主要按钮按下时轻震，删除和清除等危险确认更明显；设备不支持时自动忽略",
            checked = preferences?.hapticFeedbackEnabled == true,
            enabled = preferences != null,
            onCheckedChange = onHapticFeedbackChange,
        )

        SettingsSectionTitle("家庭与位置")
        PendingSettingsRow("位置管理", "请从首页底部进入，设置页不重复提供入口")

        SettingsSectionTitle("数据与备份")
        SettingsSwitchRow(
            title = "备份提醒",
            description = "还没有成功备份时，在首页提醒你到设置里创建加密备份",
            checked = appPreferences?.backupReminderEnabled == true,
            enabled = appPreferences != null,
            onCheckedChange = onBackupReminderChange,
        )
        Text(
            modifier = Modifier.padding(bottom = 10.dp),
            text = lastVerifiedBackupText ?: "尚未成功备份。密码丢失后无法恢复。",
            color = if (appPreferences?.backupReminderEnabled == true &&
                lastVerifiedBackupText == null
            ) {
                WherePrimaryColor
            } else {
                WhereSecondaryTextColor
            },
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsActionRow(
            title = "创建加密备份",
            description = "导出过滤后的家庭数据和物品原图，不包含草稿和本机设置",
            enabled = !submitting,
            onClick = {
                createPasswordDialogVisible = true
            },
        )
        SettingsActionRow(
            title = "验证备份",
            description = "只读检查密码、格式和摘要，不覆盖当前家庭数据",
            enabled = !submitting,
            onClick = {
                verifyPasswordDialogVisible = true
            },
        )
        SettingsActionRow(
            title = "恢复备份",
            description = "先预览新增、更新、删除和冲突，再选择合并或替换",
            enabled = !submitting,
            onClick = {
                restorePasswordDialogVisible = true
            },
        )
        SettingsActionRow(
            title = "导出完整家庭数据",
            description = "使用与备份相同的加密数据包，由你选择保存或分享位置",
            enabled = !submitting,
            onClick = {
                exportPasswordDialogVisible = true
            },
        )

        SettingsSectionTitle("危险操作")
        SettingsActionRow(
            title = "清除家庭数据",
            description = "删除家庭物品、位置和照片，本机设置和草稿文字会保留",
            enabled = !submitting,
            onClick = {
                clearFirstConfirmVisible = true
            },
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (aiAssistanceDisclosureVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    aiAssistanceDisclosureVisible = false
                }
            },
            title = { Text("开启 AI 辅助") },
            text = {
                Column {
                    Text("数据类型：${AiAssistanceDisclosure.DATA_TYPE}")
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "服务供应商：${AiAssistanceDisclosure.VENDOR}",
                    )
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "处理用途：${AiAssistanceDisclosure.PURPOSE}",
                    )
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = "不会后台自动上传。没有建议或你不采用时，本地手填、查找和备份不受影响。",
                        color = WhereSecondaryTextColor,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        aiAssistanceDisclosureVisible = false
                        onAiAssistanceChange(true)
                    },
                ) {
                    Text("确认开启")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        aiAssistanceDisclosureVisible = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
    if (cloudSpeechDisclosureVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    cloudSpeechDisclosureVisible = false
                }
            },
            title = { Text("开启云端语音识别") },
            text = {
                Column {
                    Text("数据类型：${CloudSpeechDisclosure.DATA_TYPE}")
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "服务供应商：${CloudSpeechDisclosure.VENDOR}",
                    )
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "处理用途：${CloudSpeechDisclosure.PURPOSE}",
                    )
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = "不会后台持续听，也不会保存原始录音。关闭后本地查找和手填不受影响。",
                        color = WhereSecondaryTextColor,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        cloudSpeechDisclosureVisible = false
                        onCloudSpeechChange(true)
                    },
                ) {
                    Text("确认开启")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        cloudSpeechDisclosureVisible = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
    if (createPasswordDialogVisible) {
        BackupPasswordDialog(
            title = "创建加密备份",
            confirmLabel = "创建",
            requireConfirmation = true,
            enabled = !submitting,
            onDismiss = {
                if (!submitting) {
                    createPasswordDialogVisible = false
                }
            },
            onConfirm = { password, confirmation ->
                createPasswordDialogVisible = false
                onCreateBackup(password, confirmation)
            },
        )
    }
    if (exportPasswordDialogVisible) {
        BackupPasswordDialog(
            title = "导出完整家庭数据",
            confirmLabel = "继续",
            requireConfirmation = true,
            enabled = !submitting,
            onDismiss = {
                if (!submitting) {
                    exportPasswordDialogVisible = false
                }
            },
            onConfirm = { password, confirmation ->
                pendingExportPassword = password
                pendingExportConfirmation = confirmation
                exportPasswordDialogVisible = false
                exportDestinationDialogVisible = true
            },
        )
    }
    if (exportDestinationDialogVisible) {
        ExportDestinationDialog(
            enabled = !submitting,
            onDismiss = {
                if (!submitting) {
                    pendingExportPassword = null
                    pendingExportConfirmation = null
                    exportDestinationDialogVisible = false
                }
            },
            onSelect = { destination ->
                val password = pendingExportPassword
                val confirmation = pendingExportConfirmation
                pendingExportPassword = null
                pendingExportConfirmation = null
                exportDestinationDialogVisible = false
                if (password != null && confirmation != null) {
                    onExportHousehold(password, confirmation, destination)
                }
            },
        )
    }
    if (verifyPasswordDialogVisible) {
        BackupPasswordDialog(
            title = "验证备份",
            confirmLabel = "验证",
            requireConfirmation = false,
            enabled = !submitting,
            onDismiss = {
                if (!submitting) {
                    verifyPasswordDialogVisible = false
                }
            },
            onConfirm = { password, _ ->
                verifyPasswordDialogVisible = false
                onVerifyBackup(password)
            },
        )
    }
    if (verificationResult != null) {
        BackupVerificationDialog(
            result = verificationResult,
            onDismiss = onDismissVerification,
        )
    }
    if (restorePasswordDialogVisible) {
        BackupPasswordDialog(
            title = "恢复备份",
            confirmLabel = "继续",
            requireConfirmation = false,
            enabled = !submitting,
            onDismiss = {
                if (!submitting) {
                    restorePasswordDialogVisible = false
                }
            },
            onConfirm = { password, _ ->
                restorePasswordDialogVisible = false
                onRestoreBackup(password)
            },
        )
    }
    if (restoreSession != null) {
        RestorePreviewDialog(
            session = restoreSession,
            lastVerifiedBackupText = lastVerifiedBackupText,
            currentSummary = householdSummary,
            resolutions = conflictResolutions,
            enabled = !submitting,
            onDismiss = onDismissRestore,
            onResolve = onResolveConflict,
            onMerge = {
                onApplyRestore(RestoreMode.MERGE)
            },
            onReplace = {
                replaceConfirmVisible = true
            },
        )
    }
    if (replaceConfirmVisible && restoreSession != null) {
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    replaceConfirmVisible = false
                }
            },
            title = { Text("确认替换当前家庭") },
            text = {
                Column {
                    Text("替换会用备份覆盖当前家庭数据，本机设置和草稿文字会保留。")
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = lastVerifiedBackupText ?: "尚未成功备份。密码丢失后无法恢复。",
                    )
                    householdSummary?.let { summary ->
                        Text("当前家庭：物品 ${summary.itemCount}，位置 ${summary.locationCount}，照片 ${summary.photoCount}。")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        replaceConfirmVisible = false
                        onApplyRestore(RestoreMode.REPLACE)
                    },
                ) {
                    Text("确认替换")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { replaceConfirmVisible = false },
                ) {
                    Text("取消")
                }
            },
        )
    }
    if (clearFirstConfirmVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    clearFirstConfirmVisible = false
                }
            },
            title = { Text("清除家庭数据") },
            text = {
                Column {
                    Text("将删除当前家庭的物品、位置、照片和变更记录。")
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = lastVerifiedBackupText ?: "尚未成功备份。清除后无法从本机恢复。",
                    )
                    householdSummary?.let { summary ->
                        Text("当前家庭：物品 ${summary.itemCount}，位置 ${summary.locationCount}，照片 ${summary.photoCount}。")
                    }
                    Text("适老设置、最近查找和草稿文字会保留。")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        clearFirstConfirmVisible = false
                        clearSecondConfirmVisible = true
                    },
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { clearFirstConfirmVisible = false },
                ) {
                    Text("取消")
                }
            },
        )
    }
    if (clearSecondConfirmVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    clearSecondConfirmVisible = false
                }
            },
            title = { Text("再次确认清除") },
            text = { Text("此操作不能仅靠一次误触完成。确认后将返回家庭初始化页。") },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        clearSecondConfirmVisible = false
                        onClearHousehold()
                    },
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { clearSecondConfirmVisible = false },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
        text = text,
        color = WherePrimaryTextColor,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WherePrimaryTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = description,
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SettingsToggle(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

/**
 * 按原型绘制开/关：同一颗白圆点，只换轨道颜色和位置。
 *
 * 不用 Material3 Switch，避免关闭态缩成描边胶囊、打开态变成对勾。
 * 保存中不改 enabled，避免三个开关一起闪成禁用色。
 * 点击水波纹关掉，否则会画在 48dp 方框上闪出正方形。
 */
@Composable
private fun SettingsToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            WherePrimaryColor
        } else {
            WhereSwitchUncheckedTrackColor
        },
        label = "settingsToggleTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            SettingsToggleWidth - SettingsToggleThumbSize - SettingsToggleThumbPadding
        } else {
            SettingsToggleThumbPadding
        },
        label = "settingsToggleThumb",
    )
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = SettingsToggleWidth, height = SettingsToggleHeight)
                .clip(RoundedCornerShape(SettingsToggleHeight / 2))
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(SettingsToggleThumbSize)
                    .clip(CircleShape)
                    .background(WhereSurfaceColor),
            )
        }
    }
}

private val SettingsToggleWidth = 52.dp
private val SettingsToggleHeight = 32.dp
private val SettingsToggleThumbSize = 24.dp
private val SettingsToggleThumbPadding = 4.dp

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                color = WherePrimaryTextColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = description,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PendingSettingsRow(
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                color = WherePrimaryTextColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = description,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val canConfirm = password.length >= BackupFormat.MIN_PASSWORD_LENGTH &&
        (!requireConfirmation || password == confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "密码至少 ${BackupFormat.MIN_PASSWORD_LENGTH} 位。密码不会和备份保存在一起，丢失后无法恢复。",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    value = password,
                    onValueChange = { value ->
                        password = value
                    },
                    enabled = enabled,
                    label = { Text("备份密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        value = confirmation,
                        onValueChange = { value ->
                            confirmation = value
                        },
                        enabled = enabled,
                        label = { Text("再次输入密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled && canConfirm,
                onClick = {
                    onConfirm(password, confirmation)
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                enabled = enabled,
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

/**
 * 导出入口只在这里区分保存和分享，数据包格式与加密备份相同。
 */
@Composable
private fun ExportDestinationDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ExportDestination) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (enabled) {
                onDismiss()
            }
        },
        title = { Text("选择导出位置") },
        text = {
            Text(
                text = "保存到文件会打开系统文件选择器。分享只会送出已经加密的数据包，不会附带家庭数据库。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = {
                    onSelect(ExportDestination.SAVE_DOCUMENT)
                },
            ) {
                Text("保存到文件")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = enabled,
                    onClick = {
                        onSelect(ExportDestination.SHARE)
                    },
                ) {
                    Text("分享")
                }
                TextButton(
                    enabled = enabled,
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun BackupVerificationDialog(
    result: BackupVerificationResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备份验证通过") },
        text = {
            Column {
                Text("格式版本 ${result.manifest.formatVersion}")
                Text("加密：${result.manifest.encryptionAlgorithm}")
                Text("物品 ${result.itemCount} 件")
                Text("位置 ${result.locationCount} 个")
                Text("物品照片 ${result.itemPhotoCount} 张")
                Text("已打包原图 ${result.includedMediaCount} 张")
                Text("文件大小 ${result.packageSizeBytes} 字节")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
    )
}

@Composable
private fun RestorePreviewDialog(
    session: RestoreSession,
    lastVerifiedBackupText: String?,
    currentSummary: HouseholdDataSummary?,
    resolutions: Map<String, ConflictResolution>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onResolve: (String, ConflictResolution) -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    val preview = session.preview
    val allConflictsResolved = preview.conflicts.all { conflict ->
        resolutions[conflict.key] != null
    }
    val canMerge = enabled && !preview.differentHousehold && allConflictsResolved
    AlertDialog(
        onDismissRequest = {
            if (enabled) {
                onDismiss()
            }
        },
        title = { Text("恢复预览") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("格式版本 ${preview.manifest.formatVersion}")
                Text("加密：${preview.manifest.encryptionAlgorithm}")
                Text("物品 ${preview.itemCount} 件，位置 ${preview.locationCount} 个")
                Text("物品照片 ${preview.itemPhotoCount} 张")
                Text("新增 ${preview.addedCount}，更新 ${preview.updatedCount}，删除 ${preview.deletedCount}，冲突 ${preview.conflictCount}")
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = lastVerifiedBackupText ?: "尚未成功备份。密码丢失后无法恢复。",
                )
                currentSummary?.let { summary ->
                    Text("当前家庭：物品 ${summary.itemCount}，位置 ${summary.locationCount}，照片 ${summary.photoCount}。")
                }
                if (preview.differentHousehold) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "备份来自另一个家庭，只能替换，不能合并。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                preview.conflicts.forEach { conflict ->
                    val selected = resolutions[conflict.key]
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "${conflict.entityLabel} · ${conflict.conflictFields.joinToString("、")}",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("当前：${conflict.localValue}")
                        Text("备份：${conflict.incomingValue}")
                        Row(modifier = Modifier.padding(top = 6.dp)) {
                            if (ConflictResolution.KEEP_LOCAL in conflict.allowedResolutions) {
                                TextButton(
                                    enabled = enabled,
                                    onClick = {
                                        onResolve(conflict.key, ConflictResolution.KEEP_LOCAL)
                                    },
                                ) {
                                    Text(if (selected == ConflictResolution.KEEP_LOCAL) "已保留本机" else "保留本机")
                                }
                            }
                            if (ConflictResolution.USE_INCOMING in conflict.allowedResolutions) {
                                TextButton(
                                    enabled = enabled,
                                    onClick = {
                                        onResolve(conflict.key, ConflictResolution.USE_INCOMING)
                                    },
                                ) {
                                    Text(if (selected == ConflictResolution.USE_INCOMING) "已采用备份" else "采用备份")
                                }
                            }
                            if (ConflictResolution.KEEP_BOTH in conflict.allowedResolutions) {
                                TextButton(
                                    enabled = enabled,
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
                if (preview.conflicts.isNotEmpty() && !allConflictsResolved) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "未处理的冲突不能执行合并。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canMerge,
                onClick = onMerge,
            ) {
                Text("合并")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = enabled,
                    onClick = onReplace,
                ) {
                    Text("替换")
                }
                TextButton(
                    enabled = enabled,
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
            }
        },
    )
}
