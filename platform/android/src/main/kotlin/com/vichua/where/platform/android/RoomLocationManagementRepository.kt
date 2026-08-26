package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.LocationManagementStore
import com.vichua.where.core.database.transaction.StoredLocationTreeNode
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.feature.location.management.LocationCreation
import com.vichua.where.feature.location.management.LocationDeletion
import com.vichua.where.feature.location.management.LocationManagementRepository
import com.vichua.where.feature.location.management.LocationRename
import com.vichua.where.feature.location.management.LocationTreeNode
import com.vichua.where.feature.location.management.LocationTreeSnapshot
import com.vichua.where.feature.location.management.allowedChildTypes

/**
 * 使用共享 Room Store 实现 Android 位置管理仓储。
 */
class RoomLocationManagementRepository(
    private val store: LocationManagementStore,
) : LocationManagementRepository {
    /** 加载位置树和统计摘要。 */
    override suspend fun loadTree(): LocationTreeSnapshot {
        val snapshot = store.loadTree()
        return LocationTreeSnapshot(
            householdId = snapshot.householdId,
            sourceDeviceId = snapshot.sourceDeviceId,
            householdName = snapshot.householdName,
            rootLocationId = snapshot.rootLocationId,
            roomCount = snapshot.roomCount,
            itemCount = snapshot.itemCount,
            locationUnconfirmedCount = snapshot.locationUnconfirmedCount,
            locations = snapshot.locations,
            favoriteLocations = snapshot.favoriteLocations,
            nodes = snapshot.nodes.map(::toTreeNode),
        )
    }

    /** 保存新增位置聚合。 */
    override suspend fun create(creation: LocationCreation) {
        store.create(
            location = creation.location,
            changeRecord = creation.changeRecord,
        )
    }

    /** 保存重命名聚合。 */
    override suspend fun rename(rename: LocationRename) {
        store.rename(
            location = rename.location,
            changeRecord = rename.changeRecord,
        )
    }

    /** 保存叶子位置删除聚合，并同步待确认物品。 */
    override suspend fun delete(deletion: LocationDeletion) {
        store.delete(
            location = deletion.location,
            favoriteLocation = deletion.favoriteLocation,
            changeRecords = deletion.changeRecords,
            displacedItems = deletion.displacedItems,
            locationEvents = deletion.locationEvents,
        )
    }

    /** 加载直接放在指定位置上的未删除物品。 */
    override suspend fun findActiveItemsAt(locationId: LocationNodeId): List<Item> =
        store.findActiveItemsAt(locationId)

    /**
     * 将数据库节点映射为页面展示模型，并补齐可操作状态。
     */
    private fun toTreeNode(node: StoredLocationTreeNode): LocationTreeNode {
        val location = node.location
        return LocationTreeNode(
            locationId = location.id,
            parentId = location.parentId,
            type = location.type,
            name = location.name,
            iconKey = location.iconKey,
            displayPath = node.displayPath,
            depth = node.depth,
            childCount = node.childCount,
            itemCount = node.itemCount,
            canRename = !location.isHouseholdRoot,
            canDelete = !location.isHouseholdRoot && node.childCount == 0,
            allowedChildTypes = allowedChildTypes(location.type),
            coverThumbnailStorageKey = node.coverThumbnailStorageKey,
            hasVoiceLabel = node.hasVoiceLabel,
            voiceLabelStorageKey = node.voiceLabelStorageKey,
        )
    }
}
