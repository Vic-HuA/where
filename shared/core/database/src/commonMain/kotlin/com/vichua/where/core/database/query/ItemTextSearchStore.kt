package com.vichua.where.core.database.query

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.database.transaction.DatabaseTransactionRunner
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.LocalSearchHistory
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType

/**
 * 数据库层本地文字搜索结果。
 *
 * @property item 正式物品。
 * @property locationPath 当前完整位置路径。
 * @property thumbnailStorageKey 可选封面缩略图文件标识。
 */
data class StoredItemTextSearchResult(
    val item: Item,
    val locationPath: String,
    val thumbnailStorageKey: String?,
)

/**
 * 执行本地 FTS 查询并维护当前设备最近查找。
 */
class ItemTextSearchStore(
    private val database: WhereDatabase,
) {
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 返回当前家庭中首个仍有效设备。
     */
    suspend fun currentDeviceId(): DeviceId {
        val household = requireNotNull(database.householdDao().findFirstActive()) {
            "Cannot search without an active household."
        }
        val device = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(household.id),
        ) {
            "Cannot search without an active device."
        }
        return DeviceId(device.id)
    }

    /**
     * 执行安全 FTS 查询，并保存去重后的最近查找。
     */
    suspend fun search(
        ftsQuery: String,
        history: LocalSearchHistory,
    ): List<StoredItemTextSearchResult> {
        require(ftsQuery.isNotBlank()) { "FTS query must not be blank." }
        require(history.deviceId == currentDeviceId()) {
            "Search history belongs to another device."
        }

        val documents = database.itemSearchDao().search(
            ftsQuery = ftsQuery,
            limit = MAX_SEARCH_RESULTS,
        )
        val activeLocations = database.householdDao().findFirstActive()?.let { household ->
            database.locationNodeDao().findActiveTree(household.id)
        }.orEmpty().map { entity -> entity.toDomain() }
        val locationsById = activeLocations.associateBy(LocationNode::id)
        val itemEntities = documents.mapNotNull { document ->
            database.itemDao().findActiveById(document.itemId)
        }
        val coversByItemId = if (itemEntities.isEmpty()) {
            emptyMap()
        } else {
            database.photoAssetDao()
                .findActiveCovers(itemEntities.map { entity -> entity.id })
                .associateBy { entity -> entity.itemId }
        }
        val results = itemEntities.map { entity ->
            val item = entity.toDomain()
            StoredItemTextSearchResult(
                item = item,
                locationPath = buildLocationPath(item.currentLocationId, locationsById),
                thumbnailStorageKey = coversByItemId[item.id.value]?.thumbnailStorageKey,
            )
        }

        transactionRunner.write {
            homeSupportDao().deleteMatchingSearch(
                deviceId = history.deviceId.value,
                normalizedQuery = history.normalizedQuery,
                filterHash = history.filterHash,
            )
            homeSupportDao().upsertSearchHistory(history.toEntity())
            homeSupportDao().trimSearchHistory(
                deviceId = history.deviceId.value,
                keepCount = MAX_RECENT_SEARCHES,
            )
        }
        return results
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
        const val MAX_SEARCH_RESULTS = 50
        const val MAX_RECENT_SEARCHES = 5
        const val PATH_SEPARATOR = " · "
        const val UNKNOWN_LOCATION_TEXT = "位置待确认"
    }
}
