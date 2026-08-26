package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 家人帮助进行中的单任务提示。
 *
 * 只在本机切换到普通输入，不发消息、不打电话、不上传家庭数据。
 */
@Composable
fun FamilyHelpBanner(
    visible: Boolean,
    onExit: () -> Unit,
) {
    if (!visible) {
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        color = WhereSelectedContainerColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WherePrimaryColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "家人正在这台手机上帮忙",
                color = WherePrimaryTextColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = "现在显示键盘和完整位置，方便家人帮你确认。不会发消息、不会打电话，也不会上传家庭数据。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onExit) {
                Text("返回适老")
            }
        }
    }
}

/**
 * 适老流程里的「请家人帮助」入口，固定人物图标和文案。
 */
@Composable
fun FamilyHelpAction(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) {
        return
    }
    Button(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WhereSurfaceColor,
            contentColor = WherePrimaryTextColor,
        ),
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = WhereIcons.FamilyHelp,
            contentDescription = "请家人帮助",
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = "请家人帮助",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
