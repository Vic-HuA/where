package com.vichua.where.platform.android

import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.database.transaction.ManualItemCreationStore
import com.vichua.where.feature.item.creation.ItemCreationContext
import com.vichua.where.feature.item.creation.ItemCreationLocation
import com.vichua.where.feature.item.creation.ManualItemCreation
import com.vichua.where.feature.item.creation.ManualItemCreationRepository
import com.vichua.where.feature.item.profile.ItemCategoryOption

/**
 * 使用共享 Room Store 实现 Android 基础手动物品录入仓储。
 */
class RoomManualItemCreationRepository(
    private val store: ManualItemCreationStore,
) : ManualItemCreationRepository {
    /**
     * 加载当前家庭和可选择位置，并转换为功能层模型。
     */
    override suspend fun loadContext(): ItemCreationContext {
        val context = store.loadContext()
        return ItemCreationContext(
            householdId = context.householdId,
            sourceDeviceId = context.sourceDeviceId,
            rootLocationId = context.rootLocationId,
            availableLocations = context.availableLocations.map { location ->
                ItemCreationLocation(
                    locationId = location.locationId,
                    parentId = location.parentId,
                    displayPath = location.displayPath,
                    name = location.name,
                    type = location.type,
                    iconKey = location.iconKey,
                )
            },
            categories = context.categories.map { category ->
                ItemCategoryOption(
                    categoryId = category.id,
                    name = category.name,
                )
            },
        )
    }

    /**
     * 把已校验聚合交给共享数据库事务保存。
     */
    override suspend fun create(creation: ManualItemCreation) {
        store.create(
            item = creation.item,
            photos = creation.photos,
            locationEvent = creation.initialLocationEvent,
            changeRecord = creation.changeRecord,
            searchDocument = ItemSearchDocument(
                itemId = creation.searchContent.itemId,
                name = creation.searchContent.name,
                aliasesText = creation.searchContent.aliasesText,
                categoryText = creation.searchContent.categoryText,
                noteText = creation.searchContent.noteText,
                locationPathText = creation.searchContent.locationPathText,
            ),
            aliases = creation.aliases,
        )
    }
}
