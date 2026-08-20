package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.vichua.where.feature.search.home.FavoriteLocationSummary
import com.vichua.where.feature.search.home.HomeItemSummary
import com.vichua.where.feature.search.home.HomeSnapshot

/**
 * 按 Pencil 原型展示首页搜索入口和本地摘要。
 *
 * @param snapshot 已加载的首页快照；加载中或失败时为空。
 * @param resolveMediaPath 把封面缩略图标识解析为本地绝对路径。
 * @param loading 是否正在读取本地数据。
 * @param errorMessage 可展示的中文读取错误。
 * @param deletionUndo 当前会话内尚未过期的删除撤销条；没有待撤销删除时为空。
 * @param deletionUndoSubmitting 是否正在执行撤销。
 * @param deletionUndoErrorMessage 可展示的中文撤销错误。
 * @param onUndoDeletion 撤销最近一次物品删除。
 * @param onRetry 重试加载首页数据。
 * @param onTextSearch 提交首页键盘查询。
 * @param onVoiceSearchRequested 长按语音区域后请求语音查找。
 * @param onItemClick 打开最近物品详情。
 * @param onRecordItemClick 打开新增物品流程。
 * @param onLocationClick 打开位置管理。
 * @param onSettingsClick 打开设置与数据。
 * @param elderFriendlyMode 是否使用适老首页：两大入口替代搜索框和拍照记录按钮。
 */
@Composable
fun HomeScreen(
    snapshot: HomeSnapshot?,
    resolveMediaPath: (String) -> String?,
    loading: Boolean,
    errorMessage: String?,
    deletionUndo: HomeDeletionUndo? = null,
    deletionUndoSubmitting: Boolean = false,
    deletionUndoErrorMessage: String? = null,
    onUndoDeletion: () -> Unit = {},
    onRetry: () -> Unit,
    onTextSearch: (String) -> Unit,
    onVoiceSearchRequested: () -> Unit,
    onItemClick: (HomeItemSummary) -> Unit,
    onRecordItemClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    elderFriendlyMode: Boolean = false,
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

            if (deletionUndo != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    color = WhereSelectedContainerColor,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "已删除「${deletionUndo.itemName}」 ${deletionUndo.remainingSeconds}s",
                            color = WherePrimaryTextColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            enabled = !deletionUndoSubmitting,
                            onClick = onUndoDeletion,
                        ) {
                            Text("撤销")
                        }
                    }
                }
            }
            if (deletionUndoErrorMessage != null) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = deletionUndoErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (elderFriendlyMode) {
                ElderHomeActionButton(
                    modifier = Modifier.padding(top = 18.dp),
                    icon = WhereIcons.Search,
                    title = "我要找东西",
                    description = "先说要找什么，也可以改用键盘",
                    primary = true,
                    onClick = onVoiceSearchRequested,
                )
                ElderHomeActionButton(
                    modifier = Modifier.padding(top = 12.dp),
                    icon = WhereIcons.Camera,
                    title = "我要放东西",
                    description = "拍照或说一句记录存放位置",
                    primary = false,
                    onClick = onRecordItemClick,
                )
            } else {
                HomeSearchSurface(
                    modifier = Modifier.padding(top = 18.dp),
                    onTextSearch = onTextSearch,
                    onVoiceSearchRequested = onVoiceSearchRequested,
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .heightIn(min = 54.dp),
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
                        thumbnailPath = item.thumbnailStorageKey?.let(resolveMediaPath),
                        onClick = {
                            onItemClick(item)
                        },
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
@OptIn(ExperimentalFoundationApi::class)
private fun HomeSearchSurface(
    modifier: Modifier,
    onTextSearch: (String) -> Unit,
    onVoiceSearchRequested: () -> Unit,
) {
    var textModeEnabled by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (textModeEnabled) {
        OutlinedTextField(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            value = query,
            onValueChange = { value ->
                query = value
            },
            placeholder = {
                Text("输入物品名称、别名或位置")
            },
            leadingIcon = {
                Icon(
                    imageVector = WhereIcons.Search,
                    contentDescription = null,
                    tint = WherePrimaryColor,
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        textModeEnabled = false
                        keyboardController?.hide()
                    },
                ) {
                    Icon(
                        imageVector = WhereIcons.Microphone,
                        contentDescription = "切换到语音查找",
                        tint = WherePrimaryColor,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WhereSelectedContainerColor,
                unfocusedContainerColor = WhereSelectedContainerColor,
                focusedBorderColor = WherePrimaryColor,
                unfocusedBorderColor = WherePrimaryColor.copy(alpha = 0.35f),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) {
                        keyboardController?.hide()
                        onTextSearch(query)
                    }
                },
            ),
        )
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            color = WhereSelectedContainerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, WherePrimaryColor.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            role = Role.Button,
                            onClick = onVoiceSearchRequested,
                            onLongClick = onVoiceSearchRequested,
                            onLongClickLabel = "按住说话查找物品",
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(start = 16.dp),
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
                IconButton(
                    onClick = {
                        textModeEnabled = true
                    },
                ) {
                    Icon(
                        imageVector = WhereIcons.Keyboard,
                        contentDescription = "切换到键盘输入",
                        tint = WherePrimaryColor,
                    )
                }
            }
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
    thumbnailPath: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
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
                LocalStorageImage(
                    absolutePath = thumbnailPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
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
            }
            Column(
                modifier = Modifier.weight(1f),
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
            .heightIn(min = 76.dp),
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
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                .heightIn(min = 74.dp)
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

/**
 * 适老首页主入口：图形、文字和可朗读语义同时给出。
 */
@Composable
private fun ElderHomeActionButton(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) WherePrimaryColor else WhereSurfaceColor,
            contentColor = if (primary) WhereSurfaceColor else WherePrimaryTextColor,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                imageVector = icon,
                contentDescription = title,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 首页当前会话内的删除撤销条内容。
 *
 * @property itemName 刚删除的物品名称。
 * @property remainingSeconds 撤销窗口剩余秒数。
 */
data class HomeDeletionUndo(
    val itemName: String,
    val remainingSeconds: Int,
)

private const val MAX_VISIBLE_CHIPS = 3
