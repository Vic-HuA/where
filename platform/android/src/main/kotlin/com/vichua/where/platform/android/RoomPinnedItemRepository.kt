package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.PinnedItemStore
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.PinnedItem
import com.vichua.where.feature.item.pin.PinnedItemContext
import com.vichua.where.feature.item.pin.PinnedItemRepository

/**
 * 使用共享 Room Store 实现 Android 常用物品入口仓储。
 */
class RoomPinnedItemRepository(
    private val store: PinnedItemStore,
) : PinnedItemRepository {
    override suspend fun load(itemId: ItemId): PinnedItemContext {
        val context = store.load(itemId)
        return PinnedItemContext(
            householdId = context.householdId,
            sourceDeviceId = context.sourceDeviceId,
            item = context.item,
            existing = context.existing,
            activeCount = context.activeCount,
        )
    }

    override suspend fun pin(pinned: PinnedItem, changeRecord: ChangeRecord) {
        store.pin(pinned, changeRecord)
    }

    override suspend fun unpin(pinned: PinnedItem, changeRecord: ChangeRecord) {
        store.unpin(pinned, changeRecord)
    }
}
