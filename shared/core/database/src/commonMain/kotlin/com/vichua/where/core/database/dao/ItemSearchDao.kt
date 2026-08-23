package com.vichua.where.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vichua.where.core.database.entity.ItemSearchFtsEntity

/**
 * 可重建物品全文索引的数据访问接口。
 *
 * 调用方必须先把用户输入转换为合法 FTS 查询表达式，不能直接拼接未经处理的输入。
 */
@Dao
interface ItemSearchDao {
    /** 插入单条索引记录，并返回 SQLite FTS 行标识。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ItemSearchFtsEntity): Long

    /** 在正式实体变更事务中移除指定物品的旧索引记录。 */
    @Query("DELETE FROM item_search_fts WHERE item_id = :itemId")
    suspend fun deleteByItemId(itemId: String): Int

    /** 执行已经转义和标准化的 FTS 查询。 */
    @Query(
        """
        SELECT rowid, item_id, name, aliases_text, category_text, note_text, location_path_text
        FROM item_search_fts
        WHERE item_search_fts MATCH :ftsQuery
        LIMIT :limit
        """,
    )
    suspend fun search(
        ftsQuery: String,
        limit: Int,
    ): List<ItemSearchFtsEntity>

    /**
     * 按子串补全 FTS 前缀匹配，让“电脑”也能命中“笔记本电脑”。
     *
     * 查询必须先标准化为小写；instr 对中文按字符比较，不依赖分词。
     * 反向 instr 用于语音转写仍带着“我要找…”时，物品名被包在整句里。
     */
    @Query(
        """
        SELECT rowid, item_id, name, aliases_text, category_text, note_text, location_path_text
        FROM item_search_fts
        WHERE instr(lower(name), :containsQuery) > 0
           OR instr(lower(aliases_text), :containsQuery) > 0
           OR instr(lower(category_text), :containsQuery) > 0
           OR instr(lower(note_text), :containsQuery) > 0
           OR instr(lower(location_path_text), :containsQuery) > 0
           OR (length(name) >= 2 AND instr(:containsQuery, lower(name)) > 0)
        LIMIT :limit
        """,
    )
    suspend fun searchContaining(
        containsQuery: String,
        limit: Int,
    ): List<ItemSearchFtsEntity>

    /** 清空可重建索引，正式物品数据不受影响。 */
    @Query("DELETE FROM item_search_fts")
    suspend fun clear(): Int
}
