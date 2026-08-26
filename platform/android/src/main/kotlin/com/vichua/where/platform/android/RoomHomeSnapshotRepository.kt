package com.vichua.where.platform.android

import com.vichua.where.core.database.query.HomeSnapshotStore
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.feature.search.home.FavoriteLocationSummary
import com.vichua.where.feature.search.home.HomeItemSummary
import com.vichua.where.feature.search.home.HomeSnapshot
import com.vichua.where.feature.search.home.HomeSnapshotRepository
import com.vichua.where.feature.search.home.PinnedItemSummary
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
                    coverThumbnailStorageKey = storedFavorite.coverThumbnailStorageKey,
                )
            },
            pinnedItems = snapshot.pinnedItems.map { storedPinned ->
                PinnedItemSummary(
                    pinnedId = storedPinned.pinned.id,
                    itemId = storedPinned.item.id,
                    name = storedPinned.item.name,
                    locationPath = storedPinned.locationPath,
                    thumbnailStorageKey = storedPinned.thumbnailStorageKey,
                    locationUnconfirmed = storedPinned.locationUnconfirmed,
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

    /**
     * 把指定位置树下的物品收成与首页相同的只读摘要。
     */
    override suspend fun loadItemsAtLocation(locationId: LocationNodeId): List<HomeItemSummary> =
        store.loadItemsAtLocation(locationId).map { storedItem ->
            HomeItemSummary(
                itemId = storedItem.item.id,
                name = storedItem.item.name,
                locationPath = storedItem.locationPath,
                updatedAt = storedItem.item.updatedAt,
                thumbnailStorageKey = storedItem.thumbnailStorageKey,
            )
        }

    /**
     * 把位置待确认物品收成与首页相同的只读摘要。
     */
    override suspend fun loadLocationUnconfirmedItems(): List<HomeItemSummary> =
        store.loadLocationUnconfirmedItems().map { storedItem ->
            HomeItemSummary(
                itemId = storedItem.item.id,
                name = storedItem.item.name,
                locationPath = storedItem.locationPath,
                updatedAt = storedItem.item.updatedAt,
                thumbnailStorageKey = storedItem.thumbnailStorageKey,
            )
        }
}
