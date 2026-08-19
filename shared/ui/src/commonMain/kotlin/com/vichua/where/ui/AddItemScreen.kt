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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.feature.item.creation.CreateManualItemRequest
import com.vichua.where.feature.item.creation.ItemCreationContext
import com.vichua.where.feature.item.creation.ItemCreationLocation
import com.vichua.where.feature.item.draft.ItemDraftContent

/**
 * 按 Pencil 原型展示新增物品基础页面，并支持不依赖相机、语音或 AI 的手动保存路径。
 *
 * @param context 当前家庭和可选位置。
 * @param loading 是否正在读取位置。
 * @param submitting 是否正在保存物品。
 * @param errorMessage 可展示的中文错误。
 * @param onRetry 重试读取可选位置。
 * @param draft 当前设备可恢复的未过期草稿；没有时为空。
 * @param onBack 返回首页。
 * @param onSaveDraft 保存当前未完成输入为设备本地草稿。
 * @param onDiscardDraft 放弃当前草稿并离开页面。
 * @param onSubmit 确认后提交基础手动物品请求。
 */
@Composable
fun AddItemScreen(
    context: ItemCreationContext?,
    draft: ItemDraftContent?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSaveDraft: (ItemDraftContent) -> Unit,
    onDiscardDraft: () -> Unit,
    onSubmit: (CreateManualItemRequest) -> Unit,
) {
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
    val requestLeave: () -> Unit = {
        if (currentDraft.hasUserInput) {
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
        AddItemSteps(modifier = Modifier.padding(top = 18.dp))

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
            )
            PhotoActionCard(
                modifier = Modifier.weight(1f),
                icon = WhereIcons.AddPhoto,
                title = "拍物品 ＋",
                description = "拍清楚物品外观",
            )
        }
        PhotoThumbnailRow(modifier = Modifier.padding(top = 12.dp))

        AddItemFieldLabel(
            modifier = Modifier.padding(top = 18.dp),
            text = "物品名称",
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(52.dp),
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
                .height(48.dp)
                .clickable(
                    enabled = !submitting,
                    role = Role.Button,
                    onClick = {
                        moreInformationExpanded = !moreInformationExpanded
                    },
                ),
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
                .height(64.dp),
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
                    contentDescription = null,
                    tint = WherePrimaryColor,
                )
                Text(
                    text = "按住说：放在书柜第二层蓝色盒子",
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

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(52.dp),
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
 * 原型中的拍摄入口卡片；平台相机接入前保持视觉入口但不申请权限。
 */
@Composable
private fun PhotoActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    description: String,
) {
    Surface(
        modifier = modifier.height(146.dp),
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
 * 多照片类型缩略图占位行。
 */
@Composable
private fun PhotoThumbnailRow(modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThumbnailPlaceholder(
            modifier = Modifier.weight(1f),
            label = "位置照片",
            selected = true,
        )
        ThumbnailPlaceholder(
            modifier = Modifier.weight(1f),
            label = "物品照片",
            selected = false,
        )
        ThumbnailPlaceholder(
            modifier = Modifier.weight(1f),
            label = "更多照片",
            selected = false,
        )
        ThumbnailPlaceholder(
            modifier = Modifier.weight(1f),
            label = "＋ 添加",
            selected = false,
        )
    }
}

/**
 * 单个照片类型占位缩略图。
 */
@Composable
private fun ThumbnailPlaceholder(
    modifier: Modifier,
    label: String,
    selected: Boolean,
) {
    Surface(
        modifier = modifier.height(66.dp),
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) null else BorderStroke(1.dp, WhereOutlineColor),
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
            .height(52.dp)
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
