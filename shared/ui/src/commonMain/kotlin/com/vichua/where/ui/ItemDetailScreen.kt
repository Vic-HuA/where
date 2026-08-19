package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.feature.item.detail.ItemDetail
import com.vichua.where.feature.item.detail.ItemDetailPhoto
import com.vichua.where.core.model.PhotoRole

/**
 * 按 Pencil 原型展示物品照片、当前位置、历史和主要操作。
 */
@Composable
fun ItemDetailScreen(
    detail: ItemDetail?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onReadLocation: () -> Unit,
    onUpdateLocation: () -> Unit,
) {
    var selectedPhotoIndex by remember(detail?.itemId) { mutableIntStateOf(0) }

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
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(12.dp),
                imageVector = WhereIcons.Back,
                contentDescription = "返回",
                tint = WherePrimaryTextColor,
            )
            Text(
                text = "物品详情",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 48.dp),
            )
            return@Column
        }
        if (errorMessage != null || detail == null) {
            Text(
                modifier = Modifier.padding(top = 32.dp),
                text = errorMessage ?: "物品不存在或已删除。",
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(top = 10.dp),
            color = WhereSelectedContainerColor,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    modifier = Modifier.size(52.dp),
                    imageVector = WhereIcons.Image,
                    contentDescription = null,
                    tint = WherePrimaryColor,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = if (detail.photos.isEmpty()) {
                        "暂无照片"
                    } else {
                        "${photoRoleLabel(detail.photos[selectedPhotoIndex.coerceAtMost(detail.photos.lastIndex)].role)} · ${selectedPhotoIndex + 1} / ${detail.photos.size}"
                    },
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        PhotoThumbnailSelector(
            photos = detail.photos,
            selectedIndex = selectedPhotoIndex,
            onSelect = { index ->
                selectedPhotoIndex = index
            },
        )

        Text(
            modifier = Modifier.padding(top = 18.dp),
            text = detail.name,
            color = WherePrimaryTextColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前位置",
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = detail.locationPath,
                    color = WherePrimaryTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "由 ${detail.sourceDeviceName} 更新",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(52.dp),
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "位置历史 · ${detail.locationHistory.size} 条记录",
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhereSurfaceColor,
                    contentColor = WherePrimaryTextColor,
                ),
                onClick = onReadLocation,
            ) {
                Icon(
                    imageVector = WhereIcons.ReadAloud,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = "朗读位置",
                )
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WherePrimaryColor,
                    contentColor = WhereSurfaceColor,
                ),
                onClick = onUpdateLocation,
            ) {
                Icon(
                    imageVector = WhereIcons.Location,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = "更新位置",
                )
            }
        }
    }
}

/**
 * 展示可选择的照片缩略图和继续添加入口。
 */
@Composable
private fun PhotoThumbnailSelector(
    photos: List<ItemDetailPhoto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEachIndexed { index, photo ->
            Surface(
                modifier = Modifier
                    .size(width = 82.dp, height = 58.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            onSelect(index)
                        },
                    ),
                color = if (index == selectedIndex) {
                    WhereSelectedContainerColor
                } else {
                    WhereSurfaceColor
                },
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, WhereOutlineColor),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = WhereIcons.Image,
                        contentDescription = null,
                        tint = WherePrimaryColor,
                    )
                    Text(
                        modifier = Modifier.padding(top = 3.dp),
                        text = photoRoleLabel(photo.role),
                        color = if (index == selectedIndex) {
                            WherePrimaryColor
                        } else {
                            WhereSecondaryTextColor
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.size(width = 82.dp, height = 58.dp),
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = WhereIcons.Add,
                    contentDescription = null,
                    tint = WhereSecondaryTextColor,
                )
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = "添加",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 将照片用途转换为详情页短标签。
 */
private fun photoRoleLabel(role: PhotoRole): String = when (role) {
    PhotoRole.ENVIRONMENT -> "环境照"
    PhotoRole.ITEM -> "物品照"
    PhotoRole.LABEL -> "标签照"
    PhotoRole.SUPPLEMENTARY -> "补充照"
}
