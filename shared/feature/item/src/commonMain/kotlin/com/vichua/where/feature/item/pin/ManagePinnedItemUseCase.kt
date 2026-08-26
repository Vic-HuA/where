package com.vichua.where.feature.item.pin

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.PinnedItem
import com.vichua.where.core.model.PinnedItemId
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp

/**
 * 固定或取消常用物品入口所需的当前家庭上下文。
 *
 * @property householdId 当前家庭。
 * @property sourceDeviceId 当前有效设备。
 * @property item 被固定或取消固定的未删除物品。
 * @property existing 该物品当前仍有效的入口；尚未固定时为空。
 * @property activeCount 家庭当前仍有效的入口数量。
 */
data class PinnedItemContext(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val item: Item,
    val existing: PinnedItem?,
    val activeCount: Int,
)

/**
 * 常用物品入口仓储契约。
 */
interface PinnedItemRepository {
    /** 加载指定物品的固定上下文。 */
    suspend fun load(itemId: ItemId): PinnedItemContext

    /** 固定一个常用物品入口。 */
    suspend fun pin(pinned: PinnedItem, changeRecord: ChangeRecord)

    /** 取消固定一个常用物品入口。 */
    suspend fun unpin(pinned: PinnedItem, changeRecord: ChangeRecord)
}

/**
 * 把已存在且未删除的物品固定为常用入口。
 */
class PinPinnedItemUseCase(
    private val repository: PinnedItemRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 同一物品最多一条未删除入口；物品必须仍属于当前家庭。
     */
    suspend operator fun invoke(itemId: ItemId) {
        val context = repository.load(itemId)
        require(context.existing == null) { "Item is already pinned." }
        val now = UtcTimestamp(clock.now())
        val pinned = PinnedItem(
            id = PinnedItemId(idGenerator.generate()),
            householdId = context.householdId,
            itemId = context.item.id,
            sortOrder = SortOrder(context.activeCount),
            createdAt = now,
            updatedAt = now,
            version = EntityVersion(INITIAL_ENTITY_VERSION),
            sourceDeviceId = context.sourceDeviceId,
        )
        repository.pin(
            pinned = pinned,
            changeRecord = ChangeRecord(
                id = ChangeRecordId(idGenerator.generate()),
                householdId = context.householdId,
                entityType = ChangeEntityType.PINNED_ITEM,
                entityId = pinned.id.value,
                operation = ChangeOperation.CREATE,
                entityVersion = pinned.version,
                sourceDeviceId = context.sourceDeviceId,
                occurredAt = now,
            ),
        )
    }
}

/**
 * 取消固定常用物品入口。
 */
class UnpinPinnedItemUseCase(
    private val repository: PinnedItemRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 目标必须是当前仍有效的常用物品入口。
     */
    suspend operator fun invoke(itemId: ItemId) {
        val context = repository.load(itemId)
        val existing = requireNotNull(context.existing) { "Item is not pinned." }
        val now = UtcTimestamp(clock.now())
        val updated = existing.copy(
            deletedAt = now,
            updatedAt = now,
            version = existing.version.next(),
            sourceDeviceId = context.sourceDeviceId,
        )
        repository.unpin(
            pinned = updated,
            changeRecord = ChangeRecord(
                id = ChangeRecordId(idGenerator.generate()),
                householdId = context.householdId,
                entityType = ChangeEntityType.PINNED_ITEM,
                entityId = updated.id.value,
                operation = ChangeOperation.DELETE,
                entityVersion = updated.version,
                sourceDeviceId = context.sourceDeviceId,
                occurredAt = now,
            ),
        )
    }
}

private const val INITIAL_ENTITY_VERSION = 1L
