package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DomainValidators
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.UtcTimestamp

/**
 * 编排物品正式写入及其关联索引、历史和变更记录。
 *
 * 文件临时写入与事务后转正由上层文件存储组件负责，本类只处理必须原子提交的数据库状态。
 */
class ItemWriteStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 原子创建物品、别名、照片、首次位置事件、变更记录和搜索索引。
     *
     * @param item 已通过单实体约束的新物品。
     * @param aliases 物品初始别名。
     * @param photos 物品初始照片；非空时必须只有一个封面。
     * @param initialLocationEvent 原因必须为 `CREATED` 的首次位置事件。
     * @param changeRecord 对应该物品 `CREATE` 操作的变更记录。
     * @param searchDocument 根据已确认正式数据生成的搜索文档。
     */
    suspend fun createItem(
        item: Item,
        aliases: List<ItemAlias>,
        photos: List<PhotoAsset>,
        initialLocationEvent: ItemLocationEvent,
        changeRecord: ChangeRecord,
        searchDocument: ItemSearchDocument,
    ) {
        require(item.deletedAt == null) { "New item must not be soft-deleted." }
        DomainValidators.validateItemAliases(item.id, aliases)
        DomainValidators.validatePhotoCollection(item, photos)
        validateInitialLocationEvent(item, initialLocationEvent)
        validateItemChangeRecord(item, changeRecord, ChangeOperation.CREATE)
        require(searchDocument.itemId == item.id) {
            "Search document must belong to the created item."
        }

        transactionRunner.write {
            val currentLocation = locationNodeDao()
                .findById(item.currentLocationId.value)
                ?.toDomain()
            require(currentLocation != null) {
                "Created item current location does not exist."
            }
            DomainValidators.validateItemLocation(item, listOf(currentLocation))

            itemDao().insert(item.toEntity())
            if (aliases.isNotEmpty()) {
                itemAliasDao().insertAll(aliases.map(ItemAlias::toEntity))
            }
            if (photos.isNotEmpty()) {
                photoAssetDao().insertAll(photos.map(PhotoAsset::toEntity))
            }
            itemLocationEventDao().insert(initialLocationEvent.toEntity())
            changeRecordDao().insert(changeRecord.toEntity())
            itemSearchDao().insert(searchDocument.toEntity())
            // 正式物品与草稿清理必须同事务，避免保存成功后仍恢复旧输入。
            itemDraftDao().deleteByDeviceAndHousehold(
                deviceId = item.sourceDeviceId.value,
                householdId = item.householdId.value,
            )
        }
    }

    /**
     * 原子更新物品当前位置、位置历史、变更记录和全文索引。
     *
     * @param updatedItem 已包含新位置和递增版本的物品。
     * @param locationEvent 描述原位置与新位置的位置历史事件。
     * @param changeRecord 对应该物品 `UPDATE` 操作的变更记录。
     * @param searchDocument 根据新位置路径重新生成的搜索文档。
     */
    suspend fun moveItem(
        updatedItem: Item,
        locationEvent: ItemLocationEvent,
        changeRecord: ChangeRecord,
        searchDocument: ItemSearchDocument,
    ) {
        require(updatedItem.deletedAt == null) {
            "Soft-deleted item cannot be moved."
        }
        validateMovedLocationEvent(updatedItem, locationEvent)
        validateItemChangeRecord(updatedItem, changeRecord, ChangeOperation.UPDATE)
        require(searchDocument.itemId == updatedItem.id) {
            "Search document must belong to the moved item."
        }

        transactionRunner.write {
            val currentEntity = itemDao().findActiveById(updatedItem.id.value)
            require(currentEntity != null) { "Moved item does not exist." }
            val currentItem = currentEntity.toDomain()
            require(updatedItem.version == currentItem.version.next()) {
                "Moved item version must increment the stored version by one."
            }
            require(locationEvent.fromLocationId == currentItem.currentLocationId) {
                "Location event source must match the stored current location."
            }

            val targetLocation = locationNodeDao()
                .findById(updatedItem.currentLocationId.value)
                ?.toDomain()
            require(targetLocation != null) { "Target location does not exist." }
            DomainValidators.validateItemLocation(updatedItem, listOf(targetLocation))

            require(itemDao().update(updatedItem.toEntity()) == 1) {
                "Moved item update must affect exactly one row."
            }
            itemLocationEventDao().insert(locationEvent.toEntity())
            changeRecordDao().insert(changeRecord.toEntity())
            itemSearchDao().deleteByItemId(updatedItem.id.value)
            itemSearchDao().insert(searchDocument.toEntity())
        }
    }

    /**
     * 原子更新物品名称、位置说明和备注，并同步变更记录与全文索引。
     *
     * 不改当前位置，避免把档案编辑和移动位置混在同一事务里。
     */
    suspend fun updateProfile(
        updatedItem: Item,
        changeRecord: ChangeRecord,
        searchDocument: ItemSearchDocument,
    ) {
        require(updatedItem.deletedAt == null) {
            "Soft-deleted item cannot be updated."
        }
        validateItemChangeRecord(updatedItem, changeRecord, ChangeOperation.UPDATE)
        require(searchDocument.itemId == updatedItem.id) {
            "Search document must belong to the updated item."
        }
        require(searchDocument.name == updatedItem.name) {
            "Search document name must match the updated item."
        }
        require(searchDocument.noteText == updatedItem.note.orEmpty()) {
            "Search document note must match the updated item."
        }

        transactionRunner.write {
            val currentEntity = itemDao().findActiveById(updatedItem.id.value)
            require(currentEntity != null) { "Updated item does not exist." }
            val currentItem = currentEntity.toDomain()
            require(updatedItem.version == currentItem.version.next()) {
                "Updated item version must increment the stored version by one."
            }
            require(updatedItem.currentLocationId == currentItem.currentLocationId) {
                "Profile update must not change the current location."
            }

            val currentLocation = locationNodeDao()
                .findById(updatedItem.currentLocationId.value)
                ?.toDomain()
            require(currentLocation != null) { "Updated item current location does not exist." }
            DomainValidators.validateItemLocation(updatedItem, listOf(currentLocation))

            require(itemDao().update(updatedItem.toEntity()) == 1) {
                "Item profile update must affect exactly one row."
            }
            changeRecordDao().insert(changeRecord.toEntity())
            itemSearchDao().deleteByItemId(updatedItem.id.value)
            itemSearchDao().insert(searchDocument.toEntity())
        }
    }

    /**
     * 原子追加一张未删除照片及其变更记录，不改物品当前位置。
     *
     * 已有封面时新照片不得抢封面；没有照片时新照片必须是封面。
     */
    suspend fun addPhoto(
        newPhoto: PhotoAsset,
        changeRecord: ChangeRecord,
    ) {
        require(newPhoto.deletedAt == null) { "Added photo must not be soft-deleted." }
        validatePhotoChangeRecord(newPhoto, changeRecord, ChangeOperation.CREATE)

        transactionRunner.write {
            val currentItem = itemDao().findActiveById(newPhoto.itemId.value)?.toDomain()
            require(currentItem != null) { "Photo item does not exist." }
            require(newPhoto.householdId == currentItem.householdId) {
                "Added photo must belong to the item household."
            }
            val storedPhotos = photoAssetDao()
                .findAllByItem(currentItem.id.value)
                .map { entity -> entity.toDomain() }
            require(storedPhotos.none { photo -> photo.id == newPhoto.id }) {
                "Added photo ID must be unique for the item."
            }
            val activePhotos = storedPhotos.filter { photo -> photo.deletedAt == null }
            if (activePhotos.isEmpty()) {
                require(newPhoto.isCover) { "First active photo must be the cover." }
            } else {
                require(!newPhoto.isCover) {
                    "Added photo must not replace the existing cover."
                }
            }
            DomainValidators.validatePhotoCollection(currentItem, storedPhotos + newPhoto)
            photoAssetDao().insertAll(listOf(newPhoto.toEntity()))
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    /**
     * 原子更新已有照片的用途、顺序、封面或软删除状态。
     *
     * 调用方必须提交该物品的完整照片集合，避免封面和顺序在事务外漂移。
     */
    suspend fun updatePhotoCollection(
        itemId: ItemId,
        updatedPhotos: List<PhotoAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        require(changeRecords.isNotEmpty()) {
            "Photo collection update must include change records."
        }

        transactionRunner.write {
            val currentItem = itemDao().findActiveById(itemId.value)?.toDomain()
            require(currentItem != null) { "Photo item does not exist." }
            val storedPhotos = photoAssetDao()
                .findAllByItem(itemId.value)
                .map { entity -> entity.toDomain() }
            require(
                storedPhotos.map { photo -> photo.id }.toSet() ==
                    updatedPhotos.map { photo -> photo.id }.toSet(),
            ) {
                "Photo collection update must include every stored photo."
            }
            DomainValidators.validatePhotoCollection(currentItem, updatedPhotos)

            val storedPhotosById = storedPhotos.associateBy { photo -> photo.id }
            val changedPhotos = updatedPhotos.filter { updatedPhoto ->
                val storedPhoto = storedPhotosById.getValue(updatedPhoto.id)
                updatedPhoto.role != storedPhoto.role ||
                    updatedPhoto.sortOrder != storedPhoto.sortOrder ||
                    updatedPhoto.isCover != storedPhoto.isCover ||
                    updatedPhoto.deletedAt != storedPhoto.deletedAt
            }
            require(changedPhotos.isNotEmpty()) {
                "Photo collection update must change at least one photo."
            }
            require(changeRecords.size == changedPhotos.size) {
                "Photo change records must match changed photos."
            }

            updatedPhotos.forEach { updatedPhoto ->
                val storedPhoto = storedPhotosById.getValue(updatedPhoto.id)
                require(updatedPhoto.itemId == storedPhoto.itemId) {
                    "Photo update must not move a photo to another item."
                }
                require(updatedPhoto.storageKey == storedPhoto.storageKey) {
                    "Photo update must not change the original storage key."
                }
                require(updatedPhoto.thumbnailStorageKey == storedPhoto.thumbnailStorageKey) {
                    "Photo update must not change the thumbnail storage key."
                }
                if (changedPhotos.any { photo -> photo.id == updatedPhoto.id }) {
                    require(updatedPhoto.version == storedPhoto.version.next()) {
                        "Updated photo version must increment the stored version by one."
                    }
                    require(photoAssetDao().update(updatedPhoto.toEntity()) == 1) {
                        "Photo update must affect exactly one row."
                    }
                } else {
                    require(updatedPhoto.version == storedPhoto.version) {
                        "Unchanged photo version must stay the same."
                    }
                }
            }

            changedPhotos.forEach { updatedPhoto ->
                val storedPhoto = storedPhotosById.getValue(updatedPhoto.id)
                val operation = if (storedPhoto.deletedAt == null && updatedPhoto.deletedAt != null) {
                    ChangeOperation.DELETE
                } else {
                    ChangeOperation.UPDATE
                }
                val changeRecord = requireNotNull(
                    changeRecords.singleOrNull { record ->
                        record.entityId == updatedPhoto.id.value
                    },
                ) {
                    "Each changed photo must have exactly one change record."
                }
                validatePhotoChangeRecord(updatedPhoto, changeRecord, operation)
            }
            changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
        }
    }

    /**
     * 原子软删除物品、本次级联的别名和照片，并移除全文索引。
     *
     * 位置历史不改写，因为物品离开正常界面后历史不会再被查询；文件在撤销窗口结束前不得物理删除。
     */
    suspend fun deleteItem(
        deletedItem: Item,
        deletedAliases: List<ItemAlias>,
        deletedPhotos: List<PhotoAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        require(deletedItem.deletedAt != null) { "Deleted item must be soft-deleted." }
        require(changeRecords.isNotEmpty()) { "Item deletion must include change records." }
        validateItemChangeRecord(deletedItem, changeRecords.first(), ChangeOperation.DELETE)
        require(
            changeRecords.first().entityType == ChangeEntityType.ITEM &&
                changeRecords.first().entityId == deletedItem.id.value,
        ) {
            "First deletion change record must belong to the item."
        }
        DomainValidators.validateItemAliases(deletedItem.id, deletedAliases)
        DomainValidators.validatePhotoCollection(deletedItem, deletedPhotos)

        transactionRunner.write {
            val currentEntity = itemDao().findActiveById(deletedItem.id.value)
            require(currentEntity != null) { "Deleted item does not exist." }
            val currentItem = currentEntity.toDomain()
            require(deletedItem.version == currentItem.version.next()) {
                "Deleted item version must increment the stored version by one."
            }
            require(deletedItem.currentLocationId == currentItem.currentLocationId) {
                "Item deletion must not change the current location."
            }
            require(deletedItem.deletedAt != null) { "Deleted item must have a deletion timestamp." }

            val storedAliases = itemAliasDao()
                .findAllByItem(deletedItem.id.value)
                .map { entity -> entity.toDomain() }
            val storedPhotos = photoAssetDao()
                .findAllByItem(deletedItem.id.value)
                .map { entity -> entity.toDomain() }
            require(storedAliases.map { alias -> alias.id }.toSet() == deletedAliases.map { alias -> alias.id }.toSet()) {
                "Item deletion must include every stored alias."
            }
            require(storedPhotos.map { photo -> photo.id }.toSet() == deletedPhotos.map { photo -> photo.id }.toSet()) {
                "Item deletion must include every stored photo."
            }

            val batchDeletedAt = deletedItem.deletedAt
            storedAliases.forEach { storedAlias ->
                val updatedAlias = deletedAliases.single { alias -> alias.id == storedAlias.id }
                if (storedAlias.deletedAt == null) {
                    require(updatedAlias.deletedAt == batchDeletedAt) {
                        "Newly deleted alias must share the item deletion timestamp."
                    }
                    require(itemAliasDao().update(updatedAlias.toEntity()) == 1) {
                        "Alias deletion must affect exactly one row."
                    }
                } else {
                    require(updatedAlias.deletedAt == storedAlias.deletedAt) {
                        "Previously deleted alias must stay unchanged."
                    }
                }
            }
            storedPhotos.forEach { storedPhoto ->
                val updatedPhoto = deletedPhotos.single { photo -> photo.id == storedPhoto.id }
                if (storedPhoto.deletedAt == null) {
                    require(updatedPhoto.deletedAt == batchDeletedAt) {
                        "Newly deleted photo must share the item deletion timestamp."
                    }
                    require(updatedPhoto.version == storedPhoto.version.next()) {
                        "Deleted photo version must increment the stored version by one."
                    }
                    require(photoAssetDao().update(updatedPhoto.toEntity()) == 1) {
                        "Photo deletion must affect exactly one row."
                    }
                } else {
                    require(updatedPhoto.deletedAt == storedPhoto.deletedAt) {
                        "Previously deleted photo must stay unchanged."
                    }
                    require(updatedPhoto.version == storedPhoto.version) {
                        "Previously deleted photo version must stay the same."
                    }
                }
            }

            require(itemDao().update(deletedItem.toEntity()) == 1) {
                "Item deletion must affect exactly one row."
            }
            itemSearchDao().deleteByItemId(deletedItem.id.value)
            changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
        }
    }

    /**
     * 原子撤销本次级联软删除，并重建全文索引。
     *
     * 只恢复与删除批次 `deletedAt` 相同的别名和照片，避免把更早删除的记录带回来。
     */
    suspend fun restoreItem(
        restoredItem: Item,
        restoredAliases: List<ItemAlias>,
        restoredPhotos: List<PhotoAsset>,
        changeRecords: List<ChangeRecord>,
        searchDocument: ItemSearchDocument,
        expectedDeletedAt: UtcTimestamp,
        expectedDeletedVersion: EntityVersion,
    ) {
        require(restoredItem.deletedAt == null) { "Restored item must be active." }
        require(searchDocument.itemId == restoredItem.id) {
            "Search document must belong to the restored item."
        }
        require(searchDocument.name == restoredItem.name) {
            "Search document name must match the restored item."
        }
        require(changeRecords.isNotEmpty()) { "Item restore must include change records." }
        validateItemChangeRecord(restoredItem, changeRecords.first(), ChangeOperation.RESTORE)

        transactionRunner.write {
            val currentEntity = itemDao().findById(restoredItem.id.value)
            require(currentEntity != null) { "Restored item does not exist." }
            val currentItem = currentEntity.toDomain()
            require(currentItem.deletedAt == expectedDeletedAt) {
                "Item restore must target the current deletion batch."
            }
            require(currentItem.version == expectedDeletedVersion) {
                "Item restore must target the unmodified deleted version."
            }
            require(restoredItem.version == currentItem.version.next()) {
                "Restored item version must increment the stored version by one."
            }
            require(restoredItem.currentLocationId == currentItem.currentLocationId) {
                "Item restore must not change the current location."
            }

            val storedAliases = itemAliasDao()
                .findAllByItem(restoredItem.id.value)
                .map { entity -> entity.toDomain() }
            val storedPhotos = photoAssetDao()
                .findAllByItem(restoredItem.id.value)
                .map { entity -> entity.toDomain() }
            require(storedAliases.map { alias -> alias.id }.toSet() == restoredAliases.map { alias -> alias.id }.toSet()) {
                "Item restore must include every stored alias."
            }
            require(storedPhotos.map { photo -> photo.id }.toSet() == restoredPhotos.map { photo -> photo.id }.toSet()) {
                "Item restore must include every stored photo."
            }
            DomainValidators.validateItemAliases(restoredItem.id, restoredAliases)
            DomainValidators.validatePhotoCollection(restoredItem, restoredPhotos)

            storedAliases.forEach { storedAlias ->
                val updatedAlias = restoredAliases.single { alias -> alias.id == storedAlias.id }
                if (storedAlias.deletedAt == expectedDeletedAt) {
                    require(updatedAlias.deletedAt == null) {
                        "Batch-deleted alias must be restored."
                    }
                    require(itemAliasDao().update(updatedAlias.toEntity()) == 1) {
                        "Alias restore must affect exactly one row."
                    }
                } else {
                    require(updatedAlias.deletedAt == storedAlias.deletedAt) {
                        "Older alias deletion must stay unchanged."
                    }
                }
            }
            storedPhotos.forEach { storedPhoto ->
                val updatedPhoto = restoredPhotos.single { photo -> photo.id == storedPhoto.id }
                if (storedPhoto.deletedAt == expectedDeletedAt) {
                    require(updatedPhoto.deletedAt == null) {
                        "Batch-deleted photo must be restored."
                    }
                    require(updatedPhoto.version == storedPhoto.version.next()) {
                        "Restored photo version must increment the stored version by one."
                    }
                    require(photoAssetDao().update(updatedPhoto.toEntity()) == 1) {
                        "Photo restore must affect exactly one row."
                    }
                } else {
                    require(updatedPhoto.deletedAt == storedPhoto.deletedAt) {
                        "Older photo deletion must stay unchanged."
                    }
                    require(updatedPhoto.version == storedPhoto.version) {
                        "Unchanged photo version must stay the same."
                    }
                }
            }

            require(itemDao().update(restoredItem.toEntity()) == 1) {
                "Item restore must affect exactly one row."
            }
            itemSearchDao().deleteByItemId(restoredItem.id.value)
            itemSearchDao().insert(searchDocument.toEntity())
            changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
        }
    }

    /**
     * 校验首次位置事件与新物品的一致性。
     */
    private fun validateInitialLocationEvent(
        item: Item,
        locationEvent: ItemLocationEvent,
    ) {
        require(locationEvent.householdId == item.householdId) {
            "Initial location event must belong to the item household."
        }
        require(locationEvent.itemId == item.id) {
            "Initial location event must belong to the created item."
        }
        require(locationEvent.reason == ItemLocationReason.CREATED) {
            "Initial location event reason must be CREATED."
        }
        require(locationEvent.fromLocationId == null) {
            "Initial location event must not have a source location."
        }
        require(locationEvent.toLocationId == item.currentLocationId) {
            "Initial location event target must match the item current location."
        }
    }

    /**
     * 校验移动位置事件与更新后物品的一致性。
     */
    private fun validateMovedLocationEvent(
        item: Item,
        locationEvent: ItemLocationEvent,
    ) {
        require(locationEvent.householdId == item.householdId) {
            "Moved location event must belong to the item household."
        }
        require(locationEvent.itemId == item.id) {
            "Moved location event must belong to the moved item."
        }
        require(locationEvent.reason != ItemLocationReason.CREATED) {
            "Moved location event must not use CREATED reason."
        }
        require(locationEvent.toLocationId == item.currentLocationId) {
            "Moved location event target must match the item current location."
        }
    }

    /**
     * 校验物品变更记录与正式物品版本的一致性。
     */
    private fun validateItemChangeRecord(
        item: Item,
        changeRecord: ChangeRecord,
        expectedOperation: ChangeOperation,
    ) {
        require(changeRecord.householdId == item.householdId) {
            "Item change record must belong to the item household."
        }
        require(changeRecord.entityType == ChangeEntityType.ITEM) {
            "Item change record entity type must be ITEM."
        }
        require(changeRecord.entityId == item.id.value) {
            "Item change record entity ID must match the item."
        }
        require(changeRecord.operation == expectedOperation) {
            "Item change record operation is invalid."
        }
        require(changeRecord.entityVersion == item.version) {
            "Item change record version must match the item version."
        }
    }

    /**
     * 校验照片变更记录与照片版本的一致性。
     */
    private fun validatePhotoChangeRecord(
        photo: PhotoAsset,
        changeRecord: ChangeRecord,
        expectedOperation: ChangeOperation,
    ) {
        require(changeRecord.householdId == photo.householdId) {
            "Photo change record must belong to the photo household."
        }
        require(changeRecord.entityType == ChangeEntityType.PHOTO_ASSET) {
            "Photo change record entity type must be PHOTO_ASSET."
        }
        require(changeRecord.entityId == photo.id.value) {
            "Photo change record entity ID must match the photo."
        }
        require(changeRecord.operation == expectedOperation) {
            "Photo change record operation is invalid."
        }
        require(changeRecord.entityVersion == photo.version) {
            "Photo change record version must match the photo version."
        }
    }
}
