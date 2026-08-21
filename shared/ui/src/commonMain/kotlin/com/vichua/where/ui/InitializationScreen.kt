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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.feature.location.initialization.InitialRoomInput
import com.vichua.where.feature.location.initialization.InitializeHouseholdRequest

/**
 * 创建首个本地家庭和基础房间的共享页面。
 *
 * 页面结构、颜色、尺寸和信息顺序与 Pencil 的“01 初始化家庭”原型保持一致。
 *
 * @param suggestedDeviceName 当前平台提供的设备名称建议。
 * @param devicePlatform 当前运行平台。
 * @param isSubmitting 是否正在保存，保存期间禁用重复提交。
 * @param errorMessage 可向用户展示的中文错误信息。
 * @param onSubmit 用户确认后提交完整初始化请求，并带回初始化页的适老开关。
 */
@Composable
fun InitializationScreen(
    suggestedDeviceName: String,
    devicePlatform: DevicePlatform,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (InitializeHouseholdRequest, Boolean) -> Unit,
) {
    var householdName by remember { mutableStateOf(DEFAULT_HOUSEHOLD_NAME) }
    var selectedRoomKeys by remember { mutableStateOf(DEFAULT_SELECTED_ROOM_KEYS) }
    var customRooms by remember { mutableStateOf(emptyList<RoomOption>()) }
    var customRoomDialogVisible by remember { mutableStateOf(false) }
    var elderFriendlyEnabled by remember { mutableStateOf(false) }
    val allRooms = INITIAL_ROOM_OPTIONS + customRooms
    val canSubmit = householdName.isNotBlank() &&
        selectedRoomKeys.isNotEmpty() &&
        !isSubmitting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "先从你的家开始",
            color = WherePrimaryTextColor,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "建立常用房间，之后记录物品会更快。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyLarge,
        )

        SectionLabel(
            modifier = Modifier.padding(top = 24.dp),
            text = "家庭名称",
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 54.dp),
            value = householdName,
            onValueChange = { newName ->
                householdName = newName
            },
            enabled = !isSubmitting,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WhereSurfaceColor,
                unfocusedContainerColor = WhereSurfaceColor,
                disabledContainerColor = WhereSurfaceColor,
                focusedBorderColor = WherePrimaryColor,
                unfocusedBorderColor = WhereOutlineColor,
                focusedTextColor = WherePrimaryTextColor,
                unfocusedTextColor = WherePrimaryTextColor,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        SectionLabel(
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            text = "选择常用房间",
        )
        RoomChipRow(
            rooms = allRooms.take(FIRST_ROOM_ROW_SIZE),
            selectedRoomKeys = selectedRoomKeys,
            enabled = !isSubmitting,
            onToggle = { room ->
                selectedRoomKeys = selectedRoomKeys.toggle(room.key)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        RoomChipRow(
            rooms = allRooms.drop(FIRST_ROOM_ROW_SIZE),
            selectedRoomKeys = selectedRoomKeys,
            enabled = !isSubmitting,
            onToggle = { room ->
                selectedRoomKeys = selectedRoomKeys.toggle(room.key)
            },
        )

        ActionSurface(
            modifier = Modifier.padding(top = 18.dp),
            enabled = !isSubmitting,
            onClick = {
                customRoomDialogVisible = true
            },
        ) {
            Text(
                text = "＋ 自定义房间 · 照片 / 图标 / 语音",
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            color = WhereSurfaceColor,
            shape = RoundedCornerShape(13.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "适老模式",
                        color = WherePrimaryTextColor,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = "大字、朗读，可由家人协助设置",
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = elderFriendlyEnabled,
                    enabled = !isSubmitting,
                    onCheckedChange = { enabled ->
                        elderFriendlyEnabled = enabled
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = WherePrimaryColor,
                        uncheckedTrackColor = WhereOutlineColor,
                        checkedThumbColor = WhereSurfaceColor,
                        uncheckedThumbColor = WhereSurfaceColor,
                    ),
                )
            }
        }

        if (selectedRoomKeys.isEmpty()) {
            ErrorText("请至少选择一个房间。")
        }
        if (errorMessage != null) {
            ErrorText(errorMessage)
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .heightIn(min = 54.dp),
            enabled = canSubmit,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WherePrimaryColor,
                contentColor = WhereSurfaceColor,
            ),
            onClick = {
                onSubmit(
                    InitializeHouseholdRequest(
                        householdName = householdName,
                        deviceName = suggestedDeviceName,
                        devicePlatform = devicePlatform,
                        rooms = allRooms
                            .filter { room -> room.key in selectedRoomKeys }
                            .map { room ->
                                InitialRoomInput(
                                    name = room.name,
                                    iconKey = room.iconKey,
                                )
                            },
                        rootIconKey = ROOT_LOCATION_ICON_KEY,
                    ),
                    elderFriendlyEnabled,
                )
            },
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = WhereSurfaceColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "完成，进入首页",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (customRoomDialogVisible) {
        AddCustomRoomDialog(
            existingRooms = allRooms,
            onDismiss = {
                customRoomDialogVisible = false
            },
            onAdd = { roomName ->
                val newRoom = RoomOption(
                    key = "custom-${customRooms.size}-${roomName.lowercase()}",
                    name = roomName,
                    iconKey = CUSTOM_ROOM_ICON_KEY,
                )
                customRooms = customRooms + newRoom
                selectedRoomKeys = selectedRoomKeys + newRoom.key
                customRoomDialogVisible = false
            },
        )
    }
}

/**
 * 原型中的小号分区标题。
 */
@Composable
private fun SectionLabel(
    modifier: Modifier,
    text: String,
) {
    Text(
        modifier = modifier,
        text = text,
        color = WherePrimaryTextColor,
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * 在固定行中展示房间胶囊，保持与 390 dp 原型相同的三列视觉节奏。
 */
@Composable
private fun RoomChipRow(
    rooms: List<RoomOption>,
    selectedRoomKeys: Set<String>,
    enabled: Boolean,
    onToggle: (RoomOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rooms.forEach { room ->
            val selected = room.key in selectedRoomKeys
            RoomChip(
                room = room,
                selected = selected,
                enabled = enabled,
                onClick = {
                    onToggle(room)
                },
            )
        }
    }
}

/**
 * 房间图标、名称和选中状态胶囊。
 */
@Composable
private fun RoomChip(
    room: RoomOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 72.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "已选中" else "未选中"
            },
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        contentColor = if (selected) WherePrimaryColor else WherePrimaryTextColor,
        shape = RoundedCornerShape(20.dp),
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, WhereOutlineColor)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = WhereIcons.room(room.iconKey),
                contentDescription = null,
            )
            Text(
                text = if (selected) "${room.name} ✓" else room.name,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 自定义房间和其他辅助入口使用的白色操作卡。
 */
@Composable
private fun ActionSurface(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
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
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/**
 * 添加纯文字自定义房间的对话框。
 *
 * 照片、图标和语音名称将在对应平台能力接入后继续扩展，不在此处伪造媒体数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomRoomDialog(
    existingRooms: List<RoomOption>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var roomName by remember { mutableStateOf("") }
    val trimmedRoomName = roomName.trim()
    val duplicateName = existingRooms.any { room ->
        room.name.equals(trimmedRoomName, ignoreCase = true)
    }

    WhereDialog(
        onDismissRequest = onDismiss,
        title = "添加自定义房间",
        confirmText = "添加",
        onConfirm = {
            onAdd(trimmedRoomName)
        },
        confirmEnabled = trimmedRoomName.isNotEmpty() && !duplicateName,
        dismissText = "取消",
        onDismiss = onDismiss,
    ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = roomName,
                    onValueChange = { value ->
                        roomName = value
                    },
                    label = {
                        Text("房间名称")
                    },
                    singleLine = true,
                )
                if (duplicateName) {
                    ErrorText("这个房间已经存在。")
                }
    }
}

/**
 * 使用统一错误色展示表单问题。
 */
@Composable
private fun ErrorText(message: String) {
    Text(
        modifier = Modifier.padding(top = 10.dp),
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * 切换集合中的房间键并返回新的不可变集合。
 */
private fun Set<String>.toggle(key: String): Set<String> =
    if (key in this) this - key else this + key

/**
 * 初始化页面内部使用的受控房间选项。
 */
private data class RoomOption(
    val key: String,
    val name: String,
    val iconKey: String,
)

private const val DEFAULT_HOUSEHOLD_NAME = "我的家"
private const val ROOT_LOCATION_ICON_KEY = "location.home"
private const val CUSTOM_ROOM_ICON_KEY = "room.custom"
private const val FIRST_ROOM_ROW_SIZE = 3

private val INITIAL_ROOM_OPTIONS = listOf(
    RoomOption(key = "living-room", name = "客厅", iconKey = "room.living"),
    RoomOption(key = "bedroom", name = "卧室", iconKey = "room.bedroom"),
    RoomOption(key = "kitchen", name = "厨房", iconKey = "room.kitchen"),
    RoomOption(key = "bathroom", name = "卫生间", iconKey = "room.bathroom"),
    RoomOption(key = "study", name = "书房", iconKey = "room.study"),
    RoomOption(key = "storage", name = "储物间", iconKey = "room.storage"),
)

private val DEFAULT_SELECTED_ROOM_KEYS = setOf(
    "living-room",
    "bedroom",
    "kitchen",
)
