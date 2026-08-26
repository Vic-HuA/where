package com.vichua.where.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 家庭根记录的 Room 持久化结构。
 *
 * @property id 家庭全局唯一 ID。
 * @property name 用户可见家庭名称。
 * @property createdAt 创建时间的 UTC Epoch 毫秒值。
 * @property updatedAt 最近更新时间的 UTC Epoch 毫秒值。
 * @property version 实体版本。
 * @property sourceDeviceId 最近修改设备 ID。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey
    val id: String,
    val name: String,
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
 * 设备记录的 Room 持久化结构。
 *
 * @property id 设备全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property displayName 用户可见设备名称。
 * @property platform 稳定的平台枚举名称。
 * @property publicKey 后续配对使用的公钥文本。
 * @property pairedAt 配对时间。
 * @property lastSeenAt 最近活动时间。
 * @property revokedAt 撤销时间。
 * @property createdAt 创建时间。
 */
@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["household_id"]),
    ],
)
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val platform: String,
    @ColumnInfo(name = "public_key")
    val publicKey: String?,
    @ColumnInfo(name = "paired_at")
    val pairedAt: Long?,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long?,
    @ColumnInfo(name = "revoked_at")
    val revokedAt: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * 可变深度位置树节点的 Room 持久化结构。
 *
 * @property id 位置节点全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property parentId 父位置 ID，家庭根节点为空。
 * @property type 稳定的位置类型枚举名称。
 * @property name 用户可见位置名称。
 * @property normalizedName 标准化位置名称。
 * @property description 可选位置说明。
 * @property iconKey 受控图标键。
 * @property sortOrder 同级展示顺序。
 * @property createdAt 创建时间。
 * @property updatedAt 最近更新时间。
 * @property version 实体版本。
 * @property sourceDeviceId 最近修改设备 ID。
 * @property deletedAt 软删除时间。
 */
@Entity(
    tableName = "location_nodes",
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
            childColumns = ["parent_id"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true,
        ),
    ],
    indices = [
        Index(value = ["household_id", "parent_id", "deleted_at"]),
        Index(value = ["household_id", "normalized_name", "type", "deleted_at"]),
        Index(value = ["parent_id"]),
    ],
)
data class LocationNodeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "parent_id")
    val parentId: String?,
    val type: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val description: String?,
    @ColumnInfo(name = "icon_key")
    val iconKey: String?,
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
 * 物品分类的 Room 持久化结构。
 *
 * @property id 分类全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property name 用户可见分类名称。
 * @property normalizedName 标准化分类名称。
 * @property iconKey 受控分类图标键。
 * @property sortOrder 展示顺序。
 * @property isSystem 是否为系统分类。
 * @property createdAt 创建时间。
 * @property updatedAt 最近更新时间。
 * @property deletedAt 软删除时间。
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["household_id", "normalized_name", "deleted_at"]),
        Index(value = ["household_id", "sort_order", "deleted_at"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "icon_key")
    val iconKey: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
)

/**
 * 物品档案的 Room 持久化结构。
 *
 * @property id 物品全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property currentLocationId 当前唯一位置 ID。
 * @property name 用户可见物品名称。
 * @property normalizedName 标准化物品名称。
 * @property categoryId 可选分类 ID。
 * @property quantity 物品数量。
 * @property unit 可选数量单位。
 * @property locationDescription 可选位置补充说明。
 * @property note 可选备注。
 * @property status 稳定的物品状态枚举名称。
 * @property createdAt 创建时间。
 * @property updatedAt 用户可见档案最近更新时间。
 * @property version 实体版本。
 * @property sourceDeviceId 最近修改设备 ID。
 * @property deletedAt 软删除时间。
 */
@Entity(
    tableName = "items",
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
            childColumns = ["current_location_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["household_id", "current_location_id", "deleted_at"]),
        Index(value = ["household_id", "normalized_name", "deleted_at"]),
        Index(value = ["household_id", "updated_at"]),
        Index(value = ["current_location_id"]),
        Index(value = ["category_id"]),
    ],
)
data class ItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "current_location_id")
    val currentLocationId: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    val quantity: Double,
    val unit: String?,
    @ColumnInfo(name = "location_description")
    val locationDescription: String?,
    val note: String?,
    val status: String,
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
 * 物品别名的 Room 持久化结构。
 *
 * @property id 别名记录全局唯一 ID。
 * @property itemId 所属物品 ID。
 * @property alias 用户输入别名。
 * @property normalizedAlias 标准化别名。
 * @property createdAt 创建时间。
 * @property deletedAt 软删除时间。
 */
@Entity(
    tableName = "item_aliases",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["item_id", "normalized_alias", "deleted_at"]),
    ],
)
data class ItemAliasEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val alias: String,
    @ColumnInfo(name = "normalized_alias")
    val normalizedAlias: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
)

/**
 * 物品照片的 Room 持久化结构。
 *
 * @property id 照片记录全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property itemId 所属物品 ID。
 * @property role 稳定的照片用途枚举名称。
 * @property storageKey 受控原图文件标识。
 * @property thumbnailStorageKey 受控缩略图文件标识。
 * @property mimeType 图片 MIME 类型。
 * @property width 原图像素宽度。
 * @property height 原图像素高度。
 * @property sizeBytes 原图字节数。
 * @property contentHash 内容摘要。
 * @property integrityStatus 媒体完整性状态。
 * @property lastIntegrityCheckedAt 最近完整性检查时间。
 * @property sortOrder 画廊顺序。
 * @property isCover 是否为封面。
 * @property capturedAt 原始拍摄时间。
 * @property createdAt 导入时间。
 * @property updatedAt 元数据最近更新时间。
 * @property version 实体版本。
 * @property sourceDeviceId 来源或最近修改设备 ID。
 * @property deletedAt 软删除时间。
 */
@Entity(
    tableName = "photo_assets",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["item_id", "sort_order", "deleted_at"]),
        Index(value = ["item_id", "is_cover", "deleted_at"]),
        Index(value = ["content_hash"]),
        Index(value = ["integrity_status", "deleted_at"]),
        Index(value = ["household_id"]),
    ],
)
data class PhotoAssetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val role: String,
    @ColumnInfo(name = "storage_key")
    val storageKey: String,
    @ColumnInfo(name = "thumbnail_storage_key")
    val thumbnailStorageKey: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    @ColumnInfo(name = "integrity_status")
    val integrityStatus: String,
    @ColumnInfo(name = "last_integrity_checked_at")
    val lastIntegrityCheckedAt: Long?,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_cover")
    val isCover: Boolean,
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long?,
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
 * 位置代表照片的 Room 持久化结构。
 *
 * 不保存物品用途角色；每个未删除位置最多一张未删除封面。
 */
@Entity(
    tableName = "location_photo_assets",
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
        Index(value = ["location_node_id", "sort_order", "deleted_at"]),
        Index(value = ["location_node_id", "is_cover", "deleted_at"]),
        Index(value = ["content_hash"]),
        Index(value = ["integrity_status", "deleted_at"]),
        Index(value = ["household_id"]),
    ],
)
data class LocationPhotoAssetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "location_node_id")
    val locationNodeId: String,
    @ColumnInfo(name = "storage_key")
    val storageKey: String,
    @ColumnInfo(name = "thumbnail_storage_key")
    val thumbnailStorageKey: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    @ColumnInfo(name = "integrity_status")
    val integrityStatus: String,
    @ColumnInfo(name = "last_integrity_checked_at")
    val lastIntegrityCheckedAt: Long?,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_cover")
    val isCover: Boolean,
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
 * 物品位置历史的 Room 持久化结构。
 *
 * @property id 位置事件全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property itemId 所属物品 ID。
 * @property fromLocationId 原位置 ID。
 * @property toLocationId 新位置 ID。
 * @property fromPathSnapshot 原位置路径快照。
 * @property toPathSnapshot 新位置路径快照。
 * @property reason 稳定的位置事件原因枚举名称。
 * @property note 可选事件说明。
 * @property occurredAt 事件发生时间。
 * @property sourceDeviceId 操作设备 ID。
 * @property version 实体版本。
 * @property deletedAt 软删除时间。
 */
@Entity(
    tableName = "item_location_events",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = LocationNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_location_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = LocationNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_location_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["item_id", "occurred_at"]),
        Index(value = ["household_id", "to_location_id", "occurred_at"]),
        Index(value = ["from_location_id"]),
        Index(value = ["to_location_id"]),
    ],
)
data class ItemLocationEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String?,
    @ColumnInfo(name = "to_location_id")
    val toLocationId: String,
    @ColumnInfo(name = "from_path_snapshot")
    val fromPathSnapshot: String?,
    @ColumnInfo(name = "to_path_snapshot")
    val toPathSnapshot: String,
    val reason: String,
    val note: String?,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String,
    val version: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
)

/**
 * 当前设备物品草稿的 Room 持久化结构。
 *
 * @property id 草稿全局唯一 ID。
 * @property householdId 对应家庭 ID。
 * @property deviceId 持有草稿的设备 ID。
 * @property payload 带版本的结构化 JSON。
 * @property createdAt 创建时间。
 * @property updatedAt 最近修改时间。
 * @property expiresAt 过期时间。
 */
@Entity(
    tableName = "item_drafts",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["device_id", "household_id", "expires_at", "updated_at"]),
        Index(value = ["household_id"]),
    ],
)
data class ItemDraftEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    val payload: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
)

/**
 * 正式数据变更记录的 Room 持久化结构。
 *
 * @property id 变更记录全局唯一 ID。
 * @property householdId 所属家庭 ID。
 * @property entityType 稳定的实体类型枚举名称。
 * @property entityId 被修改实体的原始 ID。
 * @property operation 稳定的操作类型枚举名称。
 * @property entityVersion 变更后的实体版本。
 * @property sourceDeviceId 操作设备 ID。
 * @property occurredAt 变更发生时间。
 * @property payloadHash 可选载荷摘要。
 */
@Entity(
    tableName = "change_records",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["household_id", "occurred_at"]),
        Index(value = ["entity_type", "entity_id", "entity_version"]),
    ],
)
data class ChangeRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    val operation: String,
    @ColumnInfo(name = "entity_version")
    val entityVersion: Long,
    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String?,
)
