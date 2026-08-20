package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DomainValidators
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.PhotoAsset

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
