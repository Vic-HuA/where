package com.vichua.where.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.vichua.where.core.database.entity.FavoriteLocationEntity
import com.vichua.where.core.database.entity.LocalSearchHistoryEntity
import com.vichua.where.core.database.entity.PinnedItemEntity

/**
 * 首页常用位置和当前设备最近查找的数据访问接口。
 */
@Dao
interface HomeSupportDao {
    /** 批量插入用户主动选择的常用位置引用。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFavoriteLocations(entities: List<FavoriteLocationEntity>)

    /** 查询家庭主动固定且仍有效的常用位置。 */
    @Query(
        """
        SELECT favorite_locations.* FROM favorite_locations
        INNER JOIN location_nodes
            ON location_nodes.id = favorite_locations.location_node_id
        WHERE favorite_locations.household_id = :householdId
          AND favorite_locations.deleted_at IS NULL
          AND location_nodes.deleted_at IS NULL
        ORDER BY favorite_locations.sort_order ASC, favorite_locations.id ASC
        LIMIT :limit
        """,
    )
    suspend fun findActiveFavoriteLocations(
        householdId: String,
        limit: Int,
    ): List<FavoriteLocationEntity>

    /** 查询家庭全部仍有效的常用位置引用，供位置管理删除时同步取消固定。 */
    @Query(
        """
        SELECT * FROM favorite_locations
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findAllActiveFavoriteLocations(householdId: String): List<FavoriteLocationEntity>

    /** 查询指定位置当前仍有效的常用位置引用。 */
    @Query(
        """
        SELECT * FROM favorite_locations
        WHERE location_node_id = :locationNodeId AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    suspend fun findActiveFavoriteByLocation(locationNodeId: String): FavoriteLocationEntity?

    /** 更新常用位置的取消固定状态，调用方必须先完成版本校验。 */
    @Update
    suspend fun updateFavoriteLocation(entity: FavoriteLocationEntity): Int

    /** 保存当前设备去重后的最近查找记录。 */
    @Upsert
    suspend fun upsertSearchHistory(entity: LocalSearchHistoryEntity)

    /** 删除同设备、同标准化查询和同筛选条件的旧记录。 */
    @Query(
        """
        DELETE FROM local_search_history
        WHERE device_id = :deviceId
          AND normalized_query = :normalizedQuery
          AND filter_hash = :filterHash
        """,
    )
    suspend fun deleteMatchingSearch(
        deviceId: String,
        normalizedQuery: String,
        filterHash: String,
    ): Int

    /** 查询当前设备最近执行的搜索。 */
    @Query(
        """
        SELECT * FROM local_search_history
        WHERE device_id = :deviceId
        ORDER BY executed_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun findRecentSearches(
        deviceId: String,
        limit: Int,
    ): List<LocalSearchHistoryEntity>

    /** 删除当前设备超过保留数量的旧搜索记录。 */
    @Query(
        """
        DELETE FROM local_search_history
        WHERE device_id = :deviceId
          AND id NOT IN (
              SELECT id FROM local_search_history
              WHERE device_id = :deviceId
              ORDER BY executed_at DESC, id DESC
              LIMIT :keepCount
          )
        """,
    )
    suspend fun trimSearchHistory(
        deviceId: String,
        keepCount: Int,
    ): Int

    /** 查询家庭全部常用位置引用，包括已取消固定记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM favorite_locations
        WHERE household_id = :householdId
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findAllFavoriteLocationsByHousehold(householdId: String): List<FavoriteLocationEntity>

    /** 删除家庭全部常用位置引用，供替换恢复或清除使用。 */
    @Query("DELETE FROM favorite_locations WHERE household_id = :householdId")
    suspend fun deleteFavoriteLocationsByHousehold(householdId: String): Int

    /** 批量插入常用物品入口。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPinnedItems(entities: List<PinnedItemEntity>)

    /**
     * 查询家庭仍有效且目标物品未删除的常用物品入口。
     *
     * 位置待确认物品仍返回，由展示层改成提醒态，避免入口消失后老人找不到。
     */
    @Query(
        """
        SELECT pinned_items.* FROM pinned_items
        INNER JOIN items
            ON items.id = pinned_items.item_id
        WHERE pinned_items.household_id = :householdId
          AND pinned_items.deleted_at IS NULL
          AND items.deleted_at IS NULL
        ORDER BY pinned_items.sort_order ASC, pinned_items.id ASC
        LIMIT :limit
        """,
    )
    suspend fun findActivePinnedItems(
        householdId: String,
        limit: Int,
    ): List<PinnedItemEntity>

    /** 查询指定物品当前仍有效的常用入口。 */
    @Query(
        """
        SELECT * FROM pinned_items
        WHERE item_id = :itemId AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    suspend fun findActivePinnedByItem(itemId: String): PinnedItemEntity?

    /** 查询家庭全部常用物品入口，包括已取消固定记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM pinned_items
        WHERE household_id = :householdId
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findAllPinnedItemsByHousehold(householdId: String): List<PinnedItemEntity>

    /** 更新常用物品入口的取消固定状态，调用方必须先完成版本校验。 */
    @Update
    suspend fun updatePinnedItem(entity: PinnedItemEntity): Int

    /** 删除家庭全部常用物品入口，供替换恢复或清除使用。 */
    @Query("DELETE FROM pinned_items WHERE household_id = :householdId")
    suspend fun deletePinnedItemsByHousehold(householdId: String): Int
}
