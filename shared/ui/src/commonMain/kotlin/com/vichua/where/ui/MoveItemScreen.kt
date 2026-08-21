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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.feature.location.management.CreateLocationRequest
import com.vichua.where.feature.location.management.allowedChildTypes
import com.vichua.where.feature.location.movement.MoveItemContext
import com.vichua.where.feature.location.movement.MoveTargetLocation

/**
 * 选择并保存物品新位置。
 */
@Composable
fun MoveItemScreen(
    context: MoveItemContext?,
    loading: Boolean,
    errorMessage: String?,
    voiceQuery: String?,
    voiceListening: Boolean,
    creatingLocation: Boolean,
    onBack: () -> Unit,
    onSave: (LocationNodeId) -> Unit,
    onCreateLocation: (CreateLocationRequest) -> Unit,
    onVoiceRequested: () -> Unit,
    onVoiceReleased: () -> Unit,
    onVoiceQueryConsumed: () -> Unit,
    elderFriendlyMode: Boolean = false,
) {
    var selectedLocationId by remember { mutableStateOf<LocationNodeId?>(null) }
    var locationQuery by remember { mutableStateOf("") }
    var browseAllVisible by remember { mutableStateOf(false) }
    var createDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(voiceQuery) {
        val query = voiceQuery ?: return@LaunchedEffect
        locationQuery = query
        browseAllVisible = true
        onVoiceQueryConsumed()
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
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(12.dp),
                imageVector = WhereIcons.Back,
                contentDescription = "返回",
            )
            Text(
                text = "更新位置",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (loading || context == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 48.dp),
            )
            return@Column
        }

        MoveItemSummaryCard(context = context)

        if (context.recentLocations.isNotEmpty()) {
            SectionTitle(text = "最近使用")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                context.recentLocations.forEach { location ->
                    RecentLocationChip(
                        location = location,
                        selected = selectedLocationId == location.locationId,
                        onClick = { selectedLocationId = location.locationId },
                    )
                }
            }
        }

        if (context.favoriteLocations.isNotEmpty()) {
            SectionTitle(text = "常用位置")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                context.favoriteLocations.forEach { location ->
                    FavoriteLocationCard(
                        location = location,
                        selected = selectedLocationId == location.locationId,
                        onClick = { selectedLocationId = location.locationId },
                    )
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 48.dp),
            value = locationQuery,
            onValueChange = { value ->
                locationQuery = value
                if (value.isNotBlank()) {
                    browseAllVisible = true
                }
            },
            singleLine = true,
            placeholder = { Text("搜索已有位置") },
            leadingIcon = {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = WhereIcons.Search,
                    contentDescription = null,
                    tint = WhereSecondaryTextColor,
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WhereSurfaceColor,
                unfocusedContainerColor = WhereSurfaceColor,
                focusedBorderColor = WherePrimaryColor,
                unfocusedBorderColor = WhereOutlineColor,
            ),
        )

        val trimmedQuery = locationQuery.trim()
        val candidateLocations = context.availableLocations.filter { location ->
            location.locationId != context.item.currentLocationId &&
                (
                    trimmedQuery.isEmpty() ||
                        location.name.contains(trimmedQuery, ignoreCase = true) ||
                        location.displayPath.contains(trimmedQuery, ignoreCase = true)
                    )
        }
        val showFullList = browseAllVisible || trimmedQuery.isNotEmpty() ||
            (elderFriendlyMode && context.recentLocations.isEmpty() && context.favoriteLocations.isEmpty())

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clickable(role = Role.Button) {
                    browseAllVisible = !browseAllVisible
                },
            shape = RoundedCornerShape(14.dp),
            color = WhereSurfaceColor,
            border = BorderStroke(1.dp, WhereOutlineColor),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountTree,
                    contentDescription = null,
                    tint = WherePrimaryTextColor,
                )
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 9.dp),
                    text = if (showFullList) "收起全部位置" else "浏览全部位置",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    imageVector = if (showFullList) {
                        WhereIcons.ExpandMore
                    } else {
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = WhereSecondaryTextColor,
                )
            }
        }

        if (showFullList) {
            if (candidateLocations.isEmpty()) {
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = if (trimmedQuery.isEmpty()) {
                        "没有其他可选择的位置。"
                    } else {
                        "没有匹配“$trimmedQuery”的位置。"
                    },
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            candidateLocations.forEach { location ->
                LocationOptionCard(
                    location = location,
                    selected = selectedLocationId == location.locationId,
                    elderFriendlyMode = elderFriendlyMode,
                    onClick = {
                        selectedLocationId = location.locationId
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .holdToSpeak(
                        enabled = !loading && !creatingLocation,
                        onPress = onVoiceRequested,
                        onRelease = onVoiceReleased,
                    ),
                shape = RoundedCornerShape(14.dp),
                color = if (voiceListening) WhereSelectedContainerColor else WhereSurfaceColor,
                border = BorderStroke(
                    1.dp,
                    if (voiceListening) WherePrimaryColor else WhereOutlineColor,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WhereIcons.Microphone,
                        contentDescription = "按住语音描述位置",
                        tint = WherePrimaryColor,
                    )
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = if (voiceListening) "正在听…" else "语音描述",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clickable(
                        enabled = !creatingLocation,
                        role = Role.Button,
                    ) {
                        createDialogVisible = true
                    },
                shape = RoundedCornerShape(14.dp),
                color = WhereSurfaceColor,
                border = BorderStroke(1.dp, WhereOutlineColor),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WhereIcons.Add,
                        contentDescription = null,
                        tint = WherePrimaryColor,
                    )
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = "新建位置",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(52.dp),
            enabled = selectedLocationId != null && !loading && !creatingLocation,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
            onClick = {
                selectedLocationId?.let(onSave)
            },
        ) {
            Text("保存新位置")
        }
    }

    if (createDialogVisible && context != null) {
        val selectedParent = context.availableLocations.firstOrNull { location ->
            location.locationId == selectedLocationId
        }
        val parentId = selectedParent?.locationId ?: context.rootLocationId
        val parentType = selectedParent?.type ?: LocationType.HOME
        MoveLocationCreateDialog(
            allowedTypes = allowedChildTypes(parentType),
            submitting = creatingLocation,
            onDismiss = { createDialogVisible = false },
            onConfirm = { type, name ->
                onCreateLocation(
                    CreateLocationRequest(
                        parentId = parentId,
                        type = type,
                        name = name,
                    ),
                )
                createDialogVisible = false
            },
        )
    }
}

@Composable
private fun MoveItemSummaryCard(context: MoveItemContext) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = WhereSelectedContainerColor,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(12.dp),
                color = WhereSurfaceColor,
            ) {
                Icon(
                    modifier = Modifier.padding(13.dp),
                    imageVector = WhereIcons.Location,
                    contentDescription = null,
                    tint = WherePrimaryColor,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = context.item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = "原位置：${visibleLocationPath(context.currentLocationPath)}",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        text = text,
        color = WhereSecondaryTextColor,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun RecentLocationChip(
    location: MoveTargetLocation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        border = BorderStroke(1.dp, if (selected) WherePrimaryColor else WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = WhereIcons.Repeat,
                contentDescription = null,
                tint = WhereSecondaryTextColor,
            )
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = visibleLocationPath(location.displayPath),
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FavoriteLocationCard(
    location: MoveTargetLocation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 96.dp, height = 74.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        border = BorderStroke(1.dp, if (selected) WherePrimaryColor else WhereOutlineColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = WhereIcons.location(location.iconKey, location.type),
                contentDescription = null,
                tint = WherePrimaryColor,
            )
            Text(
                modifier = Modifier.padding(top = 5.dp),
                text = location.name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 单个可选位置卡片。 */
@Composable
private fun LocationOptionCard(
    location: MoveTargetLocation,
    selected: Boolean,
    elderFriendlyMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .heightIn(min = if (elderFriendlyMode) 72.dp else 54.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "已选中" else "未选中"
            },
        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) WherePrimaryColor else WhereOutlineColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = WhereIcons.location(location.iconKey, location.type),
                contentDescription = null,
                tint = WherePrimaryColor,
            )
            Text(
                text = visibleLocationPath(location.displayPath),
                color = WherePrimaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MoveLocationCreateDialog(
    allowedTypes: List<LocationType>,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocationType, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(allowedTypes.first()) }
    val trimmedName = name.trim()

    WhereDialog(
        onDismissRequest = onDismiss,
        title = "新建位置",
        confirmText = "添加",
        onConfirm = { onConfirm(selectedType, trimmedName) },
        confirmEnabled = trimmedName.isNotEmpty() && !submitting,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    ) {
        if (allowedTypes.size > 1) {
            Text(
                text = "位置类型",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allowedTypes.forEach { type ->
                    val selected = type == selectedType
                    Surface(
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { selectedType = type },
                        ),
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) WhereSelectedContainerColor else WhereSurfaceColor,
                        border = BorderStroke(
                            1.dp,
                            if (selected) WherePrimaryColor else WhereOutlineColor,
                        ),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = moveLocationTypeLabel(type),
                            color = if (selected) WherePrimaryColor else WherePrimaryTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = name,
            onValueChange = { value -> name = value },
            enabled = !submitting,
            label = { Text("名称") },
            singleLine = true,
        )
    }
}

private fun moveLocationTypeLabel(type: LocationType): String = when (type) {
    LocationType.HOME -> "家庭"
    LocationType.ROOM -> "房间"
    LocationType.AREA -> "区域"
    LocationType.FURNITURE -> "家具"
    LocationType.CONTAINER -> "容器"
    LocationType.SLOT -> "具体位置"
}
