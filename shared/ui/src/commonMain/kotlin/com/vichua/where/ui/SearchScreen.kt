package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.feature.search.text.ItemTextSearchResult

/**
 * 本地文字搜索与结果页面。
 *
 * @param initialQuery 从首页键盘入口带入的查询文本。
 * @param results 最近一次查询结果，尚未查询时为空。
 * @param searching 是否正在执行本地查询。
 * @param errorMessage 可展示的中文错误。
 * @param onBack 返回首页。
 * @param onSearch 提交查询。
 * @param onResultClick 打开物品详情。
 * @param elderFriendlyMode 是否使用更大卡片，并为每条结果提供朗读位置。
 * @param speechErrorMessage 可展示的中文朗读错误。
 * @param onReadLocation 朗读单条搜索结果的名称、位置和更新时间。
 */
@Composable
fun SearchScreen(
    initialQuery: String,
    results: List<ItemTextSearchResult>?,
    searching: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onResultClick: (ItemTextSearchResult) -> Unit,
    elderFriendlyMode: Boolean = false,
    speechErrorMessage: String? = null,
    onReadLocation: (ItemTextSearchResult) -> Unit = {},
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = onBack,
                    )
                    .padding(12.dp),
                imageVector = WhereIcons.Back,
                contentDescription = "返回",
                tint = WherePrimaryTextColor,
            )
            Text(
                text = "搜索结果",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            value = query,
            onValueChange = { value ->
                query = value
            },
            enabled = !searching,
            placeholder = {
                Text("输入物品名称、别名或位置")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WhereSelectedContainerColor,
                unfocusedContainerColor = WhereSelectedContainerColor,
                focusedBorderColor = WherePrimaryColor,
                unfocusedBorderColor = WhereOutlineColor,
            ),
            trailingIcon = {
                Icon(
                    modifier = Modifier
                        .clickable(
                            enabled = !searching,
                            onClick = {
                                onSearch(query)
                            },
                        )
                        .padding(10.dp),
                    imageVector = WhereIcons.Search,
                    contentDescription = "查找",
                    tint = WherePrimaryColor,
                )
            },
        )

        if (searching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (speechErrorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = speechErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (!searching && results != null) {
            Text(
                modifier = Modifier.padding(top = 22.dp),
                text = "找到 ${results.size} 件物品",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (results.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .heightIn(min = 88.dp),
                    color = WhereSurfaceColor,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "没有找到本地记录，请修改关键词。",
                            color = WhereSecondaryTextColor,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else {
                results.forEach { result ->
                    SearchResultCard(
                        modifier = Modifier.padding(top = 12.dp),
                        result = result,
                        elderFriendlyMode = elderFriendlyMode,
                        onClick = {
                            onResultClick(result)
                        },
                        onReadLocation = {
                            onReadLocation(result)
                        },
                    )
                }
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            enabled = query.isNotBlank() && !searching,
            onClick = {
                onSearch(query)
            },
        ) {
            Text("查找")
        }
    }
}

/**
 * 单条本地搜索结果卡片。
 */
@Composable
private fun SearchResultCard(
    modifier: Modifier,
    result: ItemTextSearchResult,
    elderFriendlyMode: Boolean,
    onClick: () -> Unit,
    onReadLocation: () -> Unit,
) {
    val thumbnailSize = if (elderFriendlyMode) 96.dp else 76.dp
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
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(thumbnailSize),
                    color = WhereSelectedContainerColor,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            modifier = Modifier.size(if (elderFriendlyMode) 36.dp else 30.dp),
                            imageVector = WhereIcons.Image,
                            contentDescription = null,
                            tint = WherePrimaryColor,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.name,
                        color = WherePrimaryTextColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = visibleLocationPath(result.locationPath),
                        color = WherePrimaryTextColor,
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
            if (elderFriendlyMode) {
                TextButton(
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = onReadLocation,
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = WhereIcons.ReadAloud,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 6.dp),
                        text = "朗读位置",
                    )
                }
            }
        }
    }
}
