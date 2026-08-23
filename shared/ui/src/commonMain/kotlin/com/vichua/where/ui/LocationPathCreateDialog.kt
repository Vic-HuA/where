package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.LocationType
import com.vichua.where.feature.location.management.CreateLocationPathSegment
import com.vichua.where.feature.location.management.allowedChildTypes

/**
 * 一次填写多层位置名称，确认后按顺序创建整条链。
 */
@Composable
fun LocationPathCreateDialog(
    parentLabel: String,
    parentType: LocationType,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<CreateLocationPathSegment>) -> Unit,
) {
    val drafts = remember {
        mutableStateListOf(LocationPathDraft(allowedChildTypes(parentType).first()))
    }
    val canConfirm = drafts.all { draft -> draft.name.trim().isNotEmpty() } && !submitting

    WhereDialog(
        onDismissRequest = onDismiss,
        title = "新增位置",
        confirmText = "添加",
        onConfirm = {
            onConfirm(
                drafts.map { draft ->
                    CreateLocationPathSegment(
                        type = draft.type,
                        name = draft.name.trim(),
                    )
                },
            )
        },
        confirmEnabled = canConfirm,
        dismissText = "取消",
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    ) {
        Text(
            text = locationPathCreateHint(parentLabel, parentType),
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        drafts.forEachIndexed { index, draft ->
            val previousType = if (index == 0) parentType else drafts[index - 1].type
            val allowedTypes = allowedChildTypes(previousType)
            LocationPathDraftRow(
                index = index,
                draft = draft,
                allowedTypes = allowedTypes,
                parentLabel = parentLabel,
                ancestorDrafts = drafts.take(index),
                enabled = !submitting,
                onTypeChange = { type ->
                    drafts[index] = draft.copy(type = type)
                    while (drafts.lastIndex > index) {
                        drafts.removeAt(drafts.lastIndex)
                    }
                },
                onNameChange = { name ->
                    drafts[index] = draft.copy(name = name)
                },
                onRemove = if (index > 0) {
                    {
                        drafts.removeAt(index)
                    }
                } else {
                    null
                },
            )
        }
        val lastType = drafts.last().type
        if (allowedChildTypes(lastType).isNotEmpty() && drafts.size < MAX_LOCATION_PATH_SEGMENTS) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable(
                        enabled = !submitting,
                        role = Role.Button,
                    ) {
                        drafts += LocationPathDraft(allowedChildTypes(lastType).first())
                    },
                shape = RoundedCornerShape(14.dp),
                color = WhereSelectedContainerColor,
                border = BorderStroke(1.dp, WherePrimaryColor),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    text = "再加一层",
                    color = WherePrimaryColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LocationPathDraftRow(
    index: Int,
    draft: LocationPathDraft,
    allowedTypes: List<LocationType>,
    parentLabel: String,
    ancestorDrafts: List<LocationPathDraft>,
    enabled: Boolean,
    onTypeChange: (LocationType) -> Unit,
    onNameChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "第 ${index + 1} 层 · ${locationTypeLabel(draft.type)}",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (onRemove != null) {
            Text(
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onRemove,
                ),
                text = "删除这一层",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (allowedTypes.size > 1) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allowedTypes.forEach { type ->
                val selected = type == draft.type
                Surface(
                    modifier = Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onTypeChange(type) },
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
                        text = locationTypeLabel(type),
                        color = if (selected) WherePrimaryColor else WherePrimaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        value = draft.name,
        onValueChange = onNameChange,
        enabled = enabled,
        // 不用浮动标签占住输入框，否则第三、四层没点进去时看不到“例如…”。
        placeholder = {
            Text(
                locationNamePlaceholder(
                    type = draft.type,
                    parentLabel = parentLabel,
                    ancestorDrafts = ancestorDrafts,
                ),
            )
        },
        singleLine = true,
    )
}

/**
 * 按当前父位置和已识别场景给出沿用说明。
 */
private fun locationPathCreateHint(
    parentLabel: String,
    parentType: LocationType,
): String {
    val scene = inferLocationHintScene(listOf(parentLabel))
    val reuseHint = "已有同名同类型的层会直接沿用，只新建后面没有的层。"
    val example = when (parentType) {
        LocationType.HOME -> when (scene) {
            LocationHintScene.LIVING -> "例如已有客厅时，再填客厅、茶几即可加上茶几。"
            LocationHintScene.KITCHEN -> "例如已有厨房时，再填厨房、橱柜即可加上橱柜。"
            LocationHintScene.STUDY -> "例如已有书房时，再填书房、书柜即可加上书柜。"
            LocationHintScene.BATHROOM -> "例如已有卫生间时，再填卫生间、镜柜即可加上镜柜。"
            LocationHintScene.BEDROOM, LocationHintScene.GENERIC ->
                "例如已有卧室时，再填卧室、衣柜即可加上衣柜。"
        }
        LocationType.ROOM -> "例如再填${sceneFurnitureName(scene)}、第二层。"
        LocationType.AREA, LocationType.FURNITURE -> "例如再填抽屉、左边格子。"
        LocationType.CONTAINER, LocationType.SLOT -> "例如再填第二层、左边格子。"
    }
    return "在“$parentLabel”下一次填好多层。$reuseHint$example"
}

/**
 * 根据前面已填名称推断这一层更合理的填写提示。
 */
private fun locationNamePlaceholder(
    type: LocationType,
    parentLabel: String,
    ancestorDrafts: List<LocationPathDraft>,
): String {
    val ancestorNames = listOf(parentLabel) + ancestorDrafts.map { draft -> draft.name }
    val scene = inferLocationHintScene(ancestorNames)
    val previousDraft = ancestorDrafts.lastOrNull()
    val previousName = previousDraft?.name.orEmpty().trim()
    val previousType = previousDraft?.type
    if (type == LocationType.CONTAINER &&
        (previousName.contains("柜") || previousType == LocationType.FURNITURE)
    ) {
        return "例如抽屉"
    }
    if (type == LocationType.SLOT) {
        return if (previousName.contains("抽屉") || previousName.contains("盒")) {
            "例如左边格子"
        } else {
            "例如第二层"
        }
    }
    return when (type) {
        LocationType.HOME -> "例如我的家"
        LocationType.ROOM -> "例如${sceneRoomName(scene)}"
        LocationType.AREA -> "例如${sceneAreaName(scene)}"
        LocationType.FURNITURE -> "例如${sceneFurnitureName(scene)}"
        LocationType.CONTAINER -> "例如抽屉"
        LocationType.SLOT -> "例如第二层"
    }
}

/**
 * 从已有位置名称里识别常见房间场景，识别不到时回退到卧室。
 */
private fun inferLocationHintScene(texts: List<String>): LocationHintScene {
    val joined = texts.joinToString(" ")
    return when {
        listOf("卧室", "主卧", "次卧", "儿童房").any { keyword -> joined.contains(keyword) } ->
            LocationHintScene.BEDROOM
        listOf("客厅", "大厅", "起居").any { keyword -> joined.contains(keyword) } ->
            LocationHintScene.LIVING
        joined.contains("厨房") -> LocationHintScene.KITCHEN
        listOf("书房", "Study", "study").any { keyword -> joined.contains(keyword) } ->
            LocationHintScene.STUDY
        listOf("卫生", "浴室", "厕所").any { keyword -> joined.contains(keyword) } ->
            LocationHintScene.BATHROOM
        else -> LocationHintScene.GENERIC
    }
}

private fun sceneRoomName(scene: LocationHintScene): String = when (scene) {
    LocationHintScene.LIVING -> "客厅"
    LocationHintScene.KITCHEN -> "厨房"
    LocationHintScene.STUDY -> "书房"
    LocationHintScene.BATHROOM -> "卫生间"
    LocationHintScene.BEDROOM, LocationHintScene.GENERIC -> "卧室"
}

private fun sceneAreaName(scene: LocationHintScene): String = when (scene) {
    LocationHintScene.LIVING -> "沙发旁"
    LocationHintScene.KITCHEN -> "灶台旁"
    LocationHintScene.STUDY -> "窗边"
    LocationHintScene.BATHROOM -> "洗手台"
    LocationHintScene.BEDROOM, LocationHintScene.GENERIC -> "床边"
}

private fun sceneFurnitureName(scene: LocationHintScene): String = when (scene) {
    LocationHintScene.LIVING -> "茶几"
    LocationHintScene.KITCHEN -> "橱柜"
    LocationHintScene.STUDY -> "书柜"
    LocationHintScene.BATHROOM -> "镜柜"
    LocationHintScene.BEDROOM, LocationHintScene.GENERIC -> "衣柜"
}

private enum class LocationHintScene {
    BEDROOM,
    LIVING,
    KITCHEN,
    STUDY,
    BATHROOM,
    GENERIC,
}

private data class LocationPathDraft(
    val type: LocationType,
    val name: String = "",
)

private const val MAX_LOCATION_PATH_SEGMENTS = 4
