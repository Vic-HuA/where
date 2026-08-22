package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.feature.item.detail.ItemDetail
import com.vichua.where.feature.item.detail.ItemDetailPhoto

/**
 * 独立照片管理页：调整顺序、用途、封面和删除，不离开当前物品。
 */
@Composable
fun PhotoManagementScreen(
    detail: ItemDetail?,
    resolveMediaPath: (String) -> String?,
    submitting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onAddPhoto: (PhotoRole) -> Unit,
    onSetPhotoCover: (PhotoAssetId) -> Unit,
    onSetPhotoRole: (PhotoAssetId, PhotoRole) -> Unit,
    onMovePhoto: (PhotoAssetId, Int) -> Unit,
    onReorderPhotos: (PhotoAssetId, Int, Int) -> Unit,
    onDeletePhoto: (PhotoAssetId) -> Unit,
) {
    var addRoleDialogVisible by remember { mutableStateOf(false) }
    var editedPhotoId by remember { mutableStateOf<PhotoAssetId?>(null) }
    var deletePhotoId by remember { mutableStateOf<PhotoAssetId?>(null) }

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
            Text(
                text = "管理照片",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (detail == null) {
            Text(
                modifier = Modifier.padding(top = 24.dp),
                text = "暂时无法读取照片。",
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "${detail.name} · ${detail.photos.size} 张照片",
                color = WhereSecondaryTextColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (detail.photos.size > 1) {
                Text(
                    text = "按住右侧拖条调换顺序",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        PhotoManagementReorderList(
            photos = detail.photos,
            locationPath = visibleLocationPath(detail.locationPath),
            resolveMediaPath = resolveMediaPath,
            submitting = submitting,
            onOpen = { photoId -> editedPhotoId = photoId },
            onMove = { photoId, offset -> onMovePhoto(photoId, offset) },
            onReorder = onReorderPhotos,
            onDelete = { photoId -> deletePhotoId = photoId },
        )

        if (detail.photos.size >= MvpLimits.ITEM_PHOTO_WARNING_THRESHOLD) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "当前已有 ${detail.photos.size} 张照片，存储占用会继续增加。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(48.dp),
            enabled = !submitting,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
            onClick = { addRoleDialogVisible = true },
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = WhereIcons.AddPhoto,
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = "添加照片",
            )
        }
    }

    if (addRoleDialogVisible) {
        PhotoManagementAddRoleDialog(
            submitting = submitting,
            onDismiss = { addRoleDialogVisible = false },
            onConfirm = { role ->
                addRoleDialogVisible = false
                onAddPhoto(role)
            },
        )
    }

    val currentDetail = detail
    if (currentDetail != null) {
        val editedPhoto = currentDetail.photos.firstOrNull { photo -> photo.photoId == editedPhotoId }
        if (editedPhoto != null) {
            val index = currentDetail.photos.indexOfFirst { photo -> photo.photoId == editedPhoto.photoId }
            PhotoManagementEditDialog(
                photo = editedPhoto,
                canMovePrevious = index > 0,
                canMoveNext = index >= 0 && index < currentDetail.photos.lastIndex,
                submitting = submitting,
                onDismiss = { editedPhotoId = null },
                onSetCover = { onSetPhotoCover(editedPhoto.photoId) },
                onSetRole = { role -> onSetPhotoRole(editedPhoto.photoId, role) },
                onDelete = {
                    editedPhotoId = null
                    deletePhotoId = editedPhoto.photoId
                },
            )
        }
    }

    val pendingDelete = detail?.photos?.firstOrNull { photo -> photo.photoId == deletePhotoId }
    if (pendingDelete != null) {
        WhereDialog(
            onDismissRequest = {
                if (!submitting) {
                    deletePhotoId = null
                }
            },
            title = "删除照片",
            confirmText = "删除",
            onConfirm = {
                onDeletePhoto(pendingDelete.photoId)
                deletePhotoId = null
            },
            confirmEnabled = !submitting,
            confirmDestructive = true,
            dismissText = "取消",
            onDismiss = {
                if (!submitting) {
                    deletePhotoId = null
                }
            },
            dismissEnabled = !submitting,
        ) {
            Text("删除后这张${photoManagementRoleLabel(pendingDelete.role)}将不再显示。物品档案会保留。")
        }
    }
}

/**
 * 长按拖条后按行高换位，松手再写入新顺序。
 */
@Composable
private fun PhotoManagementReorderList(
    photos: List<ItemDetailPhoto>,
    locationPath: String,
    resolveMediaPath: (String) -> String?,
    submitting: Boolean,
    onOpen: (PhotoAssetId) -> Unit,
    onMove: (PhotoAssetId, Int) -> Unit,
    onReorder: (PhotoAssetId, Int, Int) -> Unit,
    onDelete: (PhotoAssetId) -> Unit,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    photos.forEachIndexed { index, photo ->
        val dragging = draggingIndex == index
        val visualOffset = if (dragging) dragOffset else 0f
        PhotoManagementRow(
            modifier = Modifier
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer { translationY = visualOffset }
                .onSizeChanged { size ->
                    if (size.height > 0) {
                        rowHeight = size.height
                    }
                },
            photo = photo,
            locationPath = locationPath,
            resolveMediaPath = resolveMediaPath,
            canMovePrevious = index > 0,
            canMoveNext = index < photos.lastIndex,
            submitting = submitting,
            dragEnabled = photos.size > 1 && !submitting,
            onOpen = { onOpen(photo.photoId) },
            onMove = { offset -> onMove(photo.photoId, offset) },
            onDelete = { onDelete(photo.photoId) },
            onDrag = { amount ->
                if (draggingIndex == null) {
                    draggingIndex = index
                    dragOffset = 0f
                }
                if (draggingIndex == index) {
                    dragOffset += amount
                }
            },
            onDragEnd = {
                val fromIndex = draggingIndex
                val height = rowHeight
                if (fromIndex != null && height > 0) {
                    val delta = (dragOffset / height).roundToInt()
                    val toIndex = (fromIndex + delta).coerceIn(0, photos.lastIndex)
                    if (toIndex != fromIndex) {
                        onReorder(photos[fromIndex].photoId, fromIndex, toIndex)
                    }
                }
                draggingIndex = null
                dragOffset = 0f
            },
        )
    }
}

@Composable
private fun PhotoManagementRow(
    modifier: Modifier = Modifier,
    photo: ItemDetailPhoto,
    locationPath: String,
    resolveMediaPath: (String) -> String?,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    submitting: Boolean,
    dragEnabled: Boolean = false,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable(enabled = !submitting, role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = WhereSurfaceColor,
        border = BorderStroke(1.dp, if (photo.isCover) WherePrimaryColor else WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 92.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(11.dp),
                color = WhereSelectedContainerColor,
            ) {
                LocalStorageImage(
                    absolutePath = resolveMediaPath(photo.thumbnailStorageKey)
                        ?: resolveMediaPath(photo.storageKey),
                    contentDescription = photoManagementRoleLabel(photo.role),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        modifier = Modifier.padding(18.dp),
                        imageVector = photoManagementRoleIcon(photo.role),
                        contentDescription = null,
                        tint = WherePrimaryColor,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = photoManagementRoleLabel(photo.role),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (photo.isCover) {
                        Surface(
                            modifier = Modifier.padding(start = 7.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = WhereSelectedContainerColor,
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                text = "封面",
                                color = WherePrimaryColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = locationPath,
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    modifier = Modifier
                        .size(28.dp)
                        .pointerInput(dragEnabled, photo.photoId) {
                            if (!dragEnabled) {
                                return@pointerInput
                            }
                            detectDragGesturesAfterLongPress(
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                            )
                        },
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = "拖动调整顺序",
                    tint = WhereSecondaryTextColor,
                )
                Row {
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                enabled = !submitting && canMovePrevious,
                                role = Role.Button,
                            ) { onMove(-1) }
                            .padding(4.dp),
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "前移",
                        tint = if (canMovePrevious) WherePrimaryTextColor else WhereOutlineColor,
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                enabled = !submitting && canMoveNext,
                                role = Role.Button,
                            ) { onMove(1) }
                            .padding(4.dp),
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "后移",
                        tint = if (canMoveNext) WherePrimaryTextColor else WhereOutlineColor,
                    )
                }
                Icon(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            enabled = !submitting,
                            role = Role.Button,
                            onClick = onDelete,
                        )
                        .padding(7.dp),
                    imageVector = WhereIcons.Delete,
                    contentDescription = "删除照片",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PhotoManagementAddRoleDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PhotoRole) -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "添加照片",
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    ) {
        Text(
            text = "请选择这张照片的用途。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        PhotoRole.entries.forEach { role ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clickable(
                        enabled = !submitting,
                        role = Role.Button,
                    ) { onConfirm(role) },
                shape = RoundedCornerShape(16.dp),
                color = WhereSurfaceColor,
                border = BorderStroke(1.dp, WhereOutlineColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(28.dp),
                        imageVector = photoManagementRoleIcon(role),
                        contentDescription = photoManagementRoleLabel(role),
                        tint = WherePrimaryColor,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = photoManagementRoleLabel(role),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            text = photoManagementRoleHint(role),
                            color = WhereSecondaryTextColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoManagementEditDialog(
    photo: ItemDetailPhoto,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSetCover: () -> Unit,
    onSetRole: (PhotoRole) -> Unit,
    onDelete: () -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "编辑照片",
        confirmText = "设为封面",
        onConfirm = onSetCover,
        confirmEnabled = !submitting && !photo.isCover,
        dismissText = "关闭",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
        neutralText = "删除",
        onNeutral = onDelete,
        neutralEnabled = !submitting,
    ) {
        Text("当前：${photoManagementRoleLabel(photo.role)}")
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PhotoRole.entries.forEach { role ->
                val selected = role == photo.role
                Surface(
                    modifier = Modifier.clickable(
                        enabled = !submitting && !selected,
                        role = Role.Button,
                    ) { onSetRole(role) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
                    border = BorderStroke(
                        1.dp,
                        if (selected) WherePrimaryColor else WhereOutlineColor,
                    ),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        text = photoManagementRoleLabel(role),
                        color = if (selected) WherePrimaryColor else WherePrimaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (canMovePrevious || canMoveNext) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "顺序可在列表里按住拖条调整。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun photoManagementRoleLabel(role: PhotoRole): String = when (role) {
    PhotoRole.ENVIRONMENT -> "环境照"
    PhotoRole.ITEM -> "物品照"
    PhotoRole.LABEL -> "标签照"
    PhotoRole.SUPPLEMENTARY -> "补充照"
}

private fun photoManagementRoleHint(role: PhotoRole): String = when (role) {
    PhotoRole.ENVIRONMENT -> "拍房间、柜子或盒子"
    PhotoRole.ITEM -> "拍清楚物品外观"
    PhotoRole.LABEL -> "拍包装文字或铭牌"
    PhotoRole.SUPPLEMENTARY -> "补充其他角度"
}

private fun photoManagementRoleIcon(role: PhotoRole): ImageVector = when (role) {
    PhotoRole.ENVIRONMENT -> WhereIcons.Location
    PhotoRole.ITEM -> WhereIcons.Camera
    PhotoRole.LABEL -> Icons.Outlined.LocalOffer
    PhotoRole.SUPPLEMENTARY -> WhereIcons.Image
}
