package com.vichua.where.core.database.query

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.PhotoAsset

/**
 * 数据库层聚合后的物品详情。
 *
 * @property item 正式物品。
 * @property aliases 未删除别名。
 * @property categoryName 可选分类名称。
 * @property locationPath 当前完整位置路径。
 * @property sourceDeviceName 来源设备名称。
 * @property photos 未删除照片。
 * @property locationHistory 未删除位置历史。
 */
data class StoredItemDetail(
    val item: Item,
    val aliases: List<ItemAlias>,
    val categoryName: String?,
    val locationPath: String,
    val sourceDeviceName: String,
    val photos: List<PhotoAsset>,
    val locationHistory: List<ItemLocationEvent>,
    val voiceLabelStorageKey: String? = null,
    val isPinned: Boolean = false,
)

/**
 * 从正式表聚合物品详情。
 */
class ItemDetailStore(
    private val database: WhereDatabase,
) {
    /**
     * 查询指定未删除物品详情，不存在时返回空。
     */
    suspend fun find(itemId: ItemId): StoredItemDetail? {
        val item = database.itemDao().findActiveById(itemId.value)?.toDomain()
            ?: return null
        val locations = database.locationNodeDao()
            .findActiveTree(item.householdId.value)
            .map { entity -> entity.toDomain() }
        val locationsById = locations.associateBy(LocationNode::id)
        val categoryName = item.categoryId?.let { categoryId ->
            database.categoryDao().findActiveById(categoryId.value)?.name
        }
        val sourceDeviceName = database.deviceDao()
            .findById(item.sourceDeviceId.value)
            ?.displayName
            ?: UNKNOWN_DEVICE_NAME

        return StoredItemDetail(
            item = item,
            aliases = database.itemAliasDao()
                .findActiveByItem(item.id.value)
                .map { entity -> entity.toDomain() },
            categoryName = categoryName,
            locationPath = buildLocationPath(item.currentLocationId, locationsById),
            sourceDeviceName = sourceDeviceName,
            photos = database.photoAssetDao()
                .findActiveByItem(item.id.value)
                .map { entity -> entity.toDomain() },
            locationHistory = database.itemLocationEventDao()
                .findActiveByItem(item.id.value)
                .map { entity -> entity.toDomain() },
            voiceLabelStorageKey = database.voiceLabelAssetDao()
                .findActiveByItem(item.id.value)
                .firstOrNull()
                ?.storageKey,
            isPinned = database.homeSupportDao()
                .findActivePinnedByItem(item.id.value) != null,
        )
    }

    /**
     * 从当前位置向上构建不包含家庭根节点的路径。
     */
    private fun buildLocationPath(
        locationId: LocationNodeId,
        locationsById: Map<LocationNodeId, LocationNode>,
    ): String {
        val names = mutableListOf<String>()
        val visitedIds = mutableSetOf<LocationNodeId>()
        var currentLocation = locationsById[locationId]
        while (currentLocation != null && visitedIds.add(currentLocation.id)) {
            if (currentLocation.type != LocationType.HOME) {
                names += currentLocation.name
            }
            currentLocation = currentLocation.parentId?.let(locationsById::get)
        }
        return names.asReversed().joinToString(PATH_SEPARATOR)
            .ifBlank { UNKNOWN_LOCATION_TEXT }
    }

    private companion object {
        const val PATH_SEPARATOR = " · "
        const val UNKNOWN_LOCATION_TEXT = "位置待确认"
        const val UNKNOWN_DEVICE_NAME = "未知设备"
    }
}
