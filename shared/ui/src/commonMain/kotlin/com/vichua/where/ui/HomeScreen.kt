package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.semantics
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
 * @param confirmationSpeechErrorMessage 确认保存后自动朗读失败时的中文降级提示。
 * @param backupReminderMessage 开启备份提醒且尚未成功备份时的中文提示；已有备份或未开启时为空。
 * @param onBackupReminderClick 从提醒进入设置与数据。
 * @param onUndoDeletion 撤销最近一次物品删除。
 * @param onRetry 重试加载首页数据。
 * @param onTextSearch 提交首页键盘查询。
 * @param onVoiceSearchRequested 按下语音区域后开始听。
 * @param onVoiceSearchReleased 松开后结束本轮收听。
 * @param onItemClick 打开最近物品详情。
 * @param onViewAllItems 打开全部物品列表。
 * @param onRecentSearchClick 用同一查询再次执行本地搜索。
 * @param onFavoriteLocationClick 按常用位置名称查找该处物品。
 * @param onRecordItemClick 打开新增物品流程。
 * @param onLocationClick 打开位置管理。
 * @param onSettingsClick 打开设置与数据。
 * @param elderFriendlyMode 是否使用适老首页：两大入口替代搜索框和拍照记录按钮。
 * @param voiceListening 是否正在听用户主动说的查找内容。
 * @param voicePreparing 首次使用时是否正在下载离线语音模型。
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
    confirmationSpeechErrorMessage: String? = null,
    backupReminderMessage: String? = null,
    onBackupReminderClick: () -> Unit = {},
    onUndoDeletion: () -> Unit = {},
    onRetry: () -> Unit,
    onTextSearch: (String) -> Unit,
    onVoiceSearchRequested: () -> Unit,
    onVoiceSearchReleased: () -> Unit = {},
    onItemClick: (HomeItemSummary) -> Unit,
    onViewAllItems: () -> Unit = {},
    onRecentSearchClick: (String) -> Unit,
    onFavoriteLocationClick: (FavoriteLocationSummary) -> Unit,
    onRecordItemClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    elderFriendlyMode: Boolean = false,
    voiceListening: Boolean = false,
    voicePreparing: Boolean = false,
) {
    Scaffold(
        containerColor = WhereBackgroundColor,
        bottomBar = {
            WhereBottomNavigation(
                selected = WhereRootTab.HOME,
                onHomeClick = {},
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
            if (confirmationSpeechErrorMessage != null) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = confirmationSpeechErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (backupReminderMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = onBackupReminderClick,
                        ),
                    color = WhereSelectedContainerColor,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        text = backupReminderMessage,
                        color = WherePrimaryTextColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (elderFriendlyMode) {
                ElderHomeActionButton(
                    modifier = Modifier.padding(top = 18.dp),
                    icon = WhereIcons.Search,
                    title = "我要找东西",
                    description = when {
                        voicePreparing -> "正在下载语音模型，请稍候…"
                        voiceListening -> "正在听，请说话…"
                        else -> "先说要找什么，也可以改用键盘"
                    },
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
                    onVoiceSearchReleased = onVoiceSearchReleased,
                    voiceListening = voiceListening,
                    voicePreparing = voicePreparing,
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
                actionText = if (loadedSnapshot.recentItems.isNotEmpty()) "查看全部" else null,
                onActionClick = onViewAllItems,
            )
            if (loadedSnapshot.recentItems.isEmpty()) {
                EmptyHomeCard(
                    modifier = Modifier.padding(top = 12.dp),
                    text = "还没有记录物品",
                )
            } else {
                loadedSnapshot.recentItems.forEach { item ->
                    ItemSummaryCard(
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
                            onClick = {
                                onRecentSearchClick(search.displayQuery)
                            },
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
                            FavoriteLocationChip(
                                favorite = favorite,
                                onClick = {
                                    onFavoriteLocationClick(favorite)
                                },
                            )
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
    onTextSearch: (String) -> Unit,
    onVoiceSearchRequested: () -> Unit,
    onVoiceSearchReleased: () -> Unit,
    voiceListening: Boolean,
    voicePreparing: Boolean,
) {
    var textModeEnabled by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitTextSearch: () -> Unit = {
        if (query.isNotBlank()) {
            keyboardController?.hide()
            onTextSearch(query)
        }
    }

    if (textModeEnabled) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
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
                    onSearch = { submitTextSearch() },
                ),
            )
            Button(
                modifier = Modifier.heightIn(min = 56.dp),
                enabled = query.isNotBlank(),
                onClick = submitTextSearch,
            ) {
                Text("查找")
            }
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            color = WhereSelectedContainerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(WhereStrokeWidth, WhereSoftBorderColor),
        ) {
            Row(
                modifier = Modifier.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .holdToSpeak(
                            enabled = true,
                            onPress = onVoiceSearchRequested,
                            onRelease = onVoiceSearchReleased,
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
                        text = when {
                            voicePreparing -> "正在下载语音模型，请稍候…"
                            voiceListening -> "正在听，请说话…"
                            else -> "按住说话查找物品"
                        },
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
 * 首页分区标题。右侧动作留给「查看全部」，避免再加底部入口。
 */
@Composable
private fun HomeSectionTitle(
    modifier: Modifier,
    text: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = WherePrimaryTextColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        if (actionText != null && onActionClick != null) {
            Text(
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClick = onActionClick,
                ),
                text = actionText,
                color = WherePrimaryColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 首页和全部物品共用的摘要卡片。
 */
@Composable
internal fun ItemSummaryCard(
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
        border = if (WhereEmphasizedBorders) {
            BorderStroke(WhereStrokeWidth, WhereOutlineColor)
        } else {
            null
        },
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
                    text = visibleLocationPath(item.locationPath),
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
internal fun EmptyHomeCard(
    modifier: Modifier,
    text: String,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(18.dp),
        border = if (WhereEmphasizedBorders) {
            BorderStroke(WhereStrokeWidth, WhereOutlineColor)
        } else {
            null
        },
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
    onClick: () -> Unit,
) {
    Surface(
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(WhereStrokeWidth, WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable(role = Role.Button, onClick = onClick)
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
private fun FavoriteLocationChip(
    favorite: FavoriteLocationSummary,
    onClick: () -> Unit,
) {
    HomeChip(
        icon = WhereIcons.room(favorite.iconKey.orEmpty()),
        text = "${favorite.name} ${favorite.itemCount}",
        onClick = onClick,
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
