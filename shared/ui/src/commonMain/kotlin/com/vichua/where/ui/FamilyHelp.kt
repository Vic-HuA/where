package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "家人正在帮忙",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onExit,
                ),
            color = WhereSelectedContainerColor,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, WherePrimaryColor),
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "返回适老",
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
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
