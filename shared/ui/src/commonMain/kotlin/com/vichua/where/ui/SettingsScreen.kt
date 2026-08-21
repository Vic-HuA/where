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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.vichua.where.core.model.AiProviderCredentials
import com.vichua.where.core.model.BackupFormat
import com.vichua.where.core.model.BackupVerificationResult
import com.vichua.where.core.model.CloudSpeechDisclosure
import com.vichua.where.core.model.ExportDestination
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.core.model.LocalAppPreferences

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
 * @param onHomeClick 切换到首页根入口。
 * @param onLocationClick 切换到位置根入口。
 * @param onRetry 重新读取偏好。
 * @param appPreferences 当前设备应用开关；加载失败时为空。
 * @param onElderFriendlyChange 切换适老展示。
 * @param onHighContrastChange 单独切换高对比度。
 * @param onAutoReadConfirmationChange 切换确认保存后是否自动朗读。
 * @param onHapticFeedbackChange 切换主要操作和危险确认是否震动。
 * @param aiProviderCredentials 本机 AI 接口凭证；未加载时为空。
 * @param onAiAssistanceChange 在确认披露后开启或关闭 AI 辅助。
 * @param onSaveAiProviderCredentials 保存本机 Key 和可选接口地址。
 * @param onCloudSpeechChange 在确认披露后开启或关闭云端语音识别。
 * @param onBackupReminderChange 切换尚未成功备份时是否在首页提醒。
 * @param onDiagnosticLoggingChange 切换是否写入不含敏感内容的本机诊断事件。
 * @param onCreateBackup 使用密码创建加密备份。
 * @param onExportHousehold 使用密码导出完整家庭数据。
 * @param onVerifyBackup 使用密码只读验证备份。
 * @param onDismissVerification 关闭验证摘要。
 * @param householdSummary 当前家庭摘要，供清除二次确认使用。
 * @param onRestoreBackup 进入独立恢复备份页，在该页选择文件并输入密码。
 * @param onClearHousehold 二次确认后清除家庭数据。
 */
@Composable
fun SettingsScreen(
    preferences: LocalAccessibilityPreferences?,
    appPreferences: LocalAppPreferences?,
    aiProviderCredentials: AiProviderCredentials?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    lastVerifiedBackupText: String?,
    backupProgressText: String?,
    verificationResult: BackupVerificationResult?,
    householdSummary: HouseholdDataSummary?,
    onHomeClick: () -> Unit,
    onLocationClick: () -> Unit,
    onRetry: () -> Unit,
    onElderFriendlyChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onAutoReadConfirmationChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onAiAssistanceChange: (Boolean) -> Unit,
    onSaveAiProviderCredentials: (String, String) -> Unit,
    onCloudSpeechChange: (Boolean) -> Unit,
    onBackupReminderChange: (Boolean) -> Unit,
    onDiagnosticLoggingChange: (Boolean) -> Unit,
    onCreateBackup: (String, String) -> Unit,
    onExportHousehold: (String, String, ExportDestination) -> Unit,
    onVerifyBackup: (String) -> Unit,
    onDismissVerification: () -> Unit,
    onRestoreBackup: () -> Unit,
    onClearHousehold: () -> Unit,
) {
    var aiProviderDialogVisible by remember { mutableStateOf(false) }
    var aiAssistanceDisclosureVisible by remember { mutableStateOf(false) }
    var cloudSpeechDisclosureVisible by remember { mutableStateOf(false) }
    var createPasswordDialogVisible by remember { mutableStateOf(false) }
    var exportPasswordDialogVisible by remember { mutableStateOf(false) }
    var exportDestinationDialogVisible by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingExportConfirmation by remember { mutableStateOf<String?>(null) }
    var verifyPasswordDialogVisible by remember { mutableStateOf(false) }
    var clearFirstConfirmVisible by remember { mutableStateOf(false) }
    var clearSecondConfirmVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WhereBackgroundColor,
        bottomBar = {
            WhereBottomNavigation(
                selected = WhereRootTab.SETTINGS,
                onHomeClick = onHomeClick,
                onLocationClick = onLocationClick,
                onSettingsClick = {},
            )
        },
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = "设置与数据",
            color = WherePrimaryTextColor,
            style = MaterialTheme.typography.titleLarge,
        )

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
        SettingsActionRow(
            title = "AI 接口",
            description = if (aiProviderCredentials?.isConfigured == true) {
                "已保存 Key，只存在本机，不进入备份"
            } else {
                "填写后才能识别；不填可继续手填"
            },
            enabled = appPreferences != null && !submitting,
            onClick = {
                aiProviderDialogVisible = true
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

        SettingsSectionTitle("数据与备份")
        SettingsSwitchRow(
            title = "备份提醒",
            description = "还没有成功备份时，在首页提醒你到设置里创建加密备份",
            checked = appPreferences?.backupReminderEnabled == true,
            enabled = appPreferences != null,
            onCheckedChange = onBackupReminderChange,
        )
        SettingsSwitchRow(
            title = "诊断日志",
            description = "只记录不含物品名称、位置和查询原文的操作结果，用于排查问题",
            checked = appPreferences?.diagnosticLoggingEnabled == true,
            enabled = appPreferences != null,
            onCheckedChange = onDiagnosticLoggingChange,
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
            onClick = onRestoreBackup,
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

    if (aiProviderDialogVisible) {
        AiProviderCredentialsDialog(
            initialCredentials = aiProviderCredentials,
            enabled = !submitting,
            onDismiss = {
                if (!submitting) {
                    aiProviderDialogVisible = false
                }
            },
            onSave = { apiKey, baseUrl ->
                aiProviderDialogVisible = false
                onSaveAiProviderCredentials(apiKey, baseUrl)
            },
        )
    }
    if (aiAssistanceDisclosureVisible) {
        WhereDialog(
            onDismissRequest = {
                if (!submitting) {
                    aiAssistanceDisclosureVisible = false
                }
            },
            title = "开启 AI 辅助",
            confirmText = "确认开启",
            onConfirm = {
                aiAssistanceDisclosureVisible = false
                onAiAssistanceChange(true)
            },
            confirmEnabled = !submitting,
            dismissText = "取消",
            onDismiss = {
                aiAssistanceDisclosureVisible = false
            },
            dismissEnabled = !submitting,
        ) {
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
                        text = "不会后台自动上传。没有 Key、没有建议或你不采用时，本地手填、查找和备份不受影响。",
                        color = WhereSecondaryTextColor,
                    )
        }
    }
    if (cloudSpeechDisclosureVisible) {
        WhereDialog(
            onDismissRequest = {
                if (!submitting) {
                    cloudSpeechDisclosureVisible = false
                }
            },
            title = "开启云端语音识别",
            confirmText = "确认开启",
            onConfirm = {
                cloudSpeechDisclosureVisible = false
                onCloudSpeechChange(true)
            },
            confirmEnabled = !submitting,
            dismissText = "取消",
            onDismiss = {
                cloudSpeechDisclosureVisible = false
            },
            dismissEnabled = !submitting,
        ) {
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
            extraHint = "密码要和备份文件一起校验，下一步会选择文件。",
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
    if (clearFirstConfirmVisible) {
        WhereDialog(
            onDismissRequest = {
                if (!submitting) {
                    clearFirstConfirmVisible = false
                }
            },
            title = "清除家庭数据",
            confirmText = "继续",
            onConfirm = {
                clearFirstConfirmVisible = false
                clearSecondConfirmVisible = true
            },
            confirmEnabled = !submitting,
            confirmDestructive = true,
            dismissText = "取消",
            onDismiss = { clearFirstConfirmVisible = false },
            dismissEnabled = !submitting,
        ) {
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
    }
    if (clearSecondConfirmVisible) {
        WhereDialog(
            onDismissRequest = {
                if (!submitting) {
                    clearSecondConfirmVisible = false
                }
            },
            title = "再次确认清除",
            confirmText = "确认清除",
            onConfirm = {
                clearSecondConfirmVisible = false
                onClearHousehold()
            },
            confirmEnabled = !submitting,
            confirmDestructive = true,
            dismissText = "取消",
            onDismiss = { clearSecondConfirmVisible = false },
            dismissEnabled = !submitting,
        ) {
            Text("此操作不能仅靠一次误触完成。确认后将返回家庭初始化页。")
        }
    }
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

/**
 * 在弹窗里填写 Key，避免设置首页露出密钥输入框。
 */
@Composable
private fun AiProviderCredentialsDialog(
    initialCredentials: AiProviderCredentials?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var apiKey by remember {
        mutableStateOf(initialCredentials?.apiKey.orEmpty())
    }
    var baseUrl by remember {
        mutableStateOf(initialCredentials?.baseUrl ?: AiProviderCredentials.DEFAULT_BASE_URL)
    }
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "AI 接口",
        confirmText = "保存",
        onConfirm = {
            onSave(apiKey, baseUrl)
        },
        confirmEnabled = enabled,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = enabled,
    ) {
        Text(
            text = "可填 OpenAI 或兼容接口。Key 只存在本机，不进入备份。地址必须是 https。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            value = apiKey,
            onValueChange = { value ->
                apiKey = value
            },
            enabled = enabled,
            label = { Text("接口 Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            value = baseUrl,
            onValueChange = { value ->
                baseUrl = value
            },
            enabled = enabled,
            label = { Text("接口地址（可选）") },
            singleLine = true,
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    extraHint: String? = null,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val canConfirm = password.length >= BackupFormat.MIN_PASSWORD_LENGTH &&
        (!requireConfirmation || password == confirmation)

    WhereDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = confirmLabel,
        onConfirm = {
            onConfirm(password, confirmation)
        },
        confirmEnabled = enabled && canConfirm,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = enabled,
    ) {
                Text(
                    text = buildString {
                        append("密码至少 ${BackupFormat.MIN_PASSWORD_LENGTH} 位。密码不会和备份保存在一起，丢失后无法恢复。")
                        if (extraHint != null) {
                            append(" ")
                            append(extraHint)
                        }
                    },
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
    WhereDialog(
        onDismissRequest = {
            if (enabled) {
                onDismiss()
            }
        },
        title = "选择导出位置",
        confirmText = "保存到文件",
        onConfirm = {
            onSelect(ExportDestination.SAVE_DOCUMENT)
        },
        confirmEnabled = enabled,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = enabled,
        neutralText = "分享",
        onNeutral = {
            onSelect(ExportDestination.SHARE)
        },
        neutralEnabled = enabled,
    ) {
            Text(
                text = "保存到文件会打开系统文件选择器。分享只会送出已经加密的数据包，不会附带家庭数据库。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
private fun BackupVerificationDialog(
    result: BackupVerificationResult,
    onDismiss: () -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "备份验证通过",
        confirmText = "确定",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
                Text("格式 v${result.manifest.formatVersion} · 加密数据包")
                Text("物品 ${result.itemCount} 件，位置 ${result.locationCount} 个")
                Text("物品照片 ${result.itemPhotoCount} 张，已打包原图 ${result.includedMediaCount} 张")
                Text("文件大小 ${result.packageSizeBytes} 字节")
    }
}
