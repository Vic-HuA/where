package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.PinnedItem

/**
 * 固定或取消常用物品入口所需的当前家庭上下文。
 *
 * @property householdId 当前家庭。
 * @property sourceDeviceId 当前有效设备。
 * @property item 被固定或取消固定的未删除物品。
 * @property existing 该物品当前仍有效的入口；尚未固定时为空。
 * @property activeCount 家庭当前仍有效的入口数量，用于分配新的排序。
 */
data class StoredPinnedItemContext(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val item: Item,
    val existing: PinnedItem?,
    val activeCount: Int,
)

/**
 * 在同一事务中维护常用物品入口和对应变更记录。
 */
class PinnedItemStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 加载指定物品的固定上下文；物品不存在或已删除时失败。
     */
    suspend fun load(itemId: ItemId): StoredPinnedItemContext {
        val item = requireNotNull(database.itemDao().findActiveById(itemId.value)?.toDomain()) {
            "Pinned item target does not exist."
        }
        val device = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(item.householdId.value),
        ) {
            "Cannot pin an item without an active source device."
        }
        return StoredPinnedItemContext(
            householdId = item.householdId,
            sourceDeviceId = DeviceId(device.id),
            item = item,
            existing = database.homeSupportDao()
                .findActivePinnedByItem(item.id.value)
                ?.toDomain(),
            activeCount = database.homeSupportDao()
                .findActivePinnedItems(item.householdId.value, MAX_ACTIVE_PINNED_ITEMS)
                .size,
        )
    }

    /**
     * 固定一个仍有效的常用物品入口。
     */
    suspend fun pin(
        pinned: PinnedItem,
        changeRecord: ChangeRecord,
    ) {
        require(pinned.deletedAt == null) { "Pinned item must be active." }
        require(changeRecord.entityType == ChangeEntityType.PINNED_ITEM) {
            "Pinned item change record entity type must be PINNED_ITEM."
        }
        transactionRunner.write {
            val item = itemDao().findActiveById(pinned.itemId.value)?.toDomain()
            require(item != null) { "Pinned item target does not exist." }
            require(item.householdId == pinned.householdId) {
                "Pinned item must belong to the same household."
            }
            require(homeSupportDao().findActivePinnedByItem(pinned.itemId.value) == null) {
                "Item is already pinned."
            }
            homeSupportDao().insertPinnedItems(listOf(pinned.toEntity()))
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    /**
     * 取消固定常用物品入口。
     */
    suspend fun unpin(
        pinned: PinnedItem,
        changeRecord: ChangeRecord,
    ) {
        require(pinned.deletedAt != null) { "Unpinned item must be soft-deleted." }
        require(changeRecord.entityType == ChangeEntityType.PINNED_ITEM) {
            "Pinned item change record entity type must be PINNED_ITEM."
        }
        transactionRunner.write {
            require(homeSupportDao().updatePinnedItem(pinned.toEntity()) == 1) {
                "Pinned item update must affect exactly one row."
            }
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    private companion object {
        const val MAX_ACTIVE_PINNED_ITEMS = 64
    }
}
