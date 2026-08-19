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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.LocationType
import com.vichua.where.feature.location.management.CreateLocationRequest
import com.vichua.where.feature.location.management.LocationTreeNode
import com.vichua.where.feature.location.management.LocationTreeSnapshot

/**
 * 按位置树维护房间、家具和容器，并展示物品数量与待确认提醒。
 *
 * 本页只覆盖空位置删除；包含物品或子节点的位置必须先处理关联内容。
 */
@Composable
fun LocationManagementScreen(
    snapshot: LocationTreeSnapshot?,
    loading: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCreate: (CreateLocationRequest) -> Unit,
    onRename: (LocationTreeNode, String) -> Unit,
    onDelete: (LocationTreeNode) -> Unit,
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
            Text("位置管理", style = MaterialTheme.typography.titleLarge)
        }

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
                LocationSummaryCard(snapshot = snapshot)
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp),
                    enabled = !submitting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WherePrimaryColor),
                    onClick = {
                        val root = snapshot.nodes.first { node ->
                            node.locationId == snapshot.rootLocationId
                        }
                        editorState = LocationEditorState.Create(root)
                    },
                ) {
                    Text("添加房间")
                }

                snapshot.nodes
                    .filter { node -> node.locationId != snapshot.rootLocationId }
                    .filter { node -> isVisible(node, snapshot.nodes, expandedLocationIds) }
                    .forEach { node ->
                        LocationTreeRow(
                            node = node,
                            expanded = node.locationId.value in expandedLocationIds,
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
                        )
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
        is LocationEditorState.Create -> LocationNameDialog(
            title = "新增位置",
            confirmText = "添加",
            initialName = "",
            allowedTypes = editor.parent.allowedChildTypes,
            onDismiss = { editorState = null },
            onConfirm = { type, name ->
                onCreate(
                    CreateLocationRequest(
                        parentId = editor.parent.locationId,
                        type = type,
                        name = name,
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
        is LocationEditorState.Delete -> AlertDialog(
            onDismissRequest = { editorState = null },
            title = { Text("删除位置") },
            text = {
                Text("删除“${editor.node.name}”后，该空位置不再出现在位置树中。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(editor.node)
                        editorState = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { editorState = null }) {
                    Text("取消")
                }
            },
        )
        null -> Unit
    }
}

/**
 * 展示家庭房间数、物品总数和位置待确认数量。
 */
@Composable
private fun LocationSummaryCard(snapshot: LocationTreeSnapshot) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = WhereSurfaceColor,
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = snapshot.householdName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = "${snapshot.roomCount} 个房间 · ${snapshot.itemCount} 件物品",
                color = WhereSecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (snapshot.locationUnconfirmedCount > 0L) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "${snapshot.locationUnconfirmedCount} 件物品位置待确认",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 展示单个位置节点及其新增、重命名和删除入口。
 */
@Composable
private fun LocationTreeRow(
    node: LocationTreeNode,
    expanded: Boolean,
    submitting: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 16.dp * (node.depth.coerceAtLeast(1) - 1)),
        shape = RoundedCornerShape(16.dp),
        color = WhereSurfaceColor,
        border = BorderStroke(1.dp, WhereOutlineColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.childCount > 0) {
                    Icon(
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onToggle)
                            .padding(4.dp),
                        imageVector = if (expanded) WhereIcons.ExpandLess else WhereIcons.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = WhereIcons.location(node.iconKey, node.type),
                    contentDescription = locationTypeLabel(node.type),
                    tint = WherePrimaryColor,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = node.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "${locationTypeLabel(node.type)} · ${node.itemCount} 件物品",
                        color = WhereSecondaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp, start = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    enabled = !submitting && node.allowedChildTypes.isNotEmpty(),
                    onClick = onAdd,
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = WhereIcons.Add,
                        contentDescription = null,
                    )
                    Text("添加")
                }
                if (node.canRename) {
                    TextButton(
                        enabled = !submitting,
                        onClick = onRename,
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = WhereIcons.Edit,
                            contentDescription = null,
                        )
                        Text("重命名")
                    }
                }
                if (node.canDelete) {
                    TextButton(
                        enabled = !submitting,
                        onClick = onDelete,
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = WhereIcons.Delete,
                            contentDescription = null,
                        )
                        Text("删除")
                    }
                }
            }
        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
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
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotEmpty(),
                onClick = { onConfirm(selectedType, trimmedName) },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
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
 * 返回位置类型的中文标签。
 */
private fun locationTypeLabel(type: LocationType): String = when (type) {
    LocationType.HOME -> "家庭"
    LocationType.ROOM -> "房间"
    LocationType.AREA -> "区域"
    LocationType.FURNITURE -> "家具"
    LocationType.CONTAINER -> "容器"
    LocationType.SLOT -> "具体位置"
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
