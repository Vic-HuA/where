package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdBackupSnapshot
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationPhotoAsset

/**
 * 把确认后的家庭快照原子写入正式库。
 *
 * 事务内不写媒体文件；调用方必须先准备回滚副本，失败时整体回滚数据库并还原原图。
 */
class HouseholdRestoreStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 用目标快照覆盖家庭实体，并保留当前设备记录。
     *
     * @param snapshot 已经过冲突处理的目标快照。
     * @param currentDeviceId 本机设备，恢复后必须仍然存在。
     */
    suspend fun replaceSnapshot(
        snapshot: HouseholdBackupSnapshot,
        currentDeviceId: DeviceId,
    ) {
        val previousHouseholdId = database.householdDao().findFirstActive()?.id
            ?: error("Active household is required before restoring a backup.")
        transactionRunner.write {
            applySnapshot(
                snapshot = snapshot,
                currentDeviceId = currentDeviceId,
                previousHouseholdId = previousHouseholdId,
            )
        }
    }

    /**
     * 读取当前设备在指定家庭下的草稿，供恢复后清理失效位置引用。
     */
    suspend fun loadDrafts(
        deviceId: DeviceId,
        householdId: String,
    ): List<ItemDraft> = database.itemDraftDao()
        .findAllByDeviceAndHousehold(deviceId.value, householdId)
        .map { entity -> entity.toDomain() }

    /**
     * 写回校验后的草稿，不进入变更记录。
     */
    suspend fun saveDraft(draft: ItemDraft) {
        database.itemDraftDao().upsert(draft.toEntity())
    }

    /**
     * 删除家庭可导出实体并写入目标快照，同时重建搜索索引。
     */
    private suspend fun WhereDatabase.applySnapshot(
        snapshot: HouseholdBackupSnapshot,
        currentDeviceId: DeviceId,
        previousHouseholdId: String,
    ) {
        require(deviceDao().findById(currentDeviceId.value) != null) {
            "Current device is required before restoring a backup."
        }
        val targetHouseholdId = snapshot.household.id.value

        itemLocationEventDao().deleteByHousehold(previousHouseholdId)
        locationPhotoAssetDao().deleteByHousehold(previousHouseholdId)
        photoAssetDao().deleteByHousehold(previousHouseholdId)
        itemAliasDao().deleteByHousehold(previousHouseholdId)
        itemDao().findAllByHousehold(previousHouseholdId).forEach { item ->
            itemSearchDao().deleteByItemId(item.id)
        }
        itemDao().deleteByHousehold(previousHouseholdId)
        homeSupportDao().deleteFavoriteLocationsByHousehold(previousHouseholdId)
        changeRecordDao().deleteByHousehold(previousHouseholdId)
        categoryDao().deleteByHousehold(previousHouseholdId)
        locationNodeDao().deleteByHousehold(previousHouseholdId)
        deviceDao().deleteByHouseholdExcept(previousHouseholdId, currentDeviceId.value)

        if (previousHouseholdId == targetHouseholdId) {
            householdDao().update(snapshot.household.toEntity())
        } else {
            if (householdDao().findById(targetHouseholdId) == null) {
                householdDao().insert(snapshot.household.toEntity())
            } else {
                householdDao().update(snapshot.household.toEntity())
            }
            val currentDevice = requireNotNull(deviceDao().findById(currentDeviceId.value)) {
                "Current device disappeared during restore."
            }
            deviceDao().update(currentDevice.copy(householdId = targetHouseholdId))
            householdDao().findById(previousHouseholdId)?.let { previous ->
                householdDao().update(
                    previous.copy(deletedAt = snapshot.household.updatedAt.epochMilliseconds),
                )
            }
        }

        snapshot.devices
            .filter { device -> device.id != currentDeviceId }
            .forEach { device ->
                if (deviceDao().findById(device.id.value) == null) {
                    deviceDao().insert(device.toEntity())
                }
            }
        if (snapshot.locations.isNotEmpty()) {
            locationNodeDao().insertAll(snapshot.locations.map(LocationNode::toEntity))
        }
        snapshot.categories.forEach { category ->
            categoryDao().insert(category.toEntity())
        }
        snapshot.items.forEach { item ->
            itemDao().insert(item.toEntity())
        }
        if (snapshot.aliases.isNotEmpty()) {
            itemAliasDao().insertAll(snapshot.aliases.map { alias -> alias.toEntity() })
        }
        if (snapshot.photos.isNotEmpty()) {
            photoAssetDao().insertAll(snapshot.photos.map { photo -> photo.toEntity() })
        }
        if (snapshot.locationPhotos.isNotEmpty()) {
            locationPhotoAssetDao().insertAll(
                snapshot.locationPhotos.map(LocationPhotoAsset::toEntity),
            )
        }
        snapshot.locationEvents.forEach { event ->
            itemLocationEventDao().insert(event.toEntity())
        }
        if (snapshot.favoriteLocations.isNotEmpty()) {
            homeSupportDao().insertFavoriteLocations(
                snapshot.favoriteLocations.map { favorite -> favorite.toEntity() },
            )
        }
        if (snapshot.changeRecords.isNotEmpty()) {
            changeRecordDao().insertAll(snapshot.changeRecords.map { record -> record.toEntity() })
        }
        rebuildSearchIndex(snapshot)
    }

    /**
     * 按恢复后的正式物品重建全文索引，避免搜索仍指向覆盖前的路径。
     */
    private suspend fun WhereDatabase.rebuildSearchIndex(snapshot: HouseholdBackupSnapshot) {
        itemSearchDao().clear()
        val locationsById = snapshot.locations.associateBy(LocationNode::id)
        val categoriesById = snapshot.categories.associateBy { category -> category.id }
        snapshot.items.filter { item -> !item.isDeleted }.forEach { item ->
            val aliasesText = snapshot.aliases
                .filter { alias -> alias.itemId == item.id && alias.deletedAt == null }
                .joinToString(" ") { alias -> alias.alias }
            itemSearchDao().insert(
                ItemSearchDocument(
                    itemId = item.id,
                    name = item.name,
                    aliasesText = aliasesText,
                    categoryText = item.categoryId?.let { id -> categoriesById[id]?.name }.orEmpty(),
                    noteText = item.note.orEmpty(),
                    locationPathText = buildLocationPath(item.currentLocationId, locationsById),
                ).toEntity(),
            )
        }
    }

    private fun buildLocationPath(
        locationId: LocationNodeId,
        locationsById: Map<LocationNodeId, LocationNode>,
    ): String {
        val pathNames = mutableListOf<String>()
        val visitedLocationIds = mutableSetOf<LocationNodeId>()
        var currentLocation = locationsById[locationId]
        while (currentLocation != null && visitedLocationIds.add(currentLocation.id)) {
            pathNames += currentLocation.name
            currentLocation = currentLocation.parentId?.let(locationsById::get)
        }
        return pathNames.asReversed().joinToString(PATH_SEPARATOR).ifBlank { UNKNOWN_LOCATION_TEXT }
    }

    private companion object {
        const val PATH_SEPARATOR = " · "
        const val UNKNOWN_LOCATION_TEXT = "位置待确认"
    }
}
