package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.ItemDraftId

/**
 * 管理当前设备物品录入草稿的本地读写。
 *
 * 草稿不进入变更记录；读取时先清理过期记录，保证恢复入口只看到 7 天内有效内容。
 */
class ItemDraftStore(
    private val database: WhereDatabase,
) {
    /**
     * 删除过期草稿后返回当前设备最近一份未过期草稿。
     */
    suspend fun loadLatest(nowMillis: Long): ItemDraft? {
        val household = database.householdDao().findFirstActive() ?: return null
        val device = database.deviceDao().findFirstActiveByHousehold(household.id)
            ?: return null
        database.itemDraftDao().deleteExpired(nowMillis)
        return database.itemDraftDao()
            .findLatestUnexpired(
                deviceId = device.id,
                householdId = household.id,
                currentTime = nowMillis,
            )
            ?.toDomain()
    }

    /**
     * 保存当前设备草稿。
     */
    suspend fun save(draft: ItemDraft) {
        val household = requireNotNull(database.householdDao().findFirstActive()) {
            "Cannot save an item draft without an active household."
        }
        val device = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(household.id),
        ) {
            "Cannot save an item draft without an active source device."
        }
        require(draft.householdId.value == household.id) {
            "Item draft household must match the active household."
        }
        require(draft.deviceId.value == device.id) {
            "Item draft device must match the active source device."
        }
        database.itemDraftDao().upsert(draft.toEntity())
    }

    /**
     * 删除指定设备上的草稿。
     */
    suspend fun discard(draftId: ItemDraftId, deviceId: DeviceId) {
        database.itemDraftDao().deleteByIdAndDevice(draftId.value, deviceId.value)
    }
}
