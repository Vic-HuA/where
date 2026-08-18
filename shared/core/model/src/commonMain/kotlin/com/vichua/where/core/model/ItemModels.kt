package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 物品档案状态。
 */
@Serializable
enum class ItemStatus {
    /** 物品具有有效当前位置，可正常展示和搜索。 */
    ACTIVE,

    /** 物品暂时关联家庭根节点，等待用户重新确认具体位置。 */
    LOCATION_UNCONFIRMED,

    /** 预留归档状态，MVP 界面和搜索暂不启用。 */
    ARCHIVED,
}

/**
 * 家庭中的物品档案。
 *
 * 一条记录表示一个物品，或一组始终放在同一位置的同类物品。
 *
 * @property id 物品全局唯一标识。
 * @property householdId 物品所属家庭。
 * @property currentLocationId 当前唯一位置；位置待确认时指向家庭根节点。
 * @property name 用户可见且不能为空的物品名称。
 * @property normalizedName 用于去重和搜索的标准化名称。
 * @property categoryId 可选分类标识。
 * @property quantity 大于 0 的有限数量。
 * @property unit 可选数量单位。
 * @property locationDescription 可选位置补充说明，位置待确认时应保留原始描述。
 * @property note 可选备注。
 * @property status 当前物品状态。
 * @property createdAt 物品创建时间。
 * @property updatedAt 用户可见档案最近一次变更时间。
 * @property version 当前实体版本。
 * @property sourceDeviceId 最近修改该物品的设备。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class Item(
    val id: ItemId,
    val householdId: HouseholdId,
    val currentLocationId: LocationNodeId,
    val name: String,
    val normalizedName: String,
    val categoryId: CategoryId? = null,
    val quantity: Double = DEFAULT_QUANTITY,
    val unit: String? = null,
    val locationDescription: String? = null,
    val note: String? = null,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val version: EntityVersion,
    val sourceDeviceId: DeviceId,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(name.isNotBlank()) { "Item name must not be blank." }
        require(normalizedName.isNotBlank()) { "Normalized item name must not be blank." }
        require(quantity.isFinite() && quantity > 0.0) {
            "Item quantity must be finite and greater than zero."
        }
        require(unit == null || unit.isNotBlank()) { "Item unit must be null or non-blank." }
        require(locationDescription == null || locationDescription.isNotBlank()) {
            "Item location description must be null or non-blank."
        }
        require(note == null || note.isNotBlank()) { "Item note must be null or non-blank." }
        require(updatedAt >= createdAt) { "Item update time must not precede creation time." }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Item deletion time must not precede creation time."
        }
    }

    /**
     * 判断物品是否已经软删除，正常界面和搜索必须排除已删除物品。
     */
    val isDeleted: Boolean
        get() = deletedAt != null

    private companion object {
        const val DEFAULT_QUANTITY = 1.0
    }
}

/**
 * 物品的独立别名记录。
 *
 * @property id 别名记录全局唯一标识。
 * @property itemId 别名所属物品。
 * @property alias 用户输入的别名原文。
 * @property normalizedAlias 用于同一物品内去重和搜索的标准化别名。
 * @property createdAt 别名创建时间。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class ItemAlias(
    val id: ItemAliasId,
    val itemId: ItemId,
    val alias: String,
    val normalizedAlias: String,
    val createdAt: UtcTimestamp,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(alias.isNotBlank()) { "Item alias must not be blank." }
        require(normalizedAlias.isNotBlank()) { "Normalized item alias must not be blank." }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Item alias deletion time must not precede creation time."
        }
    }
}

/**
 * 家庭或系统提供的物品分类。
 *
 * @property id 分类全局唯一标识。
 * @property householdId 所属家庭；系统分类使用约定的系统家庭标识范围。
 * @property name 用户可见且不能为空的分类名称。
 * @property normalizedName 用于去重和搜索的标准化名称。
 * @property iconKey 应用内受控分类图标标识。
 * @property sortOrder 分类展示顺序。
 * @property isSystem 是否为应用内置系统分类。
 * @property createdAt 分类创建时间。
 * @property updatedAt 分类最近更新时间。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class Category(
    val id: CategoryId,
    val householdId: HouseholdId,
    val name: String,
    val normalizedName: String,
    val iconKey: String,
    val sortOrder: SortOrder,
    val isSystem: Boolean,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank." }
        require(normalizedName.isNotBlank()) { "Normalized category name must not be blank." }
        require(iconKey.isNotBlank()) { "Category icon key must not be blank." }
        require(updatedAt >= createdAt) { "Category update time must not precede creation time." }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Category deletion time must not precede creation time."
        }
    }
}
