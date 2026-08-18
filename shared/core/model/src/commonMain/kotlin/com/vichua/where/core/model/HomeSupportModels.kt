package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 用户主动固定的常用位置引用。
 *
 * @property id 常用位置记录全局唯一标识。
 * @property householdId 所属家庭。
 * @property locationNodeId 被固定的现有位置节点。
 * @property sortOrder 用户手动设置的展示顺序。
 * @property createdAt 创建时间。
 * @property updatedAt 最近更新时间。
 * @property version 实体版本。
 * @property sourceDeviceId 最近修改设备。
 * @property deletedAt 取消固定时间，仍有效时为空。
 */
@Serializable
data class FavoriteLocation(
    val id: FavoriteLocationId,
    val householdId: HouseholdId,
    val locationNodeId: LocationNodeId,
    val sortOrder: SortOrder,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val version: EntityVersion,
    val sourceDeviceId: DeviceId,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(updatedAt >= createdAt) {
            "Favorite location update time must not precede creation time."
        }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Favorite location deletion time must not precede creation time."
        }
    }
}

/**
 * 仅保存在当前设备上的最近查找记录。
 *
 * @property id 搜索历史记录唯一标识。
 * @property deviceId 执行搜索的设备。
 * @property displayQuery 用户可见查询原文。
 * @property normalizedQuery 用于去重的标准化查询。
 * @property filtersPayload 带格式版本的本地筛选条件 JSON。
 * @property filterHash 标准化筛选条件摘要。
 * @property executedAt 最近执行时间。
 */
@Serializable
data class LocalSearchHistory(
    val id: LocalSearchHistoryId,
    val deviceId: DeviceId,
    val displayQuery: String,
    val normalizedQuery: String,
    val filtersPayload: String,
    val filterHash: String,
    val executedAt: UtcTimestamp,
) {
    init {
        require(displayQuery.isNotBlank()) { "Search display query must not be blank." }
        require(normalizedQuery.isNotBlank()) { "Normalized search query must not be blank." }
        require(filtersPayload.isNotBlank()) { "Search filters payload must not be blank." }
        require(filterHash.isNotBlank()) { "Search filter hash must not be blank." }
    }
}
