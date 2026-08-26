package com.vichua.where.core.database.mapper

import com.vichua.where.core.database.entity.FavoriteLocationEntity
import com.vichua.where.core.database.entity.LocalAccessibilityPreferencesEntity
import com.vichua.where.core.database.entity.LocalBackupRecordEntity
import com.vichua.where.core.database.entity.LocalSearchHistoryEntity
import com.vichua.where.core.database.entity.PinnedItemEntity
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.DisplayMode
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.FavoriteLocation
import com.vichua.where.core.model.FavoriteLocationId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.core.model.LocalBackupRecord
import com.vichua.where.core.model.LocalBackupRecordId
import com.vichua.where.core.model.LocalBackupStatus
import com.vichua.where.core.model.LocalSearchHistory
import com.vichua.where.core.model.LocalSearchHistoryId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.PinnedItem
import com.vichua.where.core.model.PinnedItemId
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp

/** 将常用位置领域模型转换为 Room 实体。 */
internal fun FavoriteLocation.toEntity(): FavoriteLocationEntity = FavoriteLocationEntity(
    id = id.value,
    householdId = householdId.value,
    locationNodeId = locationNodeId.value,
    sortOrder = sortOrder.value,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将常用位置 Room 实体还原为领域模型。 */
internal fun FavoriteLocationEntity.toDomain(): FavoriteLocation = FavoriteLocation(
    id = FavoriteLocationId(id),
    householdId = HouseholdId(householdId),
    locationNodeId = LocationNodeId(locationNodeId),
    sortOrder = SortOrder(sortOrder),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将常用物品入口领域模型转换为 Room 实体。 */
internal fun PinnedItem.toEntity(): PinnedItemEntity = PinnedItemEntity(
    id = id.value,
    householdId = householdId.value,
    itemId = itemId.value,
    sortOrder = sortOrder.value,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将常用物品入口 Room 实体还原为领域模型。 */
internal fun PinnedItemEntity.toDomain(): PinnedItem = PinnedItem(
    id = PinnedItemId(id),
    householdId = HouseholdId(householdId),
    itemId = ItemId(itemId),
    sortOrder = SortOrder(sortOrder),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将本机搜索历史领域模型转换为 Room 实体。 */
internal fun LocalSearchHistory.toEntity(): LocalSearchHistoryEntity =
    LocalSearchHistoryEntity(
        id = id.value,
        deviceId = deviceId.value,
        displayQuery = displayQuery,
        normalizedQuery = normalizedQuery,
        filtersPayload = filtersPayload,
        filterHash = filterHash,
        executedAt = executedAt.epochMilliseconds,
    )

/** 将本机搜索历史 Room 实体还原为领域模型。 */
internal fun LocalSearchHistoryEntity.toDomain(): LocalSearchHistory = LocalSearchHistory(
    id = LocalSearchHistoryId(id),
    deviceId = DeviceId(deviceId),
    displayQuery = displayQuery,
    normalizedQuery = normalizedQuery,
    filtersPayload = filtersPayload,
    filterHash = filterHash,
    executedAt = UtcTimestamp(executedAt),
)

/** 将本机辅助偏好领域模型转换为 Room 实体。 */
internal fun LocalAccessibilityPreferences.toEntity(): LocalAccessibilityPreferencesEntity =
    LocalAccessibilityPreferencesEntity(
        deviceId = deviceId.value,
        displayMode = displayMode.name,
        followSystemFontScale = followSystemFontScale,
        highContrastEnabled = highContrastEnabled,
        autoReadConfirmationEnabled = autoReadConfirmationEnabled,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        speechRate = speechRate,
        volumeHintEnabled = volumeHintEnabled,
        updatedAt = updatedAt.epochMilliseconds,
    )

/** 将本机辅助偏好 Room 实体还原为领域模型。 */
internal fun LocalAccessibilityPreferencesEntity.toDomain(): LocalAccessibilityPreferences =
    LocalAccessibilityPreferences(
        deviceId = DeviceId(deviceId),
        displayMode = DisplayMode.valueOf(displayMode),
        followSystemFontScale = followSystemFontScale,
        highContrastEnabled = highContrastEnabled,
        autoReadConfirmationEnabled = autoReadConfirmationEnabled,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        speechRate = speechRate,
        volumeHintEnabled = volumeHintEnabled,
        updatedAt = UtcTimestamp(updatedAt),
    )

/** 将本机备份记录领域模型转换为 Room 实体。 */
internal fun LocalBackupRecord.toEntity(): LocalBackupRecordEntity = LocalBackupRecordEntity(
    id = id.value,
    deviceId = deviceId.value,
    householdId = householdId.value,
    packageHash = packageHash,
    sizeBytes = sizeBytes,
    status = status.name,
    opaqueDocumentUri = opaqueDocumentUri,
    createdAt = createdAt.epochMilliseconds,
    verifiedAt = verifiedAt?.epochMilliseconds,
)

/** 将本机备份记录 Room 实体还原为领域模型。 */
internal fun LocalBackupRecordEntity.toDomain(): LocalBackupRecord = LocalBackupRecord(
    id = LocalBackupRecordId(id),
    deviceId = DeviceId(deviceId),
    householdId = HouseholdId(householdId),
    packageHash = packageHash,
    sizeBytes = sizeBytes,
    status = LocalBackupStatus.valueOf(status),
    opaqueDocumentUri = opaqueDocumentUri,
    createdAt = UtcTimestamp(createdAt),
    verifiedAt = verifiedAt?.let(::UtcTimestamp),
)

