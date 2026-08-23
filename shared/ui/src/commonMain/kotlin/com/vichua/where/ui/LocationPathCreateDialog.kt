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
            text = "在“$parentLabel”下一次填好多层，例如书房、抽屉、第二格。",
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
            text = "第 ${index + 1} 层",
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
        label = { Text(locationTypeLabel(draft.type) + "名称") },
        singleLine = true,
    )
}

private data class LocationPathDraft(
    val type: LocationType,
    val name: String = "",
)

private const val MAX_LOCATION_PATH_SEGMENTS = 4
