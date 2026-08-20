package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.ItemPhotoStore
import com.vichua.where.core.model.ItemId
import com.vichua.where.feature.item.photo.ItemPhotoAddition
import com.vichua.where.feature.item.photo.ItemPhotoCollectionUpdate
import com.vichua.where.feature.item.photo.ItemPhotoContext
import com.vichua.where.feature.item.photo.ItemPhotoRepository

/**
 * 使用共享 Room Store 实现 Android 物品照片管理仓储。
 */
class RoomItemPhotoRepository(
    private val store: ItemPhotoStore,
) : ItemPhotoRepository {
    /** 加载物品和全部照片。 */
    override suspend fun load(itemId: ItemId): ItemPhotoContext {
        val context = store.load(itemId)
        return ItemPhotoContext(
            item = context.item,
            photos = context.photos,
            currentDeviceId = context.currentDeviceId,
        )
    }

    /** 把已校验新照片交给共享数据库事务保存。 */
    override suspend fun add(addition: ItemPhotoAddition) {
        store.add(
            newPhoto = addition.newPhoto,
            changeRecord = addition.changeRecord,
        )
    }

    /** 把已校验照片集合交给共享数据库事务保存。 */
    override suspend fun update(update: ItemPhotoCollectionUpdate) {
        store.update(
            itemId = update.itemId,
            updatedPhotos = update.updatedPhotos,
            changeRecords = update.changeRecords,
        )
    }
}
