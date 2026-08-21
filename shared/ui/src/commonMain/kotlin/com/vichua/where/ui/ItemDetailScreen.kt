package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.feature.item.detail.ItemDetail
import com.vichua.where.feature.item.detail.ItemDetailPhoto

/**
 * 按 Pencil 原型展示物品照片、当前位置、历史和主要操作。
 *
 * @param detail 已加载的物品详情；加载中或失败时为空。
 * @param resolveMediaPath 把受控照片标识解析为本地绝对路径。
 * @param loading 是否正在读取详情。
 * @param errorMessage 可展示的中文读取错误。
 * @param onBack 返回上一页。
 * @param onReadLocation 朗读当前位置；朗读成功后再听一遍也走同一入口。
 * @param onUpdateLocation 打开更新位置页。
 * @param speechSubmitting 是否正在朗读。
 * @param speechErrorMessage 可展示的中文朗读错误。
 * @param canRepeatSpeech 用户主动朗读成功后，是否展示再听一遍。
 * @param formattedUpdatedAt 当前位置的本地更新时间文本。
 * @param shareSubmitting 是否正在打开系统分享。
 * @param shareErrorMessage 可展示的中文分享错误。
 * @param onShareLocation 预览确认后分享名称、位置、可选更新时间和选定照片。
 * @param editorVisible 是否展示档案编辑对话框。
 * @param editorSubmitting 是否正在保存档案。
 * @param editorErrorMessage 可展示的中文保存错误。
 * @param onEditProfile 打开档案编辑对话框。
 * @param onDismissEditor 关闭档案编辑对话框。
 * @param onSaveProfile 保存名称、位置说明和备注。
 * @param photoSubmitting 是否正在保存照片变更。
 * @param photoErrorMessage 可展示的中文照片错误。
 * @param onAddPhoto 选择用途后从相册追加照片。
 * @param onSetPhotoCover 把指定照片设为封面。
 * @param onSetPhotoRole 修改指定照片用途。
 * @param onMovePhoto 与相邻照片交换画廊顺序。
 * @param onDeletePhoto 软删除指定照片。
 * @param deletionSubmitting 是否正在删除物品。
 * @param deletionErrorMessage 可展示的中文删除错误。
 * @param onDeleteItem 二次确认后删除当前物品。
 * @param elderFriendlyMode 是否优先展示主图、位置和朗读/更新，并把次要操作折进更多信息。
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
    speechSubmitting: Boolean,
    speechErrorMessage: String?,
    canRepeatSpeech: Boolean,
    formattedUpdatedAt: String,
    shareSubmitting: Boolean,
    shareErrorMessage: String?,
    onShareLocation: (Boolean, List<PhotoAssetId>) -> Unit,
    editorVisible: Boolean,
    editorSubmitting: Boolean,
    editorErrorMessage: String?,
    onEditProfile: () -> Unit,
    onDismissEditor: () -> Unit,
    onSaveProfile: (String, String?, String?) -> Unit,
    photoSubmitting: Boolean,
    photoErrorMessage: String?,
    onAddPhoto: (PhotoRole) -> Unit,
    onOpenPhotoManagement: () -> Unit,
    onSetPhotoCover: (PhotoAssetId) -> Unit,
    onSetPhotoRole: (PhotoAssetId, PhotoRole) -> Unit,
    onMovePhoto: (PhotoAssetId, Int) -> Unit,
    onDeletePhoto: (PhotoAssetId) -> Unit,
    deletionSubmitting: Boolean,
    deletionErrorMessage: String?,
    onDeleteItem: () -> Unit,
    elderFriendlyMode: Boolean = false,
) {
    var selectedPhotoIndex by remember(detail?.itemId) { mutableIntStateOf(0) }
    var lastPhotoCount by remember(detail?.itemId) { mutableIntStateOf(detail?.photos?.size ?: 0) }
    var addRoleDialogVisible by remember { mutableStateOf(false) }
    var managedPhotoId by remember { mutableStateOf<PhotoAssetId?>(null) }
    var deletePhotoId by remember { mutableStateOf<PhotoAssetId?>(null) }
    var deleteItemConfirmVisible by remember { mutableStateOf(false) }
    var shareDialogVisible by remember { mutableStateOf(false) }
    var fullscreenVisible by remember { mutableStateOf(false) }
    var moreInformationExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(detail?.photos?.size) {
        val photoCount = detail?.photos?.size ?: 0
        if (photoCount > lastPhotoCount) {
            selectedPhotoIndex = photoCount - 1
        } else if (photoCount == 0) {
            selectedPhotoIndex = 0
        } else if (selectedPhotoIndex >= photoCount) {
            selectedPhotoIndex = photoCount - 1
        }
        lastPhotoCount = photoCount
    }

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
                .padding(top = 10.dp)
                .clickable(
                    enabled = detail.photos.isNotEmpty(),
                    role = Role.Button,
                    onClick = {
                        fullscreenVisible = true
                    },
                ),
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
        if (!elderFriendlyMode) {
            ItemDetailPhotoManagement(
                detail = detail,
                selectedPhotoIndex = selectedPhotoIndex,
                resolveMediaPath = resolveMediaPath,
                photoSubmitting = photoSubmitting,
                photoErrorMessage = photoErrorMessage,
                onSelectPhoto = { index ->
                    selectedPhotoIndex = index
                },
                onManagePhoto = { photoId ->
                    managedPhotoId = photoId
                },
                onOpenPhotoManagement = onOpenPhotoManagement,
                onAddPhoto = {
                    addRoleDialogVisible = true
                },
            )
        }

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
            if (!elderFriendlyMode) {
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
                    text = visibleLocationPath(detail.locationPath),
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

        if (!elderFriendlyMode) {
            ItemDetailNoteAndHistory(detail = detail)
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
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhereSurfaceColor,
                    contentColor = WherePrimaryTextColor,
                ),
                enabled = !speechSubmitting && !shareSubmitting && !deletionSubmitting,
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
                    .heightIn(min = 52.dp),
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
        if (elderFriendlyMode || canRepeatSpeech) {
            TextButton(
                enabled = !speechSubmitting,
                onClick = onReadLocation,
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = WhereIcons.Repeat,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = "再听一遍",
                )
            }
        }
        if (speechErrorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = speechErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (elderFriendlyMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            moreInformationExpanded = !moreInformationExpanded
                        },
                    ),
                color = WhereSurfaceColor,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, WhereOutlineColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "更多信息",
                        color = WherePrimaryTextColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        imageVector = if (moreInformationExpanded) {
                            WhereIcons.ExpandLess
                        } else {
                            WhereIcons.ExpandMore
                        },
                        contentDescription = if (moreInformationExpanded) "收起" else "展开",
                        tint = WherePrimaryTextColor,
                    )
                }
            }
            if (moreInformationExpanded) {
                ItemDetailPhotoManagement(
                    detail = detail,
                    selectedPhotoIndex = selectedPhotoIndex,
                    resolveMediaPath = resolveMediaPath,
                    photoSubmitting = photoSubmitting,
                    photoErrorMessage = photoErrorMessage,
                    onSelectPhoto = { index ->
                        selectedPhotoIndex = index
                    },
                    onManagePhoto = { photoId ->
                        managedPhotoId = photoId
                    },
                    onOpenPhotoManagement = onOpenPhotoManagement,
                    onAddPhoto = {
                        addRoleDialogVisible = true
                    },
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
                ItemDetailNoteAndHistory(detail = detail)
                ItemDetailShareAndDelete(
                    shareSubmitting = shareSubmitting,
                    speechSubmitting = speechSubmitting,
                    deletionSubmitting = deletionSubmitting,
                    photoSubmitting = photoSubmitting,
                    editorSubmitting = editorSubmitting,
                    shareErrorMessage = shareErrorMessage,
                    deletionErrorMessage = deletionErrorMessage,
                    onShare = {
                        shareDialogVisible = true
                    },
                    onDelete = {
                        deleteItemConfirmVisible = true
                    },
                )
            }
        } else {
            ItemDetailShareAndDelete(
                shareSubmitting = shareSubmitting,
                speechSubmitting = speechSubmitting,
                deletionSubmitting = deletionSubmitting,
                photoSubmitting = photoSubmitting,
                editorSubmitting = editorSubmitting,
                shareErrorMessage = shareErrorMessage,
                deletionErrorMessage = deletionErrorMessage,
                onShare = {
                    shareDialogVisible = true
                },
                onDelete = {
                    deleteItemConfirmVisible = true
                },
            )
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
    if (addRoleDialogVisible && detail != null) {
        AddPhotoRoleDialog(
            submitting = photoSubmitting,
            onDismiss = {
                if (!photoSubmitting) {
                    addRoleDialogVisible = false
                }
            },
            onConfirm = { role ->
                addRoleDialogVisible = false
                onAddPhoto(role)
            },
        )
    }
    val managedPhotos = detail?.photos
    val managedPhoto = managedPhotos?.firstOrNull { photo -> photo.photoId == managedPhotoId }
    if (managedPhotos != null && managedPhoto != null) {
        ManagePhotoDialog(
            photo = managedPhoto,
            canMovePrevious = managedPhotos.indexOf(managedPhoto) > 0,
            canMoveNext = managedPhotos.indexOf(managedPhoto) < managedPhotos.lastIndex,
            submitting = photoSubmitting,
            onDismiss = {
                if (!photoSubmitting) {
                    managedPhotoId = null
                }
            },
            onSetCover = {
                onSetPhotoCover(managedPhoto.photoId)
            },
            onSetRole = { role ->
                onSetPhotoRole(managedPhoto.photoId, role)
            },
            onMove = { offset ->
                onMovePhoto(managedPhoto.photoId, offset)
            },
            onDelete = {
                deletePhotoId = managedPhoto.photoId
            },
        )
    }
    val pendingDeletePhoto = detail?.photos?.firstOrNull { photo -> photo.photoId == deletePhotoId }
    if (pendingDeletePhoto != null) {
        WhereDialog(
            onDismissRequest = {
                if (!photoSubmitting) {
                    deletePhotoId = null
                }
            },
            title = "删除照片",
            confirmText = "删除",
            onConfirm = {
                val photoId = pendingDeletePhoto.photoId
                deletePhotoId = null
                managedPhotoId = null
                onDeletePhoto(photoId)
            },
            confirmEnabled = !photoSubmitting,
            confirmDestructive = true,
            dismissText = "取消",
            onDismiss = {
                deletePhotoId = null
            },
            dismissEnabled = !photoSubmitting,
        ) {
            Text(
                text = "删除后这张${photoRoleLabel(pendingDeletePhoto.role)}将不再显示。物品档案会保留。",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (shareDialogVisible && detail != null) {
        ShareLocationDialog(
            detail = detail,
            formattedUpdatedAt = formattedUpdatedAt,
            submitting = shareSubmitting,
            onDismiss = {
                if (!shareSubmitting) {
                    shareDialogVisible = false
                }
            },
            onConfirm = { includeUpdatedAt, selectedPhotoIds ->
                shareDialogVisible = false
                onShareLocation(includeUpdatedAt, selectedPhotoIds)
            },
        )
    }
    if (deleteItemConfirmVisible && detail != null) {
        WhereDialog(
            onDismissRequest = {
                if (!deletionSubmitting) {
                    deleteItemConfirmVisible = false
                }
            },
            title = "删除物品",
            confirmText = "删除",
            onConfirm = {
                deleteItemConfirmVisible = false
                onDeleteItem()
            },
            confirmEnabled = !deletionSubmitting,
            confirmDestructive = true,
            dismissText = "取消",
            onDismiss = {
                deleteItemConfirmVisible = false
            },
            dismissEnabled = !deletionSubmitting,
        ) {
            Text(
                text = "删除后「${detail.name}」将从首页和搜索中消失。当前会话内可在 ${MvpLimits.DELETE_UNDO_WINDOW_SECONDS} 秒内撤销。",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (fullscreenVisible && detail != null && detail.photos.isNotEmpty()) {
        PhotoFullscreenDialog(
            photos = detail.photos,
            selectedIndex = selectedPhotoIndex.coerceAtMost(detail.photos.lastIndex),
            resolveMediaPath = resolveMediaPath,
            onSelect = { index ->
                selectedPhotoIndex = index
            },
            onDismiss = {
                fullscreenVisible = false
            },
        )
    }
}

/**
 * 分享前预览文本，并让用户选择是否带上更新时间和选定照片。
 *
 * 默认只分享名称和位置，避免误把照片或完整家庭数据送出应用。
 */
@Composable
private fun ShareLocationDialog(
    detail: ItemDetail,
    formattedUpdatedAt: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, List<PhotoAssetId>) -> Unit,
) {
    var includeUpdatedAt by remember { mutableStateOf(true) }
    var includePhotos by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember {
        mutableStateOf(
            detail.photos.firstOrNull { photo -> photo.isCover }?.photoId
                ?.let { photoId -> setOf(photoId) }
                ?: emptySet(),
        )
    }
    val previewText = buildString {
        append(detail.name)
        append('\n')
        append(visibleLocationPath(detail.locationPath))
        val locationDescription = detail.locationDescription
        if (!locationDescription.isNullOrBlank()) {
            append('\n')
            append(locationDescription)
        }
        if (includeUpdatedAt && formattedUpdatedAt.isNotBlank()) {
            append('\n')
            append(formattedUpdatedAt)
        }
    }
    val canSharePhotos = includePhotos && selectedPhotoIds.isNotEmpty()
    val confirmEnabled = !submitting && (!includePhotos || canSharePhotos)

    WhereDialog(
        onDismissRequest = {
            if (!submitting) {
                onDismiss()
            }
        },
        title = "分享位置",
        confirmText = "系统分享",
        onConfirm = {
            onConfirm(
                includeUpdatedAt,
                if (includePhotos) selectedPhotoIds.toList() else emptyList(),
            )
        },
        confirmEnabled = confirmEnabled,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    ) {
                Text("预览")
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = previewText,
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ShareOptionRow(
                    label = "包含更新时间",
                    checked = includeUpdatedAt,
                    enabled = !submitting,
                    onCheckedChange = { checked ->
                        includeUpdatedAt = checked
                    },
                )
                if (detail.photos.isNotEmpty()) {
                    ShareOptionRow(
                        label = "同时分享选定照片",
                        checked = includePhotos,
                        enabled = !submitting,
                        onCheckedChange = { checked ->
                            includePhotos = checked
                            if (checked && selectedPhotoIds.isEmpty()) {
                                selectedPhotoIds = detail.photos.firstOrNull { photo ->
                                    photo.isCover
                                }?.photoId?.let { photoId -> setOf(photoId) }
                                    ?: setOf(detail.photos.first().photoId)
                            }
                        },
                    )
                }
                if (includePhotos && detail.photos.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "选择要分享的照片",
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    detail.photos.forEach { photo ->
                        val selected = selectedPhotoIds.contains(photo.photoId)
                        ShareOptionRow(
                            label = photoRoleLabel(photo.role) + if (photo.isCover) " · 封面" else "",
                            checked = selected,
                            enabled = !submitting,
                            onCheckedChange = { checked ->
                                selectedPhotoIds = if (checked) {
                                    selectedPhotoIds + photo.photoId
                                } else {
                                    selectedPhotoIds - photo.photoId
                                }
                            },
                        )
                    }
                }
                Text(
                    modifier = Modifier.padding(top = 10.dp),
                    text = "不会分享完整家庭数据。",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
    }
}

/**
 * 分享预览中的勾选项，避免只用图标表达是否包含时间和照片。
 */
@Composable
private fun ShareOptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            color = WherePrimaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 照片管理、缩略图和添加入口；适老详情把这块折进更多信息。
 */
@Composable
private fun ItemDetailPhotoManagement(
    detail: ItemDetail,
    selectedPhotoIndex: Int,
    resolveMediaPath: (String) -> String?,
    photoSubmitting: Boolean,
    photoErrorMessage: String?,
    onSelectPhoto: (Int) -> Unit,
    onManagePhoto: (PhotoAssetId) -> Unit,
    onOpenPhotoManagement: () -> Unit,
    onAddPhoto: () -> Unit,
) {
    if (detail.photos.isNotEmpty()) {
        val selectedPhoto = detail.photos[selectedPhotoIndex.coerceAtMost(detail.photos.lastIndex)]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                text = "${photoRoleLabel(selectedPhoto.role)} · ${selectedPhotoIndex + 1} / ${detail.photos.size}",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                enabled = !photoSubmitting,
                onClick = onOpenPhotoManagement,
            ) {
                Text("管理照片")
            }
        }
    }
    PhotoThumbnailSelector(
        photos = detail.photos,
        selectedIndex = selectedPhotoIndex,
        resolveMediaPath = resolveMediaPath,
        enabled = !photoSubmitting,
        onSelect = onSelectPhoto,
        onManage = onManagePhoto,
        onAdd = onAddPhoto,
    )
    if (detail.photos.size >= MvpLimits.ITEM_PHOTO_WARNING_THRESHOLD) {
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "当前已有 ${detail.photos.size} 张照片，存储占用会继续增加。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (photoErrorMessage != null) {
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = photoErrorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 备注和位置历史摘要；适老详情默认折叠。
 */
@Composable
private fun ItemDetailNoteAndHistory(
    detail: ItemDetail,
) {
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
            .heightIn(min = 52.dp),
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
}

/**
 * 分享和删除入口；适老详情默认折叠，避免误触危险操作。
 */
@Composable
private fun ItemDetailShareAndDelete(
    shareSubmitting: Boolean,
    speechSubmitting: Boolean,
    deletionSubmitting: Boolean,
    photoSubmitting: Boolean,
    editorSubmitting: Boolean,
    shareErrorMessage: String?,
    deletionErrorMessage: String?,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .heightIn(min = 52.dp),
        enabled = !shareSubmitting && !speechSubmitting && !deletionSubmitting,
        colors = ButtonDefaults.buttonColors(
            containerColor = WhereSurfaceColor,
            contentColor = WherePrimaryTextColor,
        ),
        onClick = onShare,
    ) {
        Icon(
            imageVector = WhereIcons.Share,
            contentDescription = null,
        )
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = "分享位置",
        )
    }
    if (shareErrorMessage != null) {
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = shareErrorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (deletionErrorMessage != null) {
        Text(
            modifier = Modifier.padding(top = 10.dp),
            text = deletionErrorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .heightIn(min = 52.dp),
        enabled = !deletionSubmitting && !photoSubmitting && !editorSubmitting,
        colors = ButtonDefaults.buttonColors(
            containerColor = WhereSurfaceColor,
            contentColor = MaterialTheme.colorScheme.error,
        ),
        onClick = onDelete,
    ) {
        Text("删除物品")
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

    WhereDialog(
        onDismissRequest = {
            if (!submitting) {
                onDismiss()
            }
        },
        title = "编辑物品",
        confirmText = "保存",
        onConfirm = {
            onConfirm(trimmedName, trimmedLocationDescription, trimmedNote)
        },
        confirmEnabled = !submitting && trimmedName.isNotEmpty() && hasVisibleChange,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    ) {
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
}

/**
 * 展示可选择的照片缩略图和继续添加入口。
 *
 * 短按切换当前照片，长按进入管理，避免和选择手势冲突。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PhotoThumbnailSelector(
    photos: List<ItemDetailPhoto>,
    selectedIndex: Int,
    resolveMediaPath: (String) -> String?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onManage: (PhotoAssetId) -> Unit,
    onAdd: () -> Unit,
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
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = {
                            onSelect(index)
                        },
                        onLongClick = {
                            onManage(photo.photoId)
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
                Box(modifier = Modifier.fillMaxSize()) {
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
                    if (photo.isCover) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp),
                            text = "封面",
                            color = WherePrimaryColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .size(width = 82.dp, height = 58.dp)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onAdd,
                ),
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
 * 追加照片前先确认用途，避免相册选择后再打断导入流程。
 */
@Composable
private fun AddPhotoRoleDialog(
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
                        onClick = {
                            onConfirm(role)
                        },
                    ),
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
                        imageVector = photoRoleIcon(role),
                        contentDescription = photoRoleLabel(role),
                        tint = WherePrimaryColor,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = photoRoleLabel(role),
                            color = WherePrimaryTextColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            text = photoRoleHint(role),
                            color = WhereSecondaryTextColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 管理当前照片的用途、封面、顺序和删除。
 */
@Composable
private fun ManagePhotoDialog(
    photo: ItemDetailPhoto,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSetCover: () -> Unit,
    onSetRole: (PhotoRole) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    WhereDialog(
        onDismissRequest = onDismiss,
        title = "管理照片",
        confirmText = "删除",
        onConfirm = onDelete,
        confirmEnabled = !submitting,
        confirmDestructive = true,
        dismissText = "关闭",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    ) {
                Text("当前：${photoRoleLabel(photo.role)}")
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
                                onClick = {
                                    onSetRole(role)
                                },
                            ),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                WhereSelectedContainerColor
                            } else {
                                WhereSurfaceColor
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) WherePrimaryColor else WhereOutlineColor,
                            ),
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                text = photoRoleLabel(role),
                                color = if (selected) {
                                    WherePrimaryColor
                                } else {
                                    WherePrimaryTextColor
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        enabled = !submitting && !photo.isCover,
                        onClick = onSetCover,
                    ) {
                        Text(if (photo.isCover) "已是封面" else "设为封面")
                    }
                    TextButton(
                        enabled = !submitting && canMovePrevious,
                        onClick = {
                            onMove(-1)
                        },
                    ) {
                        Text("前移")
                    }
                    TextButton(
                        enabled = !submitting && canMoveNext,
                        onClick = {
                            onMove(1)
                        },
                    ) {
                        Text("后移")
                    }
                }
    }
}

/**
 * 全屏查看当前照片，并允许左右切换画廊。
 */
@Composable
private fun PhotoFullscreenDialog(
    photos: List<ItemDetailPhoto>,
    selectedIndex: Int,
    resolveMediaPath: (String) -> String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val photo = photos[selectedIndex]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClick = onDismiss),
            color = WherePrimaryTextColor.copy(alpha = 0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${photoRoleLabel(photo.role)} · ${selectedIndex + 1} / ${photos.size}",
                    color = WhereSurfaceColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LocalStorageImage(
                    absolutePath = resolveMediaPath(photo.storageKey)
                        ?: resolveMediaPath(photo.thumbnailStorageKey),
                    contentDescription = photoRoleLabel(photo.role),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            modifier = Modifier.size(52.dp),
                            imageVector = WhereIcons.Image,
                            contentDescription = null,
                            tint = WhereSurfaceColor,
                        )
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "照片文件暂不可用",
                            color = WhereSurfaceColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        enabled = selectedIndex > 0,
                        onClick = {
                            onSelect(selectedIndex - 1)
                        },
                    ) {
                        Text("上一张", color = WhereSurfaceColor)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = WhereSurfaceColor)
                    }
                    TextButton(
                        enabled = selectedIndex < photos.lastIndex,
                        onClick = {
                            onSelect(selectedIndex + 1)
                        },
                    ) {
                        Text("下一张", color = WhereSurfaceColor)
                    }
                }
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

/**
 * 用途卡片上的简短说明，避免只有四个字看不出该拍什么。
 */
private fun photoRoleHint(role: PhotoRole): String = when (role) {
    PhotoRole.ENVIRONMENT -> "拍房间、柜子或盒子"
    PhotoRole.ITEM -> "拍清楚物品外观"
    PhotoRole.LABEL -> "拍包装文字或铭牌"
    PhotoRole.SUPPLEMENTARY -> "补充其他角度"
}

/**
 * 用途卡片图标；标签照没有现成共享图标，用价签图标区分。
 */
private fun photoRoleIcon(role: PhotoRole): ImageVector = when (role) {
    PhotoRole.ENVIRONMENT -> WhereIcons.Location
    PhotoRole.ITEM -> WhereIcons.Camera
    PhotoRole.LABEL -> Icons.Outlined.LocalOffer
    PhotoRole.SUPPLEMENTARY -> WhereIcons.Image
}
