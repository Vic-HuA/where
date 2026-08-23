package com.vichua.where.core.database.query

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.entity.ItemEntity
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.model.FavoriteLocation
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.LocalSearchHistory
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType

/**
 * 数据库层提供的首页最近物品数据。
 *
 * @property item 正式物品领域模型。
 * @property locationPath 根据当前有效位置树实时生成的路径。
 * @property thumbnailStorageKey 可选封面缩略图文件标识。
 */
data class StoredHomeItem(
    val item: Item,
    val locationPath: String,
    val thumbnailStorageKey: String?,
)

/**
 * 数据库层提供的首页常用位置数据。
 *
 * @property favorite 常用位置领域模型。
 * @property location 对应的未删除位置节点。
 * @property itemCount 直接关联该位置的未删除物品数量。
 */
data class StoredFavoriteLocation(
    val favorite: FavoriteLocation,
    val location: LocationNode,
    val itemCount: Long,
)

/**
 * 数据库层首页快照。
 *
 * @property recentItems 最近更新物品。
 * @property recentSearches 当前设备最近查找。
 * @property favoriteLocations 主动固定的常用位置。
 * @property locationUnconfirmedCount 位置待确认物品数量。
 */
data class StoredHomeSnapshot(
    val recentItems: List<StoredHomeItem>,
    val recentSearches: List<LocalSearchHistory>,
    val favoriteLocations: List<StoredFavoriteLocation>,
    val locationUnconfirmedCount: Long,
)

/**
 * 从本地正式表和设备本地表聚合首页快照。
 *
 * 路径和物品数量实时计算，避免维护第二套可能过期的首页事实数据。
 */
class HomeSnapshotStore(
    private val database: WhereDatabase,
) {
    /**
     * 加载当前唯一家庭和当前有效设备的首页数据。
     *
     * 没有家庭时返回空快照，启动路由会继续展示初始化页面。
     */
    suspend fun load(): StoredHomeSnapshot {
        val household = database.householdDao().findFirstActive()
            ?: return EMPTY_SNAPSHOT
        val device = database.deviceDao().findFirstActiveByHousehold(household.id)
        val activeLocations = database.locationNodeDao()
            .findActiveTree(household.id)
            .map { entity -> entity.toDomain() }
        val locationsById = activeLocations.associateBy(LocationNode::id)

        val recentItems = mapStoredItems(
            itemEntities = database.itemDao().findRecentActive(
                householdId = household.id,
                limit = MAX_RECENT_ITEMS,
            ),
            locationsById = locationsById,
        )

        val recentSearches = if (device == null) {
            emptyList()
        } else {
            database.homeSupportDao()
                .findRecentSearches(device.id, MAX_RECENT_SEARCHES)
                .map { entity -> entity.toDomain() }
        }

        val favoriteLocations = database.homeSupportDao()
            .findActiveFavoriteLocations(household.id, MAX_FAVORITE_LOCATIONS)
            .mapNotNull { favoriteEntity ->
                val favorite = favoriteEntity.toDomain()
                val location = locationsById[favorite.locationNodeId] ?: return@mapNotNull null
                StoredFavoriteLocation(
                    favorite = favorite,
                    location = location,
                    itemCount = database.itemDao().countActiveAtLocation(location.id.value),
                )
            }

        return StoredHomeSnapshot(
            recentItems = recentItems,
            recentSearches = recentSearches,
            favoriteLocations = favoriteLocations,
            locationUnconfirmedCount = database.itemDao()
                .countLocationUnconfirmed(household.id),
        )
    }

    /**
     * 加载当前家庭全部未删除物品，供「查看全部」页使用，不截成首页三件。
     */
    suspend fun loadAllItems(): List<StoredHomeItem> {
        val household = database.householdDao().findFirstActive() ?: return emptyList()
        val activeLocations = database.locationNodeDao()
            .findActiveTree(household.id)
            .map { entity -> entity.toDomain() }
        return mapStoredItems(
            itemEntities = database.itemDao().findActiveByHousehold(household.id),
            locationsById = activeLocations.associateBy(LocationNode::id),
        )
    }

    /**
     * 补上实时路径和封面，避免首页和全部列表各维护一套展示字段。
     */
    private suspend fun mapStoredItems(
        itemEntities: List<ItemEntity>,
        locationsById: Map<LocationNodeId, LocationNode>,
    ): List<StoredHomeItem> {
        val coverByItemId = if (itemEntities.isEmpty()) {
            emptyMap()
        } else {
            database.photoAssetDao()
                .findActiveCovers(itemEntities.map { entity -> entity.id })
                .associateBy { entity -> entity.itemId }
        }
        return itemEntities.map { entity ->
            val item = entity.toDomain()
            StoredHomeItem(
                item = item,
                locationPath = buildLocationPath(item.currentLocationId, locationsById),
                thumbnailStorageKey = coverByItemId[item.id.value]?.thumbnailStorageKey,
            )
        }
    }

    /**
     * 从当前位置沿父链生成路径，并排除仅用于数据归属的家庭根节点。
     */
    private fun buildLocationPath(
        locationId: LocationNodeId,
        locationsById: Map<LocationNodeId, LocationNode>,
    ): String {
        val pathNames = mutableListOf<String>()
        val visitedLocationIds = mutableSetOf<LocationNodeId>()
        var currentLocation = locationsById[locationId]

        while (currentLocation != null && visitedLocationIds.add(currentLocation.id)) {
            if (currentLocation.type != LocationType.HOME) {
                pathNames += currentLocation.name
            }
            currentLocation = currentLocation.parentId?.let(locationsById::get)
        }
        return pathNames.asReversed().joinToString(PATH_SEPARATOR)
            .ifBlank { UNKNOWN_LOCATION_TEXT }
    }

    private companion object {
        const val MAX_RECENT_ITEMS = 3
        const val MAX_RECENT_SEARCHES = 5
        const val MAX_FAVORITE_LOCATIONS = 8
        const val PATH_SEPARATOR = " · "
        const val UNKNOWN_LOCATION_TEXT = "位置待确认"

        val EMPTY_SNAPSHOT = StoredHomeSnapshot(
            recentItems = emptyList(),
            recentSearches = emptyList(),
            favoriteLocations = emptyList(),
            locationUnconfirmedCount = 0L,
        )
    }
}
