package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 与首页卡片同一套圆角、描边和主色按钮，避免系统默认弹窗显得发飘。
 *
 * 点遮罩关闭走 [onDismissRequest]；底栏取消走 [onDismiss]，两者可以不同，
 * 例如离开录入时遮罩表示继续编辑，取消按钮表示放弃修改。
 */
@Composable
fun WhereDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmDestructive: Boolean = false,
    dismissText: String? = "取消",
    onDismiss: (() -> Unit)? = onDismissRequest,
    dismissEnabled: Boolean = true,
    dismissDestructive: Boolean = false,
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null,
    neutralEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            shape = RoundedCornerShape(22.dp),
            color = WhereSurfaceColor,
            border = BorderStroke(WhereStrokeWidth, WhereOutlineColor),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
                if (confirmText != null || dismissText != null || neutralText != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (dismissText != null && onDismiss != null) {
                            WhereDialogButton(
                                modifier = Modifier.weight(1f),
                                text = dismissText,
                                enabled = dismissEnabled,
                                outlined = !dismissDestructive,
                                destructive = dismissDestructive,
                                onClick = onDismiss,
                            )
                        }
                        if (neutralText != null && onNeutral != null) {
                            WhereDialogButton(
                                modifier = Modifier.weight(1f),
                                text = neutralText,
                                enabled = neutralEnabled,
                                outlined = true,
                                destructive = false,
                                onClick = onNeutral,
                            )
                        }
                        if (confirmText != null && onConfirm != null) {
                            WhereDialogButton(
                                modifier = Modifier.weight(1f),
                                text = confirmText,
                                enabled = confirmEnabled,
                                outlined = false,
                                destructive = confirmDestructive,
                                onClick = onConfirm,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 备份进行中的页内提示。
 *
 * 系统选文件会盖住独立弹窗，页内卡片仍留在按钮旁边，避免只剩一行灰字。
 */
@Composable
fun WorkingProgressCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = WhereSelectedContainerColor,
        border = BorderStroke(WhereStrokeWidth, WherePrimaryColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = WherePrimaryColor,
                strokeWidth = 3.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = message,
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 备份、验证和恢复进行中的挡板。
 *
 * 加密和读写文件会停几秒，进度如果只写在长页面顶部，用户会以为卡住。
 * 遮罩不可点掉，避免中途再点一次造成重复提交。
 */
@Composable
fun WorkingProgressDialog(
    title: String,
    message: String,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            shape = RoundedCornerShape(22.dp),
            color = WhereSurfaceColor,
            border = BorderStroke(WhereStrokeWidth, WhereOutlineColor),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = WherePrimaryColor,
                    strokeWidth = 3.dp,
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = title,
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = message,
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 弹窗底栏按钮：主操作填色，次操作描边，危险操作用错误色。
 */
@Composable
private fun WhereDialogButton(
    modifier: Modifier,
    text: String,
    enabled: Boolean,
    outlined: Boolean,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        destructive -> MaterialTheme.colorScheme.error
        outlined -> WhereSurfaceColor
        else -> WherePrimaryColor
    }
    val contentColor = when {
        destructive -> WhereSurfaceColor
        outlined -> WherePrimaryTextColor
        else -> WhereSurfaceColor
    }
    val border = if (outlined) {
        BorderStroke(WhereStrokeWidth, WhereOutlineColor)
    } else {
        null
    }
    val colors = ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = containerColor.copy(alpha = 0.4f),
        disabledContentColor = contentColor.copy(alpha = 0.7f),
    )
    if (outlined) {
        OutlinedButton(
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = border,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor,
                disabledContentColor = contentColor.copy(alpha = 0.5f),
            ),
        ) {
            Text(text)
        }
    } else {
        Button(
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            colors = colors,
        ) {
            Text(text)
        }
    }
}
