package com.vichua.where.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * 可从正式数据完整重建的物品全文搜索索引。
 *
 * `rowId` 只满足 SQLite FTS 内部主键要求，物品关联始终以 `itemId` 为准。
 *
 * @property rowId SQLite FTS 使用的本地整数行标识。
 * @property itemId 对应正式物品的全局 ID。
 * @property name 物品名称。
 * @property aliasesText 以稳定分隔规则拼接的未删除别名。
 * @property categoryText 分类名称。
 * @property noteText 备注文本。
 * @property locationPathText 当前完整位置路径。
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "item_search_fts")
data class ItemSearchFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val name: String,
    @ColumnInfo(name = "aliases_text")
    val aliasesText: String,
    @ColumnInfo(name = "category_text")
    val categoryText: String,
    @ColumnInfo(name = "note_text")
    val noteText: String,
    @ColumnInfo(name = "location_path_text")
    val locationPathText: String,
)
