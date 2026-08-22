package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 拍照和相册二选一，避免“拍照”入口实际只打开相册。
 */
@Composable
fun ImageSourceDialog(
    onDismiss: () -> Unit,
    onCapture: () -> Unit,
    onPick: () -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "添加照片",
        dismissText = "取消",
        onDismiss = onDismiss,
    ) {
        Text(
            text = "可以直接拍照，也可以从相册选一张已有照片。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        ImageSourceRow(
            title = "拍照",
            description = "打开相机拍一张当前物品或存放位置",
            onClick = onCapture,
        )
        ImageSourceRow(
            title = "从相册选择",
            description = "使用手机里已经拍好的照片",
            onClick = onPick,
        )
    }
}

@Composable
private fun ImageSourceRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = WhereSurfaceColor,
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColumnText(
                title = title,
                description = description,
            )
        }
    }
}

@Composable
private fun ColumnText(
    title: String,
    description: String,
) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = description,
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
