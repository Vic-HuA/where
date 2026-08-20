package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.ItemDeletionStore
import com.vichua.where.core.model.ItemId
import com.vichua.where.feature.item.deletion.ItemDeletionContext
import com.vichua.where.feature.item.deletion.ItemDeletionRepository
import com.vichua.where.feature.item.deletion.ItemDeletionWrite
import com.vichua.where.feature.item.deletion.ItemRestoreWrite

/**
 * 使用共享 Room Store 实现 Android 物品删除与短时撤销仓储。
 */
class RoomItemDeletionRepository(
    private val store: ItemDeletionStore,
) : ItemDeletionRepository {
    /** 加载物品及其全部别名和照片。 */
    override suspend fun load(itemId: ItemId): ItemDeletionContext {
        val context = store.load(itemId)
        return ItemDeletionContext(
            item = context.item,
            aliases = context.aliases,
            photos = context.photos,
            locationPath = context.locationPath,
            aliasesText = context.aliasesText,
            categoryText = context.categoryText,
            currentDeviceId = context.currentDeviceId,
        )
    }

    /** 把已校验删除聚合交给共享数据库事务保存。 */
    override suspend fun delete(write: ItemDeletionWrite) {
        store.delete(
            deletedItem = write.deletedItem,
            deletedAliases = write.deletedAliases,
            deletedPhotos = write.deletedPhotos,
            changeRecords = write.changeRecords,
        )
    }

    /** 把已校验恢复聚合交给共享数据库事务保存。 */
    override suspend fun restore(write: ItemRestoreWrite) {
        store.restore(
            restoredItem = write.restoredItem,
            restoredAliases = write.restoredAliases,
            restoredPhotos = write.restoredPhotos,
            changeRecords = write.changeRecords,
            aliasesText = write.aliasesText,
            categoryText = write.categoryText,
            locationPathText = write.locationPathText,
            expectedDeletedAt = write.expectedDeletedAt,
            expectedDeletedVersion = write.expectedDeletedVersion,
        )
    }
}
