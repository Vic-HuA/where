package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.feature.item.creation.CreateManualItemRequest
import com.vichua.where.feature.item.creation.ItemCreationContext
import com.vichua.where.feature.item.creation.ItemCreationLocation
import com.vichua.where.feature.item.draft.ItemDraftContent
import com.vichua.where.core.platform.AiFieldSuggestions
import com.vichua.where.feature.item.photo.ImportedItemPhoto

/**
 * 按 Pencil 原型展示新增物品基础页面，并支持不依赖相机、语音或 AI 的手动保存路径。
 *
 * @param context 当前家庭和可选位置。
 * @param loading 是否正在读取位置。
 * @param submitting 是否正在保存物品。
 * @param errorMessage 可展示的中文错误。
 * @param onRetry 重试读取可选位置。
 * @param draft 当前设备可恢复的未过期草稿；没有时为空。
 * @param photos 本次已导入但尚未正式保存的照片。
 * @param resolveMediaPath 把受控标识解析为本地绝对路径。
 * @param onBack 返回首页。
 * @param onPickPhoto 从相册导入指定用途的照片。
 * @param onSaveDraft 保存当前未完成输入为设备本地草稿。
 * @param onDiscardDraft 放弃当前草稿并离开页面。
 * @param onSubmit 确认后提交基础手动物品请求。
 * @param elderFriendlyMode 是否突出拍物品、拍存放位置、说一句和继续确认。
 * @param onSpeakRequested 用户主动说话后返回转写文字；取消或失败时为空。
 * @param onAiRecognizeRequested 用户主动选择识别后返回建议；取消或失败时为空，不得自动保存。
 */
@Composable
fun AddItemScreen(
    context: ItemCreationContext?,
    draft: ItemDraftContent?,
    photos: List<ImportedItemPhoto>,
    resolveMediaPath: (String) -> String?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPickPhoto: (PhotoRole) -> Unit,
    onSaveDraft: (ItemDraftContent) -> Unit,
    onDiscardDraft: () -> Unit,
    onSubmit: (CreateManualItemRequest) -> Unit,
    elderFriendlyMode: Boolean = false,
    onSpeakRequested: suspend () -> String? = { null },
    onAiRecognizeRequested: suspend () -> AiFieldSuggestions? = { null },
) {
    val speakScope = rememberCoroutineScope()
    var speechSubmitting by remember { mutableStateOf(false) }
    var aiSubmitting by remember { mutableStateOf(false) }
    var pendingAiSuggestions by remember { mutableStateOf<AiFieldSuggestions?>(null) }
    var itemName by remember(draft) { mutableStateOf(draft?.name.orEmpty()) }
    var selectedLocationId by remember(draft) { mutableStateOf(draft?.locationId) }
    var locationDescription by remember(draft) {
        mutableStateOf(draft?.locationDescription.orEmpty())
    }
    var note by remember(draft) { mutableStateOf(draft?.note.orEmpty()) }
    var moreInformationExpanded by remember(draft) {
        mutableStateOf(
            !draft?.locationDescription.isNullOrBlank() || !draft?.note.isNullOrBlank(),
        )
    }
    var locationDialogVisible by remember { mutableStateOf(false) }
    var confirmationDialogVisible by remember { mutableStateOf(false) }
    var leaveDialogVisible by remember { mutableStateOf(false) }
    val selectedLocation = context?.availableLocations?.singleOrNull { location ->
        location.locationId == selectedLocationId
    }
    val canContinue = itemName.isNotBlank() &&
        selectedLocation != null &&
        !loading &&
        !submitting
    val currentDraft = ItemDraftContent(
        name = itemName,
        locationId = selectedLocationId,
        locationDescription = locationDescription,
        note = note,
    )
    val requestAiRecognize: () -> Unit = {
        if (!aiSubmitting && !submitting && !speechSubmitting) {
            speakScope.launch {
                aiSubmitting = true
                try {
                    pendingAiSuggestions = onAiRecognizeRequested()
                } finally {
                    aiSubmitting = false
                }
            }
        }
    }
    val requestSpeech: () -> Unit = {
        if (!speechSubmitting && !submitting) {
            speakScope.launch {
                speechSubmitting = true
                try {
                    val spoken = onSpeakRequested()
                    if (!spoken.isNullOrBlank()) {
                        itemName = spoken
                    }
                } finally {
                    speechSubmitting = false
                }
            }
        }
    }
    val requestLeave: () -> Unit = {
        if (currentDraft.hasUserInput || photos.isNotEmpty()) {
            leaveDialogVisible = true
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        AddItemHeader(onBack = requestLeave)
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "拍照或说一句，确认名称和位置后保存。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (draft != null && draft.hasUserInput) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "已恢复未完成草稿，可继续编辑或放弃。",
                color = WherePrimaryColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!elderFriendlyMode) {
            AddItemSteps(modifier = Modifier.padding(top = 18.dp))
        }
        if (elderFriendlyMode) {
            ElderAddItemActions(
                modifier = Modifier.padding(top = 18.dp),
                enabled = !submitting,
                canContinue = canContinue,
                onPickItemPhoto = {
                    onPickPhoto(PhotoRole.ITEM)
                },
                onPickLocationPhoto = {
                    onPickPhoto(PhotoRole.ENVIRONMENT)
                },
                onSpeak = requestSpeech,
                onContinue = {
                    confirmationDialogVisible = true
                },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PhotoActionCard(
                    modifier = Modifier.weight(1f),
                    icon = WhereIcons.Location,
                    title = "拍存放位置 ＋",
                    description = "拍房间、柜子或盒子",
                    enabled = !submitting,
                    onClick = {
                        onPickPhoto(PhotoRole.ENVIRONMENT)
                    },
                )
                PhotoActionCard(
                    modifier = Modifier.weight(1f),
                    icon = WhereIcons.AddPhoto,
                    title = "拍物品 ＋",
                    description = "拍清楚物品外观",
                    enabled = !submitting,
                    onClick = {
                        onPickPhoto(PhotoRole.ITEM)
                    },
                )
            }
        }
        PhotoThumbnailRow(
            modifier = Modifier.padding(top = 12.dp),
            photos = photos,
            resolveMediaPath = resolveMediaPath,
            enabled = !submitting,
            onPickPhoto = onPickPhoto,
        )
        if (photos.size >= MvpLimits.ITEM_PHOTO_WARNING_THRESHOLD) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "当前已有 ${photos.size} 张照片，存储占用会继续增加。",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(min = 52.dp)
                .clickable(
                    enabled = !submitting && !speechSubmitting && !aiSubmitting,
                    role = Role.Button,
                    onClick = requestAiRecognize,
                ),
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = WhereIcons.Image,
                    contentDescription = "AI 识别这次选中的照片",
                    tint = WherePrimaryColor,
                )
                Text(
                    text = if (aiSubmitting) {
                        "正在识别…"
                    } else {
                        "AI 识别这次选中的照片"
                    },
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        AddItemFieldLabel(
            modifier = Modifier.padding(top = 18.dp),
            text = "物品名称",
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 52.dp),
            value = itemName,
            onValueChange = { value ->
                itemName = value
            },
            enabled = !submitting,
            placeholder = {
                Text("等待识别或手动输入")
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = addItemTextFieldColors(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        AddItemFieldLabel(
            modifier = Modifier.padding(top = 16.dp),
            text = "所在位置",
        )
        LocationSelectionField(
            modifier = Modifier.padding(top = 6.dp),
            selectedLocation = selectedLocation,
            enabled = !loading && !submitting && context != null,
            onClick = {
                locationDialogVisible = true
            },
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 48.dp)
                .clickable(
                    enabled = !submitting,
                    role = Role.Button,
                    onClick = {
                        moreInformationExpanded = !moreInformationExpanded
                    },
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (moreInformationExpanded) "已展开" else "已收起"
                },
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "更多信息 · 位置说明、备注",
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (moreInformationExpanded) "⌃" else "⌄",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (moreInformationExpanded) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                value = locationDescription,
                onValueChange = { value ->
                    locationDescription = value
                },
                enabled = !submitting,
                label = {
                    Text("位置补充说明")
                },
                shape = RoundedCornerShape(14.dp),
                colors = addItemTextFieldColors(),
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                value = note,
                onValueChange = { value ->
                    note = value
                },
                enabled = !submitting,
                label = {
                    Text("备注")
                },
                shape = RoundedCornerShape(14.dp),
                colors = addItemTextFieldColors(),
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 64.dp)
                .clickable(
                    enabled = !submitting && !speechSubmitting && !aiSubmitting,
                    role = Role.Button,
                    onClick = requestSpeech,
                ),
            color = WhereSelectedContainerColor,
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = WhereIcons.Microphone,
                    contentDescription = "说一句填写名称",
                    tint = WherePrimaryColor,
                )
                Text(
                    text = if (speechSubmitting) {
                        "正在听，请说话…"
                    } else {
                        "按住说：放在书柜第二层蓝色盒子"
                    },
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 10.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }

        if (!elderFriendlyMode) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 52.dp),
                enabled = canContinue,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WherePrimaryColor,
                    contentColor = WhereSurfaceColor,
                ),
                onClick = {
                    confirmationDialogVisible = true
                },
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = WhereSurfaceColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "继续确认",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (locationDialogVisible && context != null) {
        LocationSelectionDialog(
            locations = context.availableLocations,
            onDismiss = {
                locationDialogVisible = false
            },
            onSelect = { location ->
                selectedLocationId = location.locationId
                locationDialogVisible = false
            },
        )
    }

    if (pendingAiSuggestions != null) {
        val suggestions = pendingAiSuggestions
        if (suggestions != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingAiSuggestions = null
                },
                title = { Text("确认 AI 建议") },
                text = {
                    Column {
                        Text("这些内容还不会保存。采用后写入当前表单，仍可再改。")
                        if (!suggestions.itemName.isNullOrBlank()) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                text = "物品名称：${suggestions.itemName}",
                            )
                        }
                        if (!suggestions.locationDescription.isNullOrBlank()) {
                            Text(
                                modifier = Modifier.padding(top = 6.dp),
                                text = "位置说明：${suggestions.locationDescription}",
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val suggestedName = suggestions.itemName
                            val suggestedLocationDescription = suggestions.locationDescription
                            if (!suggestedName.isNullOrBlank()) {
                                itemName = suggestedName
                            }
                            if (!suggestedLocationDescription.isNullOrBlank()) {
                                locationDescription = suggestedLocationDescription
                                moreInformationExpanded = true
                            }
                            pendingAiSuggestions = null
                        },
                    ) {
                        Text("采用")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingAiSuggestions = null
                        },
                    ) {
                        Text("不用")
                    }
                },
            )
        }
    }

    if (leaveDialogVisible) {
        LeaveAddItemDialog(
            onDismiss = {
                leaveDialogVisible = false
            },
            onSaveDraft = {
                leaveDialogVisible = false
                onSaveDraft(currentDraft)
            },
            onDiscard = {
                leaveDialogVisible = false
                onDiscardDraft()
            },
        )
    }

    if (confirmationDialogVisible && selectedLocation != null) {
        ConfirmManualItemDialog(
            itemName = itemName.trim(),
            locationPath = selectedLocation.displayPath,
            submitting = submitting,
            onDismiss = {
                if (!submitting) {
                    confirmationDialogVisible = false
                }
            },
            onConfirm = {
                confirmationDialogVisible = false
                onSubmit(
                    CreateManualItemRequest(
                        name = itemName,
                        locationId = selectedLocation.locationId,
                        locationDescription = locationDescription,
                        note = note,
                    ),
                )
            },
        )
    }
}

/**
 * 离开新增页且已有输入时，提示保存草稿或放弃修改。
 */
@Composable
private fun LeaveAddItemDialog(
    onDismiss: () -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存未完成内容？") },
        text = {
            Text("草稿只保存在当前设备，7 天后自动失效，不会进入家庭备份。")
        },
        confirmButton = {
            TextButton(onClick = onSaveDraft) {
                Text("保存草稿")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("放弃修改")
            }
        },
    )
}

/**
 * 新增物品页标题和返回入口。
 */
@Composable
private fun AddItemHeader(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onBack,
                ),
            color = WhereBackgroundColor,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = WhereIcons.Back,
                    contentDescription = "返回",
                    tint = WherePrimaryTextColor,
                )
            }
        }
        Text(
            text = "记录物品",
            color = WherePrimaryTextColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * 拍照、确认和保存三步提示。
 */
@Composable
private fun AddItemSteps(modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StepBadge(number = "1", label = "拍照", active = true)
        StepBadge(number = "2", label = "确认", active = false)
        StepBadge(number = "3", label = "保存", active = false)
    }
}

/**
 * 单个步骤数字和文字。
 */
@Composable
private fun StepBadge(
    number: String,
    label: String,
    active: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            color = if (active) WherePrimaryColor else WhereOutlineColor,
            contentColor = if (active) WhereSurfaceColor else WhereSecondaryTextColor,
            shape = RoundedCornerShape(15.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = label,
            color = if (active) WherePrimaryTextColor else WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 适老录入四个主操作，映射到已有相册分槽和确认保存。
 */
@Composable
private fun ElderAddItemActions(
    modifier: Modifier,
    enabled: Boolean,
    canContinue: Boolean,
    onPickItemPhoto: () -> Unit,
    onPickLocationPhoto: () -> Unit,
    onSpeak: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ElderAddItemActionButton(
            icon = WhereIcons.AddPhoto,
            title = "拍物品",
            enabled = enabled,
            primary = false,
            onClick = onPickItemPhoto,
        )
        ElderAddItemActionButton(
            icon = WhereIcons.Location,
            title = "拍存放位置",
            enabled = enabled,
            primary = false,
            onClick = onPickLocationPhoto,
        )
        ElderAddItemActionButton(
            icon = WhereIcons.Microphone,
            title = "说一句",
            enabled = enabled,
            primary = false,
            onClick = onSpeak,
        )
        ElderAddItemActionButton(
            icon = WhereIcons.Confirm,
            title = "继续确认",
            enabled = canContinue,
            primary = true,
            onClick = onContinue,
        )
    }
}

@Composable
private fun ElderAddItemActionButton(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) WherePrimaryColor else WhereSurfaceColor,
            contentColor = if (primary) WhereSurfaceColor else WherePrimaryTextColor,
        ),
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = icon,
            contentDescription = title,
        )
        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * 相册导入入口卡片。当前使用系统照片选择器，不申请相机或相册权限。
 */
@Composable
private fun PhotoActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 146.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                imageVector = icon,
                contentDescription = null,
                tint = WherePrimaryColor,
            )
            Text(
                modifier = Modifier.padding(top = 10.dp),
                text = title,
                color = WherePrimaryTextColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = description,
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 按原型固定展示位置照、物品照、更多照三个用途槽，避免空态变成无区别的待添加。
 *
 * 已导入照片按用途落入对应槽；空槽仍保留用途文案，点击后按该用途选图。
 */
@Composable
private fun PhotoThumbnailRow(
    modifier: Modifier,
    photos: List<ImportedItemPhoto>,
    resolveMediaPath: (String) -> String?,
    enabled: Boolean,
    onPickPhoto: (PhotoRole) -> Unit,
) {
    val locationPhoto = photos.firstOrNull { photo -> photo.role == PhotoRole.ENVIRONMENT }
    val itemPhoto = photos.firstOrNull { photo -> photo.role == PhotoRole.ITEM }
    val morePhoto = photos.firstOrNull { photo ->
        photo != locationPhoto && photo != itemPhoto
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RolePhotoSlot(
            modifier = Modifier.weight(1f),
            label = "位置照片",
            photo = locationPhoto,
            resolveMediaPath = resolveMediaPath,
            selected = locationPhoto != null || photos.isEmpty(),
            enabled = enabled,
            onClick = {
                onPickPhoto(PhotoRole.ENVIRONMENT)
            },
        )
        RolePhotoSlot(
            modifier = Modifier.weight(1f),
            label = "物品照片",
            photo = itemPhoto,
            resolveMediaPath = resolveMediaPath,
            selected = itemPhoto != null,
            enabled = enabled,
            onClick = {
                onPickPhoto(PhotoRole.ITEM)
            },
        )
        RolePhotoSlot(
            modifier = Modifier.weight(1f),
            label = "更多照片",
            photo = morePhoto,
            resolveMediaPath = resolveMediaPath,
            selected = morePhoto != null,
            enabled = enabled,
            onClick = {
                onPickPhoto(PhotoRole.SUPPLEMENTARY)
            },
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 66.dp)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        onPickPhoto(PhotoRole.SUPPLEMENTARY)
                    },
                ),
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "＋ 添加",
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 单个用途槽：有图显示缩略图，无图保留用途标签以便继续补拍。
 */
@Composable
private fun RolePhotoSlot(
    modifier: Modifier,
    label: String,
    photo: ImportedItemPhoto?,
    resolveMediaPath: (String) -> String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 66.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = when {
                    photo != null && selected -> "已选择"
                    photo != null -> "已添加"
                    else -> "未添加"
                }
            },
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) null else BorderStroke(1.dp, WhereOutlineColor),
    ) {
        if (photo == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    color = WherePrimaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LocalStorageImage(
                absolutePath = resolveMediaPath(photo.media.thumbnailTempStorageKey),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = label,
                        color = WherePrimaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 新增物品字段标签。
 */
@Composable
private fun AddItemFieldLabel(
    modifier: Modifier,
    text: String,
) {
    Text(
        modifier = modifier,
        text = text,
        color = WhereSecondaryTextColor,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * 可点击的位置选择输入框。
 */
@Composable
private fun LocationSelectionField(
    modifier: Modifier,
    selectedLocation: ItemCreationLocation?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        color = WhereSurfaceColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLocation?.displayPath ?: "选择最近或常用位置",
                color = if (selectedLocation == null) {
                    WhereSecondaryTextColor
                } else {
                    WherePrimaryTextColor
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 位置选择对话框。
 */
@Composable
private fun LocationSelectionDialog(
    locations: List<ItemCreationLocation>,
    onDismiss: () -> Unit,
    onSelect: (ItemCreationLocation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择所在位置")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                locations.forEach { location ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(location)
                            },
                        color = WhereSurfaceColor,
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 14.dp),
                            text = location.displayPath,
                            color = WherePrimaryTextColor,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 手动物品最终确认对话框。
 */
@Composable
private fun ConfirmManualItemDialog(
    itemName: String,
    locationPath: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("确认物品信息")
        },
        text = {
            Column {
                Text(
                    text = itemName,
                    color = WherePrimaryTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = locationPath,
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = onConfirm,
            ) {
                Text("确认保存")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text("返回修改")
            }
        },
    )
}

/**
 * 新增物品输入框颜色。
 */
@Composable
private fun addItemTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = WhereSurfaceColor,
    unfocusedContainerColor = WhereSurfaceColor,
    disabledContainerColor = WhereSurfaceColor,
    focusedBorderColor = WherePrimaryColor,
    unfocusedBorderColor = WhereOutlineColor,
    focusedTextColor = WherePrimaryTextColor,
    unfocusedTextColor = WherePrimaryTextColor,
)
