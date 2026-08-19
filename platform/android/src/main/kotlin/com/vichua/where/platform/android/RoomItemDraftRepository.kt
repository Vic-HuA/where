package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.ItemDraftStore
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.ItemDraftId
import com.vichua.where.feature.item.draft.ItemDraftRepository

/**
 * 使用共享 Room Store 实现 Android 物品草稿仓储。
 */
class RoomItemDraftRepository(
    private val store: ItemDraftStore,
    private val nowMillis: () -> Long,
) : ItemDraftRepository {
    /** 加载当前设备最近一份未过期草稿。 */
    override suspend fun loadLatest(): ItemDraft? = store.loadLatest(nowMillis())

    /** 保存当前设备草稿。 */
    override suspend fun save(draft: ItemDraft) {
        store.save(draft)
    }

    /** 删除指定草稿。 */
    override suspend fun discard(draftId: ItemDraftId, deviceId: DeviceId) {
        store.discard(draftId, deviceId)
    }
}
