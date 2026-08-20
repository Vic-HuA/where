package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId

/**
 * 数据库层提供的物品档案编辑上下文。
 *
 * @property item 当前未删除物品。
 * @property locationPath 当前完整位置路径。
 * @property aliasesText 未删除别名拼接文本。
 * @property categoryText 当前分类名称。
 * @property currentDeviceId 当前有效设备，用于记录本次修改来源。
 */
data class StoredItemProfileContext(
    val item: Item,
    val locationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val currentDeviceId: DeviceId,
)

/**
 * 加载物品档案并复用写入事务保存名称、位置说明和备注。
 */
class ItemProfileStore(
    private val database: WhereDatabase,
) {
    private val detailStore = ItemDetailStore(database)
    private val writeStore = ItemWriteStore(database)

    /**
     * 读取指定物品和当前设备；物品不存在时失败。
     */
    suspend fun load(itemId: ItemId): StoredItemProfileContext {
        val detail = requireNotNull(detailStore.find(itemId)) {
            "Cannot edit a missing item."
        }
        val currentDevice = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(detail.item.householdId.value),
        ) {
            "Cannot edit an item without an active source device."
        }
        return StoredItemProfileContext(
            item = detail.item,
            locationPath = detail.locationPath,
            aliasesText = detail.aliases.joinToString(" ") { alias -> alias.alias },
            categoryText = detail.categoryName.orEmpty(),
            currentDeviceId = DeviceId(currentDevice.id),
        )
    }

    /**
     * 原子保存档案字段、变更记录和搜索索引。
     */
    suspend fun update(
        updatedItem: Item,
        changeRecord: ChangeRecord,
        aliasesText: String,
        categoryText: String,
        locationPathText: String,
    ) {
        writeStore.updateProfile(
            updatedItem = updatedItem,
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
