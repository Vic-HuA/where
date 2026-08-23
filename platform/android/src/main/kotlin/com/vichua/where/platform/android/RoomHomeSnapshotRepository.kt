package com.vichua.where.platform.android

import com.vichua.where.core.database.query.HomeSnapshotStore
import com.vichua.where.feature.search.home.FavoriteLocationSummary
import com.vichua.where.feature.search.home.HomeItemSummary
import com.vichua.where.feature.search.home.HomeSnapshot
import com.vichua.where.feature.search.home.HomeSnapshotRepository
import com.vichua.where.feature.search.home.RecentSearchSummary

/**
 * 使用共享 Room 查询 Store 实现 Android 首页仓储契约。
 */
class RoomHomeSnapshotRepository(
    private val store: HomeSnapshotStore,
) : HomeSnapshotRepository {
    /**
     * 加载数据库快照并转换为功能层只读摘要。
     */
    override suspend fun load(): HomeSnapshot {
        val snapshot = store.load()
        return HomeSnapshot(
            recentItems = snapshot.recentItems.map { storedItem ->
                HomeItemSummary(
                    itemId = storedItem.item.id,
                    name = storedItem.item.name,
                    locationPath = storedItem.locationPath,
                    updatedAt = storedItem.item.updatedAt,
                    thumbnailStorageKey = storedItem.thumbnailStorageKey,
                )
            },
            recentSearches = snapshot.recentSearches.map { search ->
                RecentSearchSummary(
                    historyId = search.id,
                    displayQuery = search.displayQuery,
                )
            },
            favoriteLocations = snapshot.favoriteLocations.map { storedFavorite ->
                FavoriteLocationSummary(
                    favoriteId = storedFavorite.favorite.id,
                    locationNodeId = storedFavorite.location.id,
                    name = storedFavorite.location.name,
                    iconKey = storedFavorite.location.iconKey,
                    itemCount = storedFavorite.itemCount,
                )
            },
            locationUnconfirmedCount = snapshot.locationUnconfirmedCount,
        )
    }

    /**
     * 把数据库层全部物品收成与首页相同的只读摘要。
     */
    override suspend fun loadAllItems(): List<HomeItemSummary> =
        store.loadAllItems().map { storedItem ->
            HomeItemSummary(
                itemId = storedItem.item.id,
                name = storedItem.item.name,
                locationPath = storedItem.locationPath,
                updatedAt = storedItem.item.updatedAt,
                thumbnailStorageKey = storedItem.thumbnailStorageKey,
            )
        }
}
