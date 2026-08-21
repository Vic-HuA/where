package com.vichua.where.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.vichua.where.core.model.LocationNodeId
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
    onBack: () -> Unit,
    onSave: (LocationNodeId) -> Unit,
    elderFriendlyMode: Boolean = false,
) {
    var selectedLocationId by remember { mutableStateOf<LocationNodeId?>(null) }
    var locationQuery by remember { mutableStateOf("") }

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
            Text("更新位置", style = MaterialTheme.typography.titleLarge)
        }

        if (loading || context == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 48.dp),
            )
            return@Column
        }

        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = context.item.name,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = "原位置：${visibleLocationPath(context.currentLocationPath)}",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
            text = "选择新位置",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            value = locationQuery,
            onValueChange = { value ->
                locationQuery = value
            },
            singleLine = true,
            label = { Text("搜索已有位置") },
        )
        val trimmedQuery = locationQuery.trim()
        val candidateLocations = context.availableLocations.filter { location ->
            location.locationId != context.item.currentLocationId &&
                (
                    trimmedQuery.isEmpty() ||
                        location.displayPath.contains(trimmedQuery, ignoreCase = true)
                    )
        }
        if (candidateLocations.isEmpty()) {
            Text(
                modifier = Modifier.padding(bottom = 10.dp),
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
                .padding(top = 18.dp)
                .heightIn(min = 52.dp),
            enabled = selectedLocationId != null && !loading,
            onClick = {
                selectedLocationId?.let(onSave)
            },
        ) {
            Text("保存新位置")
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
            .padding(bottom = 10.dp)
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
                imageVector = WhereIcons.Location,
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
