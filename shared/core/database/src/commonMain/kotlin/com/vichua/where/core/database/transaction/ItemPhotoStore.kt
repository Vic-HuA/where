package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.PhotoAsset

/**
 * 数据库层提供的物品照片管理上下文。
 *
 * @property item 当前未删除物品。
 * @property photos 该物品全部照片，包含软删除记录，便于封面和顺序校验。
 * @property currentDeviceId 当前有效设备，用于记录本次修改来源。
 */
data class StoredItemPhotoContext(
    val item: Item,
    val photos: List<PhotoAsset>,
    val currentDeviceId: DeviceId,
)

/**
 * 加载物品照片并复用写入事务追加或更新照片元数据。
 */
class ItemPhotoStore(
    private val database: WhereDatabase,
) {
    private val writeStore = ItemWriteStore(database)

    /**
     * 读取指定物品和全部照片；物品不存在时失败。
     */
    suspend fun load(itemId: ItemId): StoredItemPhotoContext {
        val item = requireNotNull(database.itemDao().findActiveById(itemId.value)?.toDomain()) {
            "Cannot manage photos of a missing item."
        }
        val currentDevice = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(item.householdId.value),
        ) {
            "Cannot manage photos without an active source device."
        }
        return StoredItemPhotoContext(
            item = item,
            photos = database.photoAssetDao()
                .findAllByItem(item.id.value)
                .map { entity -> entity.toDomain() },
            currentDeviceId = DeviceId(currentDevice.id),
        )
    }

    /**
     * 追加一张已确认用途的正式照片。
     */
    suspend fun add(
        newPhoto: PhotoAsset,
        changeRecord: ChangeRecord,
    ) {
        writeStore.addPhoto(
            newPhoto = newPhoto,
            changeRecord = changeRecord,
        )
    }

    /**
     * 保存完整照片集合中的用途、顺序、封面或软删除变更。
     */
    suspend fun update(
        itemId: ItemId,
        updatedPhotos: List<PhotoAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        writeStore.updatePhotoCollection(
            itemId = itemId,
            updatedPhotos = updatedPhotos,
            changeRecords = changeRecords,
        )
    }
}
