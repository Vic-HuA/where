package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 当前设备上的未完成物品录入草稿。
 *
 * 草稿属于设备本地临时数据，不进入家庭备份、完整导出、变更记录或后续同步。
 *
 * @property id 草稿全局唯一标识。
 * @property householdId 草稿对应的家庭。
 * @property deviceId 创建并持有草稿的设备。
 * @property payload 带格式版本的结构化 JSON 内容。
 * @property createdAt 草稿创建时间。
 * @property updatedAt 最近一次用户修改时间。
 * @property expiresAt 草稿过期时间，必须晚于或等于最近修改时间。
 */
@Serializable
data class ItemDraft(
    val id: ItemDraftId,
    val householdId: HouseholdId,
    val deviceId: DeviceId,
    val payload: String,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val expiresAt: UtcTimestamp,
) {
    init {
        require(payload.isNotBlank()) { "Item draft payload must not be blank." }
        require(updatedAt >= createdAt) { "Item draft update time must not precede creation time." }
        require(expiresAt >= updatedAt) { "Item draft expiration time must not precede update time." }
    }
}

/**
 * 进入备份、导入检查和后续同步的实体类型。
 */
@Serializable
enum class ChangeEntityType {
    /** 家庭根记录。 */
    HOUSEHOLD,

    /** 参与家庭数据的设备记录。 */
    DEVICE,

    /** 位置树节点。 */
    LOCATION_NODE,

    /** 物品档案。 */
    ITEM,

    /** 物品别名。 */
    ITEM_ALIAS,

    /** 物品分类。 */
    CATEGORY,

    /** 物品照片。 */
    PHOTO_ASSET,

    /** 位置代表照片。 */
    LOCATION_PHOTO_ASSET,

    /** 用户主动保存的语音名称。 */
    VOICE_LABEL_ASSET,

    /** 物品位置历史。 */
    ITEM_LOCATION_EVENT,

    /** 用户主动固定的常用位置。 */
    FAVORITE_LOCATION,
}

/**
 * 正式数据的变更操作类型。
 */
@Serializable
enum class ChangeOperation {
    /** 创建正式实体。 */
    CREATE,

    /** 更新正式实体。 */
    UPDATE,

    /** 软删除正式实体。 */
    DELETE,

    /** 撤销软删除并恢复正式实体。 */
    RESTORE,
}

/**
 * 正式数据的可追踪变更记录。
 *
 * @property id 变更记录全局唯一标识。
 * @property householdId 变更所属家庭。
 * @property entityType 被修改的实体类型。
 * @property entityId 被修改实体的原始全局 ID 文本。
 * @property operation 变更操作类型。
 * @property entityVersion 变更完成后的实体版本。
 * @property sourceDeviceId 执行变更的设备。
 * @property occurredAt 变更发生时间。
 * @property payloadHash 可选实体载荷摘要，不保存敏感内容原文。
 */
@Serializable
data class ChangeRecord(
    val id: ChangeRecordId,
    val householdId: HouseholdId,
    val entityType: ChangeEntityType,
    val entityId: String,
    val operation: ChangeOperation,
    val entityVersion: EntityVersion,
    val sourceDeviceId: DeviceId,
    val occurredAt: UtcTimestamp,
    val payloadHash: String? = null,
) {
    init {
        require(entityId.isNotBlank()) { "Changed entity ID must not be blank." }
        require(payloadHash == null || payloadHash.isNotBlank()) {
            "Change payload hash must be null or non-blank."
        }
    }
}
