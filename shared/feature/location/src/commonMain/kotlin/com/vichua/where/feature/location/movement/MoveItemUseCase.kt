package com.vichua.where.feature.location.movement

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationEventId
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.UtcTimestamp

/** 更新位置页面可选择的位置。 */
data class MoveTargetLocation(
    val locationId: LocationNodeId,
    val displayPath: String,
    val name: String,
    val type: LocationType,
    val iconKey: String?,
)

/**
 * 更新位置所需的当前物品和索引上下文。
 *
 * @property recentLocations 该物品近期到过、且仍有效的位置，供顶部快捷选择。
 * @property favoriteLocations 用户固定的常用位置。
 * @property rootLocationId 家庭根节点，页内新建房间时作为父位置。
 */
data class MoveItemContext(
    val item: Item,
    val currentLocationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val availableLocations: List<MoveTargetLocation>,
    val recentLocations: List<MoveTargetLocation>,
    val favoriteLocations: List<MoveTargetLocation>,
    val rootLocationId: LocationNodeId,
)

/**
 * 更新位置后需要原子保存的聚合。
 */
data class ItemMovement(
    val updatedItem: Item,
    val locationEvent: ItemLocationEvent,
    val changeRecord: ChangeRecord,
    val aliasesText: String,
    val categoryText: String,
    val locationPathText: String,
)

/**
 * 更新物品位置的仓储契约。
 */
interface ItemMovementRepository {
    /** 加载指定物品和可选位置。 */
    suspend fun loadContext(itemId: ItemId): MoveItemContext

    /** 原子保存物品、历史、变更记录和搜索索引。 */
    suspend fun move(movement: ItemMovement)
}

/**
 * 加载更新位置页面上下文。
 */
class LoadMoveItemContextUseCase(
    private val repository: ItemMovementRepository,
) {
    /** 返回指定物品和可选位置。 */
    suspend operator fun invoke(itemId: ItemId): MoveItemContext =
        repository.loadContext(itemId)
}

/**
 * 原子更新单个物品当前位置。
 */
class MoveItemUseCase(
    private val repository: ItemMovementRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 移动指定物品到新的有效位置。
     */
    suspend operator fun invoke(
        itemId: ItemId,
        targetLocationId: LocationNodeId,
    ) {
        val context = repository.loadContext(itemId)
        val targetLocation = requireNotNull(
            context.availableLocations.singleOrNull { location ->
                location.locationId == targetLocationId
            },
        ) {
            "Target location is unavailable."
        }
        require(context.item.currentLocationId != targetLocationId) {
            "Target location must differ from current location."
        }

        val now = UtcTimestamp(clock.now())
        val updatedItem = context.item.copy(
            currentLocationId = targetLocationId,
            updatedAt = now,
            version = context.item.version.next(),
        )
        repository.move(
            ItemMovement(
                updatedItem = updatedItem,
                locationEvent = ItemLocationEvent(
                    id = ItemLocationEventId(idGenerator.generate()),
                    householdId = updatedItem.householdId,
                    itemId = updatedItem.id,
                    fromLocationId = context.item.currentLocationId,
                    toLocationId = targetLocationId,
                    fromPathSnapshot = context.currentLocationPath,
                    toPathSnapshot = targetLocation.displayPath,
                    reason = ItemLocationReason.MOVED,
                    occurredAt = now,
                    sourceDeviceId = updatedItem.sourceDeviceId,
                    version = updatedItem.version,
                ),
                changeRecord = ChangeRecord(
                    id = ChangeRecordId(idGenerator.generate()),
                    householdId = updatedItem.householdId,
                    entityType = ChangeEntityType.ITEM,
                    entityId = updatedItem.id.value,
                    operation = ChangeOperation.UPDATE,
                    entityVersion = updatedItem.version,
                    sourceDeviceId = updatedItem.sourceDeviceId,
                    occurredAt = now,
                ),
                aliasesText = context.aliasesText,
                categoryText = context.categoryText,
                locationPathText = targetLocation.displayPath,
            ),
        )
    }
}
