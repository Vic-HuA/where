package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.LocationType
import com.vichua.where.feature.location.management.CreateLocationPathRequest
import com.vichua.where.feature.location.management.LocationTreeNode
import com.vichua.where.feature.location.management.LocationTreeSnapshot

/**
 * 按位置树维护房间、家具和容器，并展示物品数量与待确认提醒。
 *
 * 叶子位置可以直接删除；其上物品会标为位置待确认。仍有子节点时必须先处理下级。
 */
@Composable
fun LocationManagementScreen(
    snapshot: LocationTreeSnapshot?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCreate: (CreateLocationPathRequest) -> Unit,
    onRename: (LocationTreeNode, String) -> Unit,
    onDelete: (LocationTreeNode) -> Unit,
    onViewItems: (LocationTreeNode) -> Unit,
    onAddPhoto: (LocationTreeNode) -> Unit = {},
    onRemovePhoto: (LocationTreeNode) -> Unit = {},
    resolveMediaPath: (String) -> String? = { null },
    onLocationUnconfirmedClick: () -> Unit = {},
) {
    var expandedLocationIds by remember(snapshot?.rootLocationId) {
        mutableStateOf(
            snapshot
                ?.nodes
                ?.filter { node -> node.depth <= 1 }
                ?.map { node -> node.locationId.value }
                ?.toSet()
                .orEmpty(),
        )
    }
    var editorState by remember { mutableStateOf<LocationEditorState?>(null) }
    var locationQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = WhereBackgroundColor,
        bottomBar = {
            WhereBottomNavigation(
                selected = WhereRootTab.LOCATION,
                onHomeClick = onHomeClick,
                onLocationClick = {},
                onSettingsClick = onSettingsClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = "位置管理",
                style = MaterialTheme.typography.headlineMedium,
            )

            when {
                loading && snapshot == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 48.dp),
                    )
                }
                snapshot == null -> {
                    Text(
                        modifier = Modifier.padding(top = 24.dp),
                        text = errorMessage ?: "暂时无法读取位置。",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        modifier = Modifier.padding(top = 16.dp),
                        onClick = onRetry,
                    ) {
                        Text("重试")
                    }
                }
                else -> {
                    LocationSearchField(
                        query = locationQuery,
                        onQueryChange = { value ->
                            locationQuery = value
                        },
                    )
                    LocationSummaryCard(
                        snapshot = snapshot,
                        onLocationUnconfirmedClick = onLocationUnconfirmedClick,
                    )
                    Text(
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        text = "位置结构",
                        color = WhereSecondaryTextColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    val trimmedQuery = locationQuery.trim()
                    snapshot.nodes
                        .filter { node -> node.locationId != snapshot.rootLocationId }
                        .filter { node ->
                            if (trimmedQuery.isEmpty()) {
                                isVisible(node, snapshot.nodes, expandedLocationIds)
                            } else {
                                node.name.contains(trimmedQuery, ignoreCase = true) ||
                                    node.displayPath.contains(trimmedQuery, ignoreCase = true)
                            }
                        }
                        .forEach { node ->
                            val expanded = node.locationId.value in expandedLocationIds
                            LocationTreeRow(
                                node = node,
                                expanded = expanded,
                                highlighted = expanded && node.depth == 1 && trimmedQuery.isEmpty(),
                                submitting = submitting,
                                onToggle = {
                                    expandedLocationIds = expandedLocationIds.toggle(node.locationId.value)
                                },
                                onAdd = {
                                    editorState = LocationEditorState.Create(node)
                                },
                                onRename = {
                                    editorState = LocationEditorState.Rename(node)
                                },
                                onDelete = {
                                    editorState = LocationEditorState.Delete(node)
                                },
                                onViewItems = {
                                    onViewItems(node)
                                },
                                onAddPhoto = {
                                    onAddPhoto(node)
                                },
                                onRemovePhoto = {
                                    onRemovePhoto(node)
                                },
                                resolveMediaPath = resolveMediaPath,
                            )
                        }

                    val root = snapshot.nodes.first { node ->
                        node.locationId == snapshot.rootLocationId
                    }
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(48.dp),
                        enabled = !submitting && root.allowedChildTypes.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
                        onClick = {
                            editorState = LocationEditorState.Create(root)
                        },
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = WhereIcons.Add,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("新增位置")
                    }
                }
            }

            if (errorMessage != null && snapshot != null) {
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        when (val editor = editorState) {
            is LocationEditorState.Create -> LocationPathCreateDialog(
                parentLabel = editor.parent.name,
                parentType = editor.parent.type,
                submitting = submitting,
                onDismiss = { editorState = null },
                onConfirm = { segments ->
                    onCreate(
                        CreateLocationPathRequest(
                            parentId = editor.parent.locationId,
                            segments = segments,
                        ),
                    )
                    editorState = null
                },
            )
            is LocationEditorState.Rename -> LocationNameDialog(
                title = "重命名${locationTypeLabel(editor.node.type)}",
                confirmText = "保存",
                initialName = editor.node.name,
                allowedTypes = listOf(editor.node.type),
                typeLocked = true,
                onDismiss = { editorState = null },
                onConfirm = { _, name ->
                    onRename(editor.node, name)
                    editorState = null
                },
            )
            is LocationEditorState.Delete -> WhereDialog(
                onDismissRequest = { editorState = null },
                title = "删除位置",
                confirmText = "删除",
                onConfirm = {
                    onDelete(editor.node)
                    editorState = null
                },
                confirmDestructive = true,
                dismissText = "取消",
                onDismiss = { editorState = null },
            ) {
                val deleteMessage = if (editor.node.itemCount > 0) {
                    "删除“${editor.node.name}”后，这里的 ${editor.node.itemCount} 件物品会标为位置待确认，请再选一次位置。"
                } else {
                    "删除“${editor.node.name}”后，该空位置不再出现在位置树中。"
                }
                Text(deleteMessage)
            }
            null -> Unit
        }
    }
}

/**
 * 按原型做成带搜索图标的单行检索框，避免再占一块表单标签。
 */
@Composable
private fun LocationSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .heightIn(min = 48.dp),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("搜索房间、家具或容器") },
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
            disabledContainerColor = WhereSurfaceColor,
            focusedBorderColor = WherePrimaryColor,
            unfocusedBorderColor = WhereOutlineColor,
        ),
    )
}

/**
 * 展示家庭房间数、物品总数和位置待确认数量。
 */
@Composable
private fun LocationSummaryCard(
    snapshot: LocationTreeSnapshot,
    onLocationUnconfirmedClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .then(
                if (snapshot.locationUnconfirmedCount > 0L) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onLocationUnconfirmedClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = WhereSelectedContainerColor,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.householdName,
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = "${snapshot.roomCount} 个房间 · ${snapshot.itemCount} 件物品",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (snapshot.locationUnconfirmedCount > 0L) {
                Text(
                    text = "${snapshot.locationUnconfirmedCount} 件待确认",
                    color = ColorUnconfirmed,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 展示单个位置节点，操作收入右侧菜单以免撑高树行。
 */
@Composable
private fun LocationTreeRow(
    node: LocationTreeNode,
    expanded: Boolean,
    highlighted: Boolean,
    submitting: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onViewItems: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    resolveMediaPath: (String) -> String?,
) {
    var menuExpanded by remember(node.locationId) { mutableStateOf(false) }
    val canAdd = node.allowedChildTypes.isNotEmpty()
    val hasMenuActions = true

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 20.dp * (node.depth.coerceAtLeast(1) - 1)),
        shape = RoundedCornerShape(13.dp),
        color = if (highlighted) WhereSelectedContainerColor else WhereSurfaceColor,
        border = BorderStroke(
            1.dp,
            if (highlighted) WherePrimaryColor else WhereOutlineColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 50.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.childCount > 0) {
                Icon(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(role = Role.Button, onClick = onToggle)
                        .padding(12.dp),
                    imageVector = if (expanded) {
                        WhereIcons.ExpandMore
                    } else {
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                    },
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = WhereSecondaryTextColor,
                )
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onViewItems),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverPath = node.coverThumbnailStorageKey?.let(resolveMediaPath)
                if (coverPath != null) {
                    LocalStorageImage(
                        absolutePath = coverPath,
                        contentDescription = locationTypeLabel(node.type),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = WhereIcons.location(node.iconKey, node.type),
                            contentDescription = locationTypeLabel(node.type),
                            tint = WherePrimaryColor,
                        )
                    }
                } else {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = WhereIcons.location(node.iconKey, node.type),
                        contentDescription = locationTypeLabel(node.type),
                        tint = WherePrimaryColor,
                    )
                }
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, end = 8.dp),
                    text = node.name,
                    fontWeight = if (node.depth == 1) FontWeight.Bold else FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${node.itemCount} 件",
                    color = WhereSecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (hasMenuActions) {
                Box {
                    Icon(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                enabled = !submitting,
                                role = Role.Button,
                                onClick = { menuExpanded = true },
                            )
                            .padding(12.dp),
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "位置操作",
                        tint = WhereSecondaryTextColor,
                    )
                    if (menuExpanded) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            onDismissRequest = { menuExpanded = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 40.dp, end = 4.dp)
                                    .widthIn(min = 176.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = WhereSurfaceColor,
                                border = BorderStroke(1.dp, WhereOutlineColor),
                                shadowElevation = 8.dp,
                            ) {
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    LocationNodeMenuRow(
                                        icon = WhereIcons.Search,
                                        label = "查看物品",
                                        onClick = {
                                            menuExpanded = false
                                            onViewItems()
                                        },
                                    )
                                    if (canAdd) {
                                        LocationNodeMenuRow(
                                            icon = WhereIcons.Add,
                                            label = "添加子位置",
                                            onClick = {
                                                menuExpanded = false
                                                onAdd()
                                            },
                                        )
                                    }
                                    if (node.canRename) {
                                        LocationNodeMenuRow(
                                            icon = WhereIcons.AddPhoto,
                                            label = if (node.coverThumbnailStorageKey == null) {
                                                "添加照片"
                                            } else {
                                                "更换照片"
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onAddPhoto()
                                            },
                                        )
                                        if (node.coverThumbnailStorageKey != null) {
                                            LocationNodeMenuRow(
                                                icon = WhereIcons.Delete,
                                                label = "删除照片",
                                                onClick = {
                                                    menuExpanded = false
                                                    onRemovePhoto()
                                                },
                                            )
                                        }
                                        LocationNodeMenuRow(
                                            icon = WhereIcons.Edit,
                                            label = "重命名",
                                            onClick = {
                                                menuExpanded = false
                                                onRename()
                                            },
                                        )
                                    }
                                    if (node.canDelete) {
                                        LocationNodeMenuRow(
                                            icon = WhereIcons.Delete,
                                            label = "删除",
                                            destructive = true,
                                            onClick = {
                                                menuExpanded = false
                                                onDelete()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 节点菜单行：和页面卡片同一套白底、圆角和图标，避免系统默认弹出层发灰发紫。
 */
@Composable
private fun LocationNodeMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        WherePrimaryTextColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) contentColor else WherePrimaryColor,
        )
        Text(
            modifier = Modifier.padding(start = 12.dp),
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * 新增或重命名位置时收集名称和类型。
 */
@Composable
private fun LocationNameDialog(
    title: String,
    confirmText: String,
    initialName: String,
    allowedTypes: List<LocationType>,
    typeLocked: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (LocationType, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf(allowedTypes.first()) }
    val trimmedName = name.trim()

    WhereDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = confirmText,
        onConfirm = { onConfirm(selectedType, trimmedName) },
        confirmEnabled = trimmedName.isNotEmpty(),
        dismissText = "取消",
        onDismiss = onDismiss,
    ) {
        if (!typeLocked && allowedTypes.size > 1) {
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = locationTypeLabel(type),
                            color = if (selected) {
                                WherePrimaryColor
                            } else {
                                WherePrimaryTextColor
                            },
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
            label = { Text("名称") },
            singleLine = true,
        )
    }
}

/**
 * 判断节点的全部祖先是否处于展开状态。
 */
private fun isVisible(
    node: LocationTreeNode,
    nodes: List<LocationTreeNode>,
    expandedLocationIds: Set<String>,
): Boolean {
    var currentParentId = node.parentId
    while (currentParentId != null) {
        val parent = nodes.singleOrNull { candidate ->
            candidate.locationId == currentParentId
        } ?: return false
        if (parent.depth > 0 && parent.locationId.value !in expandedLocationIds) {
            return false
        }
        currentParentId = parent.parentId
    }
    return true
}

/**
 * 切换展开集合中的位置 ID。
 */
private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * 位置管理页当前打开的编辑对话框。
 */
private sealed interface LocationEditorState {
    data class Create(val parent: LocationTreeNode) : LocationEditorState
    data class Rename(val node: LocationTreeNode) : LocationEditorState
    data class Delete(val node: LocationTreeNode) : LocationEditorState
}

/** 原型待确认数量使用的红棕色，避免和错误提示抢同一套 error 色。 */
private val ColorUnconfirmed = Color(0xFFA64239)
