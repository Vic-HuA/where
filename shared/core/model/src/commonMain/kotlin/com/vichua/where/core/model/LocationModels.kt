package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 位置树节点类型。
 *
 * 类型表示推荐语义而不是固定深度，业务允许跳过不适用的中间层级。
 */
@Serializable
enum class LocationType {
    /** 家庭唯一根节点，不能作为其他位置的子节点。 */
    HOME,

    /** 房间，例如客厅、卧室或书房。 */
    ROOM,

    /** 房间内区域，例如阳台角落。 */
    AREA,

    /** 家具，例如书柜或床头柜。 */
    FURNITURE,

    /** 容器，例如收纳盒或工具箱。 */
    CONTAINER,

    /** 具体位置，例如第二层或左侧抽屉。 */
    SLOT,
}

/**
 * 可变深度位置树中的统一节点。
 *
 * @property id 位置节点全局唯一标识。
 * @property householdId 节点所属家庭。
 * @property parentId 父节点标识，家庭根节点必须为空。
 * @property type 位置语义类型。
 * @property name 用户可见且不能为空的位置名称。
 * @property normalizedName 用于同级去重和搜索的标准化名称。
 * @property description 可选位置说明。
 * @property iconKey 应用内受控图标标识，不允许保存外部 URL 或文件路径。
 * @property sortOrder 同一父节点下的稳定展示顺序。
 * @property createdAt 节点创建时间。
 * @property updatedAt 节点最近一次正式变更时间。
 * @property version 当前实体版本。
 * @property sourceDeviceId 最近修改该节点的设备。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class LocationNode(
    val id: LocationNodeId,
    val householdId: HouseholdId,
    val parentId: LocationNodeId?,
    val type: LocationType,
    val name: String,
    val normalizedName: String,
    val description: String? = null,
    val iconKey: String? = null,
    val sortOrder: SortOrder,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val version: EntityVersion,
    val sourceDeviceId: DeviceId,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(name.isNotBlank()) { "Location name must not be blank." }
        require(normalizedName.isNotBlank()) { "Normalized location name must not be blank." }
        require(description == null || description.isNotBlank()) {
            "Location description must be null or non-blank."
        }
        require(iconKey == null || iconKey.isNotBlank()) {
            "Location icon key must be null or non-blank."
        }
        require(parentId != id) { "Location node cannot be its own parent." }
        require(type != LocationType.HOME || parentId == null) {
            "Home location must not have a parent."
        }
        require(type == LocationType.HOME || parentId != null) {
            "Non-home location must have a parent."
        }
        require(updatedAt >= createdAt) { "Location update time must not precede creation time." }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Location deletion time must not precede creation time."
        }
    }

    /**
     * 判断当前节点是否为不能通过普通位置操作移动或删除的家庭根节点。
     */
    val isHouseholdRoot: Boolean
        get() = type == LocationType.HOME
}
