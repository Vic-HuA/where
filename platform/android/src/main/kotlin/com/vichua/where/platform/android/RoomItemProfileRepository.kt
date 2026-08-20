package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.ItemProfileStore
import com.vichua.where.core.model.ItemId
import com.vichua.where.feature.item.profile.ItemProfileContext
import com.vichua.where.feature.item.profile.ItemProfileRepository
import com.vichua.where.feature.item.profile.ItemProfileUpdate

/**
 * 使用共享 Room Store 实现 Android 物品档案编辑仓储。
 */
class RoomItemProfileRepository(
    private val store: ItemProfileStore,
) : ItemProfileRepository {
    /** 加载物品档案和当前设备。 */
    override suspend fun load(itemId: ItemId): ItemProfileContext {
        val context = store.load(itemId)
        return ItemProfileContext(
            item = context.item,
            locationPath = context.locationPath,
            aliasesText = context.aliasesText,
            categoryText = context.categoryText,
            currentDeviceId = context.currentDeviceId,
        )
    }

    /** 把已校验聚合交给共享数据库事务保存。 */
    override suspend fun update(update: ItemProfileUpdate) {
        store.update(
            updatedItem = update.updatedItem,
            changeRecord = update.changeRecord,
            aliasesText = update.aliasesText,
            categoryText = update.categoryText,
            locationPathText = update.locationPathText,
        )
    }
}
