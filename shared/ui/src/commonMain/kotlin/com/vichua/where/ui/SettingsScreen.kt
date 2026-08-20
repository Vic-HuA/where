package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.LocalAccessibilityPreferences

/**
 * 设置与数据页：只让适老模式和高对比度真正生效，其余入口明确标明尚未接入。
 *
 * @param preferences 当前设备辅助偏好；加载中或失败时为空。
 * @param loading 是否正在读取偏好。
 * @param submitting 是否正在保存偏好。
 * @param errorMessage 可展示的中文错误。
 * @param onBack 返回首页。
 * @param onRetry 重新读取偏好。
 * @param onElderFriendlyChange 切换适老展示。
 * @param onHighContrastChange 单独切换高对比度。
 */
@Composable
fun SettingsScreen(
    preferences: LocalAccessibilityPreferences?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onElderFriendlyChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
) {
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

        SettingsSectionTitle("常用设置")
        PendingSettingsRow("AI 辅助", "首次安装默认关闭，尚未接入")
        PendingSettingsRow("云端语音识别", "仅在用户主动说话后才会上传，尚未接入")

        SettingsSectionTitle("辅助能力")
        SettingsSwitchRow(
            title = "适老模式",
            description = "首页改为两大入口，并切换录入、搜索、详情和更新位置的适老展示",
            checked = preferences?.elderFriendly == true,
            enabled = preferences != null && !submitting,
            onCheckedChange = onElderFriendlyChange,
        )
        SettingsSwitchRow(
            title = "高对比度",
            description = "独立设备偏好；启用适老模式时默认开启，可单独关闭",
            checked = preferences?.highContrastEnabled == true,
            enabled = preferences != null && !submitting,
            onCheckedChange = onHighContrastChange,
        )
        PendingSettingsRow("自动朗读确认结果", "字段已预留，行为尚未接入")
        PendingSettingsRow("触觉反馈", "字段已预留，行为尚未接入")

        SettingsSectionTitle("家庭与位置")
        PendingSettingsRow("位置管理", "请从首页底部进入，设置页不重复提供入口")

        SettingsSectionTitle("数据与备份")
        PendingSettingsRow("创建加密备份", "尚未接入")
        PendingSettingsRow("验证备份", "尚未接入")
        PendingSettingsRow("恢复备份", "尚未接入")
        PendingSettingsRow("导出完整家庭数据", "尚未接入")

        SettingsSectionTitle("危险操作")
        PendingSettingsRow("清除家庭数据", "尚未接入")

        Spacer(modifier = Modifier.height(20.dp))
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
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = WherePrimaryColor,
                    checkedThumbColor = WhereSurfaceColor,
                ),
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
