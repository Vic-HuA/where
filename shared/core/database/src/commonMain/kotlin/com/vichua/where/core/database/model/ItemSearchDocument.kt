package com.vichua.where.core.database.model

import com.vichua.where.core.database.entity.ItemSearchFtsEntity
import com.vichua.where.core.model.ItemId

/**
 * 由正式物品数据聚合得到的全文搜索文档。
 *
 * 该对象不是事实来源，索引损坏时可以根据物品、别名、分类和位置树完整重建。
 *
 * @property itemId 对应正式物品 ID。
 * @property name 物品名称。
 * @property aliasesText 按稳定规则拼接的未删除别名。
 * @property categoryText 分类名称，没有分类时为空字符串。
 * @property noteText 备注文本，没有备注时为空字符串。
 * @property locationPathText 当前完整位置路径。
 */
data class ItemSearchDocument(
    val itemId: ItemId,
    val name: String,
    val aliasesText: String,
    val categoryText: String,
    val noteText: String,
    val locationPathText: String,
) {
    init {
        require(name.isNotBlank()) { "Search document item name must not be blank." }
        require(locationPathText.isNotBlank()) {
            "Search document location path must not be blank."
        }
    }

    /**
     * 转换为可写入 SQLite FTS 的 Room 实体。
     */
    internal fun toEntity(): ItemSearchFtsEntity = ItemSearchFtsEntity(
        rowId = 0,
        itemId = itemId.value,
        name = name,
        aliasesText = aliasesText,
        categoryText = categoryText,
        noteText = noteText,
        locationPathText = locationPathText,
    )
}
