package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 位置历史事件产生原因。
 */
@Serializable
enum class ItemLocationReason {
    /** 首次创建物品时记录初始位置。 */
    CREATED,

    /** 用户主动更新单个物品的位置。 */
    MOVED,

    /** 后续批量移动扩展预留值，MVP 不产生该事件。 */
    BATCH_MOVED,

    /** 原位置被删除后回退到家庭根节点。 */
    LOCATION_DELETED,

    /** 重复位置合并后将物品迁移到目标节点。 */
    LOCATION_MERGED,

    /** 备份或数据包导入产生的位置事件。 */
    IMPORT,

    /** 后续设备同步产生的位置事件。 */
    SYNC,

    /** 用户纠正错误位置或完成位置待确认。 */
    CORRECTION,
}

/**
 * 物品位置变化的不可变历史事件。
 *
 * 路径快照用于保留事件发生时的可读信息，当前位置仍以物品档案为事实来源。
 *
 * @property id 位置事件全局唯一标识。
 * @property householdId 事件所属家庭。
 * @property itemId 发生位置变化的物品。
 * @property fromLocationId 原位置标识；首次创建时为空。
 * @property toLocationId 新位置标识；位置待确认时指向家庭根节点。
 * @property fromPathSnapshot 事件发生时的原位置路径快照。
 * @property toPathSnapshot 事件发生时的新位置路径快照。
 * @property reason 产生位置事件的原因。
 * @property note 可选事件说明。
 * @property occurredAt 事件发生的 UTC 时间。
 * @property sourceDeviceId 执行该操作的设备。
 * @property version 当前事件版本。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class ItemLocationEvent(
    val id: ItemLocationEventId,
    val householdId: HouseholdId,
    val itemId: ItemId,
    val fromLocationId: LocationNodeId?,
    val toLocationId: LocationNodeId,
    val fromPathSnapshot: String?,
    val toPathSnapshot: String,
    val reason: ItemLocationReason,
    val note: String? = null,
    val occurredAt: UtcTimestamp,
    val sourceDeviceId: DeviceId,
    val version: EntityVersion,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(fromPathSnapshot == null || fromPathSnapshot.isNotBlank()) {
            "Source location path snapshot must be null or non-blank."
        }
        require(toPathSnapshot.isNotBlank()) {
            "Target location path snapshot must not be blank."
        }
        require(note == null || note.isNotBlank()) {
            "Location event note must be null or non-blank."
        }
        require(reason != ItemLocationReason.CREATED || fromLocationId == null) {
            "Created location event must not have a source location."
        }
        require(deletedAt == null || deletedAt >= occurredAt) {
            "Location event deletion time must not precede occurrence time."
        }
    }
}
