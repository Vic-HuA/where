package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.model.Category
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.systemCategoriesForHousehold
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity

/**
 * 数据库层提供的物品档案编辑上下文。
 *
 * @property item 当前未删除物品。
 * @property locationPath 当前完整位置路径。
 * @property aliasesText 未删除别名拼接文本。
 * @property categoryText 当前分类名称。
 * @property currentDeviceId 当前有效设备，用于记录本次修改来源。
 * @property aliases 当前未删除别名原文。
 * @property categories 可供选择的未删除分类。
 */
data class StoredItemProfileContext(
    val item: Item,
    val locationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val currentDeviceId: DeviceId,
    val aliases: List<String>,
    val categories: List<Category>,
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
        val aliasNames = detail.aliases.map(ItemAlias::alias)
        return StoredItemProfileContext(
            item = detail.item,
            locationPath = detail.locationPath,
            aliasesText = aliasNames.joinToString(" "),
            categoryText = detail.categoryName.orEmpty(),
            currentDeviceId = DeviceId(currentDevice.id),
            aliases = aliasNames,
            categories = ensureSystemCategories(detail.item.householdId.value),
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
        aliases: List<ItemAlias> = emptyList(),
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
            aliases = aliases,
        )
    }

    /**
     * 家庭还没有任何分类时写入内置系统分类。
     */
    private suspend fun ensureSystemCategories(householdId: String): List<Category> {
        val existing = database.categoryDao()
            .findActiveByHousehold(householdId)
            .map { entity -> entity.toDomain() }
        if (existing.isNotEmpty()) {
            return existing
        }
        val seeded = systemCategoriesForHousehold(HouseholdId(householdId))
        database.categoryDao().insertAll(seeded.map(Category::toEntity))
        return seeded
    }
}
