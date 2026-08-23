package com.vichua.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vichua.where.feature.search.home.HomeItemSummary

/**
 * 查看当前家庭全部未删除物品。
 *
 * 首页只留最近三条，完整浏览走这一页，避免再加底部导航。
 */
@Composable
fun AllItemsScreen(
    title: String = "全部物品",
    subtitle: String? = null,
    emptyText: String = "还没有记录物品",
    items: List<HomeItemSummary>,
    resolveMediaPath: (String) -> String?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (HomeItemSummary) -> Unit,
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
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(12.dp),
                imageVector = WhereIcons.Back,
                contentDescription = "返回",
            )
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        if (loading && items.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 48.dp),
                color = WherePrimaryColor,
            )
            return@Column
        }
        if (errorMessage != null && items.isEmpty()) {
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
        if (items.isEmpty()) {
            EmptyHomeCard(
                modifier = Modifier.padding(top = 16.dp),
                text = emptyText,
            )
            return@Column
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = subtitle ?: "共 ${items.size} 件，按最近更新排列。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        items.forEach { item ->
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
}
