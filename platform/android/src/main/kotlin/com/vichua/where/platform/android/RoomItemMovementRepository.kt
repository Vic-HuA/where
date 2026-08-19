package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.ItemMovementStore
import com.vichua.where.core.model.ItemId
import com.vichua.where.feature.location.movement.ItemMovement
import com.vichua.where.feature.location.movement.ItemMovementRepository
import com.vichua.where.feature.location.movement.MoveItemContext
import com.vichua.where.feature.location.movement.MoveTargetLocation

/**
 * 使用共享 Room Store 实现 Android 更新位置仓储。
 */
class RoomItemMovementRepository(
    private val store: ItemMovementStore,
) : ItemMovementRepository {
    /** 加载物品和可选位置。 */
    override suspend fun loadContext(itemId: ItemId): MoveItemContext {
        val context = store.loadContext(itemId)
        return MoveItemContext(
            item = context.detail.item,
            currentLocationPath = context.detail.locationPath,
            aliasesText = context.detail.aliases.joinToString(" ") { alias -> alias.alias },
            categoryText = context.detail.categoryName.orEmpty(),
            availableLocations = context.locations.map { location ->
                MoveTargetLocation(location.locationId, location.displayPath)
            },
        )
    }

    /** 保存更新位置聚合。 */
    override suspend fun move(movement: ItemMovement) {
        store.move(
            updatedItem = movement.updatedItem,
            locationEvent = movement.locationEvent,
            changeRecord = movement.changeRecord,
            aliasesText = movement.aliasesText,
            categoryText = movement.categoryText,
            locationPathText = movement.locationPathText,
        )
    }
}
