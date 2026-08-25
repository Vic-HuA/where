package com.vichua.where.feature.search.home

import com.vichua.where.core.model.FavoriteLocationId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocalSearchHistoryId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.UtcTimestamp

/**
 * 首页最近物品的只读摘要。
 *
 * @property itemId 物品全局 ID。
 * @property name 物品名称。
 * @property locationPath 当前完整位置路径。
 * @property updatedAt 最近更新时间。
 * @property thumbnailStorageKey 可选封面缩略图文件标识。
 */
data class HomeItemSummary(
    val itemId: ItemId,
    val name: String,
    val locationPath: String,
    val updatedAt: UtcTimestamp,
    val thumbnailStorageKey: String?,
)

/**
 * 首页最近查找的只读摘要。
 *
 * @property historyId 当前设备搜索历史 ID。
 * @property displayQuery 用户可见查询原文。
 */
data class RecentSearchSummary(
    val historyId: LocalSearchHistoryId,
    val displayQuery: String,
)

/**
 * 首页常用位置的只读摘要。
 *
 * @property favoriteId 常用位置引用 ID。
 * @property locationNodeId 对应位置节点 ID。
 * @property name 位置名称。
 * @property iconKey 受控位置图标键。
 * @property itemCount 该位置及其下级上的未删除物品数量。
 */
data class FavoriteLocationSummary(
    val favoriteId: FavoriteLocationId,
    val locationNodeId: LocationNodeId,
    val name: String,
    val iconKey: String?,
    val itemCount: Long,
)

/**
 * 首页一次性加载所需的全部本地数据。
 *
 * @property recentItems 最近更新的最多三件物品。
 * @property recentSearches 当前设备最近执行的最多五条去重查询。
 * @property favoriteLocations 用户主动固定的常用位置。
 * @property locationUnconfirmedCount 位置待确认物品数量。
 */
data class HomeSnapshot(
    val recentItems: List<HomeItemSummary>,
    val recentSearches: List<RecentSearchSummary>,
    val favoriteLocations: List<FavoriteLocationSummary>,
    val locationUnconfirmedCount: Long,
)

/**
 * 首页本地数据仓储契约。
 */
interface HomeSnapshotRepository {
    /**
     * 加载当前家庭和当前设备的首页摘要。
     */
    suspend fun load(): HomeSnapshot

    /**
     * 加载当前家庭全部未删除物品，不截成首页三件。
     */
    suspend fun loadAllItems(): List<HomeItemSummary>

    /**
     * 加载指定位置及其下级上的未删除物品。
     */
    suspend fun loadItemsAtLocation(locationId: LocationNodeId): List<HomeItemSummary>

    /**
     * 加载位置待确认的未删除物品。
     */
    suspend fun loadLocationUnconfirmedItems(): List<HomeItemSummary>
}

/**
 * 加载首页所需的本地摘要，不触发网络、AI 或语音服务。
 */
class LoadHomeSnapshotUseCase(
    private val repository: HomeSnapshotRepository,
) {
    /**
     * 返回当前首页快照。
     */
    suspend operator fun invoke(): HomeSnapshot = repository.load()
}

/**
 * 加载全部物品列表，供首页「查看全部」使用。
 */
class LoadAllItemsUseCase(
    private val repository: HomeSnapshotRepository,
) {
    /**
     * 按最近更新时间返回当前家庭全部未删除物品。
     */
    suspend operator fun invoke(): List<HomeItemSummary> = repository.loadAllItems()
}

/**
 * 查看某个位置及其下级里的物品。
 */
class LoadItemsAtLocationUseCase(
    private val repository: HomeSnapshotRepository,
) {
    /**
     * 按最近更新时间返回该位置树下的未删除物品。
     */
    suspend operator fun invoke(locationId: LocationNodeId): List<HomeItemSummary> =
        repository.loadItemsAtLocation(locationId)
}

/**
 * 加载位置待确认物品，供提醒条进入处理列表。
 */
class LoadLocationUnconfirmedItemsUseCase(
    private val repository: HomeSnapshotRepository,
) {
    /**
     * 按最近更新时间返回仍待重新选择位置的物品。
     */
    suspend operator fun invoke(): List<HomeItemSummary> =
        repository.loadLocationUnconfirmedItems()
}
