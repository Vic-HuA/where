package com.vichua.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * 首页、位置、设置三个根入口。切换时替换，不往返回栈上叠一层。
 */
enum class WhereRootTab {
    HOME,
    LOCATION,
    SETTINGS,
}

/**
 * 三个根页共用的底栏，选中态与首页保持同一套胶囊样式。
 */
@Composable
fun WhereBottomNavigation(
    selected: WhereRootTab,
    onHomeClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WhereSurfaceColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 74.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhereBottomNavigationItem(
                icon = WhereIcons.Home,
                label = "首页",
                selected = selected == WhereRootTab.HOME,
                onClick = onHomeClick,
            )
            WhereBottomNavigationItem(
                icon = WhereIcons.Location,
                label = "位置",
                selected = selected == WhereRootTab.LOCATION,
                onClick = onLocationClick,
            )
            WhereBottomNavigationItem(
                icon = WhereIcons.Settings,
                label = "设置",
                selected = selected == WhereRootTab.SETTINGS,
                onClick = onSettingsClick,
            )
        }
    }
}

/**
 * 单个底部导航项。
 */
@Composable
private fun WhereBottomNavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 80.dp)
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = if (selected) "当前页面" else "未选中"
            },
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        contentColor = if (selected) WherePrimaryColor else WhereSecondaryTextColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = icon,
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = label,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
