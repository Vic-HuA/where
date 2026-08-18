package com.vichua.where.core.database.mapper

import com.vichua.where.core.database.entity.FavoriteLocationEntity
import com.vichua.where.core.database.entity.LocalSearchHistoryEntity
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.FavoriteLocation
import com.vichua.where.core.model.FavoriteLocationId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocalSearchHistory
import com.vichua.where.core.model.LocalSearchHistoryId
import com.vichua.where.core.model.LocationNodeId
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
