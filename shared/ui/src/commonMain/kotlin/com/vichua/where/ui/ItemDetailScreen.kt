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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 *
 * @param detail 已加载的物品详情；加载中或失败时为空。
 * @param resolveMediaPath 把受控照片标识解析为本地绝对路径。
 * @param loading 是否正在读取详情。
 * @param errorMessage 可展示的中文读取错误。
 * @param onBack 返回上一页。
 * @param onReadLocation 朗读当前位置。
 * @param onUpdateLocation 打开更新位置页。
 * @param editorVisible 是否展示档案编辑对话框。
 * @param editorSubmitting 是否正在保存档案。
 * @param editorErrorMessage 可展示的中文保存错误。
 * @param onEditProfile 打开档案编辑对话框。
 * @param onDismissEditor 关闭档案编辑对话框。
 * @param onSaveProfile 保存名称、位置说明和备注。
 */
@Composable
fun ItemDetailScreen(
    detail: ItemDetail?,
    resolveMediaPath: (String) -> String?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onReadLocation: () -> Unit,
    onUpdateLocation: () -> Unit,
    editorVisible: Boolean,
    editorSubmitting: Boolean,
    editorErrorMessage: String?,
    onEditProfile: () -> Unit,
    onDismissEditor: () -> Unit,
    onSaveProfile: (String, String?, String?) -> Unit,
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
            val selectedPhoto = detail.photos.getOrNull(
                selectedPhotoIndex.coerceAtMost(detail.photos.lastIndex.coerceAtLeast(0)),
            )
            LocalStorageImage(
                absolutePath = selectedPhoto?.let { photo ->
                    resolveMediaPath(photo.storageKey) ?: resolveMediaPath(photo.thumbnailStorageKey)
                },
                contentDescription = detail.name,
                modifier = Modifier.fillMaxSize(),
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
                            "${photoRoleLabel(selectedPhoto?.role ?: PhotoRole.ITEM)} · ${selectedPhotoIndex + 1} / ${detail.photos.size}"
                        },
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (detail.photos.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "${photoRoleLabel(detail.photos[selectedPhotoIndex.coerceAtMost(detail.photos.lastIndex)].role)} · ${selectedPhotoIndex + 1} / ${detail.photos.size}",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PhotoThumbnailSelector(
            photos = detail.photos,
            selectedIndex = selectedPhotoIndex,
            resolveMediaPath = resolveMediaPath,
            onSelect = { index ->
                selectedPhotoIndex = index
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = detail.name,
                color = WherePrimaryTextColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
            )
            TextButton(
                enabled = !editorSubmitting,
                onClick = onEditProfile,
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = WhereIcons.Edit,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = "编辑",
                )
            }
        }

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
                val locationDescription = detail.locationDescription
                if (!locationDescription.isNullOrBlank()) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = locationDescription,
                        color = WherePrimaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "由 ${detail.sourceDeviceName} 更新",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val note = detail.note
        if (!note.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                color = WhereSurfaceColor,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "备注",
                        color = WherePrimaryColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = note,
                        color = WherePrimaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

    if (editorVisible && detail != null) {
        EditItemProfileDialog(
            initialName = detail.name,
            initialLocationDescription = detail.locationDescription.orEmpty(),
            initialNote = detail.note.orEmpty(),
            submitting = editorSubmitting,
            errorMessage = editorErrorMessage,
            onDismiss = onDismissEditor,
            onConfirm = onSaveProfile,
        )
    }
}

/**
 * 编辑名称、位置说明和备注；当前位置仍走独立的更新位置流程。
 */
@Composable
private fun EditItemProfileDialog(
    initialName: String,
    initialLocationDescription: String,
    initialNote: String,
    submitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var locationDescription by remember(initialLocationDescription) {
        mutableStateOf(initialLocationDescription)
    }
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    val trimmedName = name.trim()
    val trimmedLocationDescription = locationDescription.trim().takeIf(String::isNotEmpty)
    val trimmedNote = note.trim().takeIf(String::isNotEmpty)
    // 没有任何可见字段变化时禁用保存，避免产生空变更记录。
    val hasVisibleChange = trimmedName != initialName.trim() ||
        trimmedLocationDescription != initialLocationDescription.trim().takeIf(String::isNotEmpty) ||
        trimmedNote != initialNote.trim().takeIf(String::isNotEmpty)

    AlertDialog(
        onDismissRequest = {
            if (!submitting) {
                onDismiss()
            }
        },
        title = { Text("编辑物品") },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { value -> name = value },
                    enabled = !submitting,
                    label = { Text("物品名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    value = locationDescription,
                    onValueChange = { value -> locationDescription = value },
                    enabled = !submitting,
                    label = { Text("位置补充说明") },
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    value = note,
                    onValueChange = { value -> note = value },
                    enabled = !submitting,
                    label = { Text("备注") },
                )
                if (errorMessage != null) {
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && trimmedName.isNotEmpty() && hasVisibleChange,
                onClick = {
                    onConfirm(trimmedName, trimmedLocationDescription, trimmedNote)
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text("取消")
            }
        },
    )
}

/**
 * 展示可选择的照片缩略图和继续添加入口。
 */
@Composable
private fun PhotoThumbnailSelector(
    photos: List<ItemDetailPhoto>,
    selectedIndex: Int,
    resolveMediaPath: (String) -> String?,
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
                LocalStorageImage(
                    absolutePath = resolveMediaPath(photo.thumbnailStorageKey),
                    contentDescription = photoRoleLabel(photo.role),
                    modifier = Modifier.fillMaxSize(),
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
