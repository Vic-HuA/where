package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType

/**
 * 数据库层更新位置上下文。
 *
 * @property recentLocationIds 该物品近期到过、且仍可选择的位置。
 * @property favoriteLocationIds 用户固定且仍可选择的常用位置。
 * @property rootLocationId 家庭根节点，页内新建房间时作为父位置。
 */
data class StoredItemMovementContext(
    val detail: com.vichua.where.core.database.query.StoredItemDetail,
    val locations: List<StoredItemCreationLocation>,
    val recentLocationIds: List<LocationNodeId>,
    val favoriteLocationIds: List<LocationNodeId>,
    val rootLocationId: LocationNodeId,
)

/**
 * 聚合更新位置所需查询并复用物品写入事务。
 */
class ItemMovementStore(
    private val database: WhereDatabase,
) {
    private val detailStore = ItemDetailStore(database)
    private val creationStore = ManualItemCreationStore(database)
    private val writeStore = ItemWriteStore(database)

    /** 加载指定物品详情、可选位置、最近位置和常用位置。 */
    suspend fun loadContext(itemId: ItemId): StoredItemMovementContext {
        val detail = requireNotNull(detailStore.find(itemId)) {
            "Cannot move a missing item."
        }
        val locations = creationStore.loadContext().availableLocations
        val availableIds = locations.map { location -> location.locationId }.toSet()
        val currentId = detail.item.currentLocationId
        val rootLocationId = requireNotNull(
            database.locationNodeDao()
                .findActiveTree(detail.item.householdId.value)
                .map { entity -> entity.toDomain() }
                .firstOrNull { location -> location.type == LocationType.HOME }
                ?.id,
        ) {
            "Cannot move an item without a household root location."
        }
        val recentLocationIds = detail.locationHistory
            .sortedByDescending { event -> event.occurredAt }
            .flatMap { event -> listOfNotNull(event.toLocationId, event.fromLocationId) }
            .filter { locationId -> locationId != currentId && locationId in availableIds }
            .distinct()
            .take(MAX_RECENT_MOVE_LOCATIONS)
            .ifEmpty {
                locations
                    .filter { location ->
                        location.type == LocationType.ROOM && location.locationId != currentId
                    }
                    .map { location -> location.locationId }
                    .take(MAX_RECENT_MOVE_LOCATIONS)
            }
        val favoriteLocationIds = database.homeSupportDao()
            .findAllActiveFavoriteLocations(detail.item.householdId.value)
            .map { favorite -> LocationNodeId(favorite.locationNodeId) }
            .filter { locationId -> locationId != currentId && locationId in availableIds }

        return StoredItemMovementContext(
            detail = detail,
            locations = locations,
            recentLocationIds = recentLocationIds,
            favoriteLocationIds = favoriteLocationIds,
            rootLocationId = rootLocationId,
        )
    }

    /** 原子更新物品位置、历史、变更记录和搜索索引。 */
    suspend fun move(
        updatedItem: Item,
        locationEvent: ItemLocationEvent,
        changeRecord: ChangeRecord,
        aliasesText: String,
        categoryText: String,
        locationPathText: String,
    ) {
        writeStore.moveItem(
            updatedItem = updatedItem,
            locationEvent = locationEvent,
            changeRecord = changeRecord,
            searchDocument = ItemSearchDocument(
                itemId = updatedItem.id,
                name = updatedItem.name,
                aliasesText = aliasesText,
                categoryText = categoryText,
                noteText = updatedItem.note.orEmpty(),
                locationPathText = locationPathText,
            ),
        )
    }

    private companion object {
        const val MAX_RECENT_MOVE_LOCATIONS = 4
    }
}
