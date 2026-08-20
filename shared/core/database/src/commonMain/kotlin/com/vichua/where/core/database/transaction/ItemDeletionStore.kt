package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.UtcTimestamp

/**
 * 数据库层提供的物品删除与短时撤销上下文。
 *
 * 这里用 `findById` 读取物品，是为了在软删除后仍能按批次时间恢复；
 * 位置历史不在此改写，物品恢复后历史会重新对正常界面可见。
 *
 * @property item 当前物品，可能已经软删除。
 * @property aliases 该物品全部别名，包含更早软删除的记录。
 * @property photos 该物品全部照片，包含更早软删除的记录。
 * @property locationPath 当前位置路径，用于撤销后重建搜索索引。
 * @property aliasesText 当前未删除别名拼接文本。
 * @property categoryText 当前分类名称。
 * @property currentDeviceId 当前有效设备，用于记录本次修改来源。
 */
data class StoredItemDeletionContext(
    val item: Item,
    val aliases: List<ItemAlias>,
    val photos: List<PhotoAsset>,
    val locationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val currentDeviceId: DeviceId,
)

/**
 * 加载物品删除上下文，并复用写入事务完成级联软删除或短时撤销。
 */
class ItemDeletionStore(
    private val database: WhereDatabase,
) {
    private val writeStore = ItemWriteStore(database)

    /**
     * 读取指定物品及其全部别名、照片和当前设备；物品不存在时失败。
     */
    suspend fun load(itemId: ItemId): StoredItemDeletionContext {
        val item = requireNotNull(database.itemDao().findById(itemId.value)?.toDomain()) {
            "Cannot delete or restore a missing item."
        }
        val currentDevice = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(item.householdId.value),
        ) {
            "Cannot delete or restore an item without an active source device."
        }
        val locations = database.locationNodeDao()
            .findActiveTree(item.householdId.value)
            .map { entity -> entity.toDomain() }
        val aliases = database.itemAliasDao()
            .findAllByItem(item.id.value)
            .map { entity -> entity.toDomain() }
        val categoryText = item.categoryId?.let { categoryId ->
            database.categoryDao().findActiveById(categoryId.value)?.name
        }.orEmpty()
        return StoredItemDeletionContext(
            item = item,
            aliases = aliases,
            photos = database.photoAssetDao()
                .findAllByItem(item.id.value)
                .map { entity -> entity.toDomain() },
            locationPath = buildLocationPath(
                locationId = item.currentLocationId,
                locationsById = locations.associateBy(LocationNode::id),
            ),
            aliasesText = aliases
                .filter { alias -> alias.deletedAt == null }
                .joinToString(" ") { alias -> alias.alias },
            categoryText = categoryText,
            currentDeviceId = DeviceId(currentDevice.id),
        )
    }

    /**
     * 原子软删除物品、本次级联的别名和照片，并移除全文索引。
     */
    suspend fun delete(
        deletedItem: Item,
        deletedAliases: List<ItemAlias>,
        deletedPhotos: List<PhotoAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        writeStore.deleteItem(
            deletedItem = deletedItem,
            deletedAliases = deletedAliases,
            deletedPhotos = deletedPhotos,
            changeRecords = changeRecords,
        )
    }

    /**
     * 原子撤销本次级联软删除，并按恢复后的可见字段重建全文索引。
     */
    suspend fun restore(
        restoredItem: Item,
        restoredAliases: List<ItemAlias>,
        restoredPhotos: List<PhotoAsset>,
        changeRecords: List<ChangeRecord>,
        aliasesText: String,
        categoryText: String,
        locationPathText: String,
        expectedDeletedAt: UtcTimestamp,
        expectedDeletedVersion: EntityVersion,
    ) {
        writeStore.restoreItem(
            restoredItem = restoredItem,
            restoredAliases = restoredAliases,
            restoredPhotos = restoredPhotos,
            changeRecords = changeRecords,
            searchDocument = ItemSearchDocument(
                itemId = restoredItem.id,
                name = restoredItem.name,
                aliasesText = aliasesText,
                categoryText = categoryText,
                noteText = restoredItem.note.orEmpty(),
                locationPathText = locationPathText,
            ),
            expectedDeletedAt = expectedDeletedAt,
            expectedDeletedVersion = expectedDeletedVersion,
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
    }
}
