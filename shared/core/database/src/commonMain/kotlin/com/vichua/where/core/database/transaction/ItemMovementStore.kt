package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent

/**
 * 数据库层更新位置上下文。
 */
data class StoredItemMovementContext(
    val detail: com.vichua.where.core.database.query.StoredItemDetail,
    val locations: List<StoredItemCreationLocation>,
)

/**
 * 聚合更新位置所需查询并复用物品写入事务。
 */
class ItemMovementStore(
    database: WhereDatabase,
) {
    private val detailStore = ItemDetailStore(database)
    private val creationStore = ManualItemCreationStore(database)
    private val writeStore = ItemWriteStore(database)

    /** 加载指定物品详情和当前家庭可用位置。 */
    suspend fun loadContext(itemId: ItemId): StoredItemMovementContext {
        val detail = requireNotNull(detailStore.find(itemId)) {
            "Cannot move a missing item."
        }
        return StoredItemMovementContext(
            detail = detail,
            locations = creationStore.loadContext().availableLocations,
        )
    }

    /** 原子更新物品位置、历史、变更记录和搜索索引。 */
    suspend fun move(
        updatedItem: Item,
        locationEvent: ItemLocationEvent,
        changeRecord: ChangeRecord,
        aliasesText: String,
        categoryText: String,
        locationPathText: String,
    ) {
        writeStore.moveItem(
            updatedItem = updatedItem,
            locationEvent = locationEvent,
            changeRecord = changeRecord,
            searchDocument = ItemSearchDocument(
                itemId = updatedItem.id,
                name = updatedItem.name,
                aliasesText = aliasesText,
                categoryText = categoryText,
                noteText = updatedItem.note.orEmpty(),
                locationPathText = locationPathText,
            ),
        )
    }
}
