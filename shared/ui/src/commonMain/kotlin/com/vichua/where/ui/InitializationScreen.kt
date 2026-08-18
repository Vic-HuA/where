package com.vichua.where.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.feature.location.initialization.InitialRoomInput
import com.vichua.where.feature.location.initialization.InitializeHouseholdRequest

/**
 * 创建首个本地家庭和基础房间的共享页面。
 *
 * 页面只收集完成初始化所需的最少字段，不申请权限，也不展示账号或云端能力。
 *
 * @param suggestedDeviceName 当前平台提供的设备名称建议。
 * @param devicePlatform 当前运行平台。
 * @param isSubmitting 是否正在保存，保存期间禁用重复提交。
 * @param errorMessage 可向用户展示的中文错误信息。
 * @param onSubmit 用户确认后提交完整初始化请求。
 */
@Composable
fun InitializationScreen(
    suggestedDeviceName: String,
    devicePlatform: DevicePlatform,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (InitializeHouseholdRequest) -> Unit,
) {
    var householdName by remember { mutableStateOf(DEFAULT_HOUSEHOLD_NAME) }
    var selectedRoomKeys by remember {
        mutableStateOf(DEFAULT_SELECTED_ROOM_KEYS)
    }
    val canSubmit = householdName.isNotBlank() &&
        selectedRoomKeys.isNotEmpty() &&
        !isSubmitting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = "先从你的家开始",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "建立常用房间，之后记录物品会更快。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            value = householdName,
            onValueChange = { newName ->
                householdName = newName
            },
            enabled = !isSubmitting,
            label = {
                Text("家庭名称")
            },
            singleLine = true,
        )

        Text(
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            text = "选择常用房间",
            style = MaterialTheme.typography.titleMedium,
        )

        INITIAL_ROOM_OPTIONS.forEach { room ->
            val selected = room.key in selectedRoomKeys
            RoomSelectionCard(
                room = room,
                selected = selected,
                enabled = !isSubmitting,
                onSelectionChange = {
                    selectedRoomKeys = if (selected) {
                        selectedRoomKeys - room.key
                    } else {
                        selectedRoomKeys + room.key
                    }
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = "当前设备：$suggestedDeviceName",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (selectedRoomKeys.isEmpty()) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "请至少选择一个房间。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 4.dp),
            enabled = canSubmit,
            onClick = {
                onSubmit(
                    InitializeHouseholdRequest(
                        householdName = householdName,
                        deviceName = suggestedDeviceName,
                        devicePlatform = devicePlatform,
                        rooms = INITIAL_ROOM_OPTIONS
                            .filter { room -> room.key in selectedRoomKeys }
                            .map { room ->
                                InitialRoomInput(
                                    name = room.name,
                                    iconKey = room.iconKey,
                                )
                            },
                        rootIconKey = ROOT_LOCATION_ICON_KEY,
                    ),
                )
            },
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("完成，进入首页")
            }
        }
    }
}

/**
 * 单个基础房间的可点击选择卡片。
 */
@Composable
private fun RoomSelectionCard(
    room: RoomOption,
    selected: Boolean,
    enabled: Boolean,
    onSelectionChange: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onSelectionChange,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Checkbox(
                checked = selected,
                enabled = enabled,
                onCheckedChange = {
                    onSelectionChange()
                },
            )
        }
    }
}

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
