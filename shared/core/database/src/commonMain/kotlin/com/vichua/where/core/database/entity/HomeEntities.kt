package com.vichua.where.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 常用位置引用的 Room 持久化结构。
 *
 * @property id 常用位置记录全局 ID。
 * @property householdId 所属家庭 ID。
 * @property locationNodeId 被固定的位置节点 ID。
 * @property sortOrder 用户设置的展示顺序。
 * @property createdAt 创建时间。
 * @property updatedAt 最近更新时间。
 * @property version 实体版本。
 * @property sourceDeviceId 最近修改设备 ID。
 * @property deletedAt 取消固定时间。
 */
@Entity(
    tableName = "favorite_locations",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = LocationNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_node_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["household_id", "location_node_id", "deleted_at"]),
        Index(value = ["household_id", "sort_order", "deleted_at"]),
        Index(value = ["location_node_id"]),
    ],
)
data class FavoriteLocationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "location_node_id")
    val locationNodeId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val version: Long,
    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
)

/**
 * 当前设备最近查找的 Room 持久化结构。
 *
 * @property id 搜索历史记录 ID。
 * @property deviceId 执行搜索的设备 ID。
 * @property displayQuery 用户可见查询原文。
 * @property normalizedQuery 标准化查询文本。
 * @property filtersPayload 带版本的筛选条件 JSON。
 * @property filterHash 标准化筛选条件摘要。
 * @property executedAt 最近执行时间。
 */
@Entity(
    tableName = "local_search_history",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            value = ["device_id", "normalized_query", "filter_hash"],
            unique = true,
        ),
        Index(value = ["device_id", "executed_at"]),
    ],
)
data class LocalSearchHistoryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "display_query")
    val displayQuery: String,
    @ColumnInfo(name = "normalized_query")
    val normalizedQuery: String,
    @ColumnInfo(name = "filters_payload")
    val filtersPayload: String,
    @ColumnInfo(name = "filter_hash")
    val filterHash: String,
    @ColumnInfo(name = "executed_at")
    val executedAt: Long,
)
