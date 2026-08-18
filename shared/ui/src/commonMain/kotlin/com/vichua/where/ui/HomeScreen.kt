package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.feature.search.home.FavoriteLocationSummary
import com.vichua.where.feature.search.home.HomeItemSummary
import com.vichua.where.feature.search.home.HomeSnapshot

/**
 * 按 Pencil 原型展示首页搜索入口和本地摘要。
 *
 * @param snapshot 已加载的首页快照；加载中或失败时为空。
 * @param loading 是否正在读取本地数据。
 * @param errorMessage 可展示的中文读取错误。
 * @param onRetry 重试加载首页数据。
 * @param onSearchClick 打开文字或语音查找。
 * @param onRecordItemClick 打开新增物品流程。
 * @param onLocationClick 打开位置管理。
 * @param onSettingsClick 打开设置与数据。
 */
@Composable
fun HomeScreen(
    snapshot: HomeSnapshot?,
    loading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSearchClick: () -> Unit,
    onRecordItemClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        containerColor = WhereBackgroundColor,
        bottomBar = {
            HomeBottomNavigation(
                onLocationClick = onLocationClick,
                onSettingsClick = onSettingsClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = "你好",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = "今天要找什么？",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.headlineMedium,
            )

            HomeSearchSurface(
                modifier = Modifier.padding(top = 18.dp),
                onClick = onSearchClick,
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WherePrimaryColor,
                    contentColor = WhereSurfaceColor,
                ),
                onClick = onRecordItemClick,
            ) {
                Text(
                    text = "拍照记录物品",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                return@Column
            }
            if (errorMessage != null) {
                HomeLoadError(
                    message = errorMessage,
                    onRetry = onRetry,
                )
                return@Column
            }

            val loadedSnapshot = snapshot ?: return@Column
            if (loadedSnapshot.locationUnconfirmedCount > 0L) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(44.dp),
                    color = WhereSelectedContainerColor,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ⓘ ${loadedSnapshot.locationUnconfirmedCount} 件位置待确认",
                            color = WherePrimaryTextColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            HomeSectionTitle(
                modifier = Modifier.padding(top = 24.dp),
                text = "最近记录",
            )
            if (loadedSnapshot.recentItems.isEmpty()) {
                EmptyHomeCard(
                    modifier = Modifier.padding(top = 12.dp),
                    text = "还没有记录物品",
                )
            } else {
                loadedSnapshot.recentItems.forEach { item ->
                    RecentItemCard(
                        modifier = Modifier.padding(top = 12.dp),
                        item = item,
                    )
                }
            }

            if (loadedSnapshot.recentSearches.isNotEmpty()) {
                HomeSectionTitle(
                    modifier = Modifier.padding(top = 22.dp),
                    text = "最近查找",
                )
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    loadedSnapshot.recentSearches.take(MAX_VISIBLE_CHIPS).forEach { search ->
                        HomeChip(
                            icon = WhereIcons.Search,
                            text = search.displayQuery,
                        )
                    }
                }
            }

            if (loadedSnapshot.favoriteLocations.isNotEmpty()) {
                HomeSectionTitle(
                    modifier = Modifier.padding(top = 22.dp),
                    text = "常用位置",
                )
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    loadedSnapshot.favoriteLocations
                        .take(MAX_VISIBLE_CHIPS)
                        .forEach { favorite ->
                            FavoriteLocationChip(favorite)
                        }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 首页语音和文字查找入口。
 */
@Composable
private fun HomeSearchSurface(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        color = WhereSelectedContainerColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WherePrimaryColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = WhereIcons.Microphone,
                contentDescription = null,
                tint = WherePrimaryColor,
            )
            Text(
                text = "按住说话查找物品",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * 首页分区标题。
 */
@Composable
private fun HomeSectionTitle(
    modifier: Modifier,
    text: String,
) {
    Text(
        modifier = modifier,
        text = text,
        color = WherePrimaryTextColor,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge,
    )
}

/**
 * 最近物品卡片。
 */
@Composable
private fun RecentItemCard(
    modifier: Modifier,
    item: HomeItemSummary,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(width = 78.dp, height = 84.dp),
                color = WhereSelectedContainerColor,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(30.dp),
                        imageVector = WhereIcons.Image,
                        contentDescription = null,
                        tint = WherePrimaryColor,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.name,
                    color = WherePrimaryTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = item.locationPath,
                    color = WherePrimaryTextColor,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = "最近更新",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 首页没有物品时的真实空状态。
 */
@Composable
private fun EmptyHomeCard(
    modifier: Modifier,
    text: String,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * 最近搜索和常用位置使用的紧凑胶囊。
 */
@Composable
private fun HomeChip(
    icon: ImageVector,
    text: String,
) {
    Surface(
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                imageVector = icon,
                contentDescription = null,
                tint = WherePrimaryColor,
            )
            Text(
                text = text,
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 常用位置胶囊。
 */
@Composable
private fun FavoriteLocationChip(favorite: FavoriteLocationSummary) {
    HomeChip(
        icon = WhereIcons.room(favorite.iconKey.orEmpty()),
        text = "${favorite.name} ${favorite.itemCount}",
    )
}

/**
 * 首页读取失败状态。
 */
@Composable
private fun HomeLoadError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            modifier = Modifier.padding(top = 12.dp),
            onClick = onRetry,
        ) {
            Text("重试")
        }
    }
}

/**
 * 首页、位置和设置三个固定底部导航入口。
 */
@Composable
private fun HomeBottomNavigation(
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
                .height(74.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavigationItem(
                icon = WhereIcons.Home,
                label = "首页",
                selected = true,
                onClick = {},
            )
            BottomNavigationItem(
                icon = WhereIcons.Location,
                label = "位置",
                selected = false,
                onClick = onLocationClick,
            )
            BottomNavigationItem(
                icon = WhereIcons.Settings,
                label = "设置",
                selected = false,
                onClick = onSettingsClick,
            )
        }
    }
}

/**
 * 单个底部导航项。
 */
@Composable
private fun BottomNavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 80.dp, height = 50.dp)
            .clickable(
                role = Role.Tab,
                onClick = onClick,
            ),
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

private const val MAX_VISIBLE_CHIPS = 3
