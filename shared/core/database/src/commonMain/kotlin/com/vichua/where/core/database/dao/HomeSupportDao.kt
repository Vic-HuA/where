package com.vichua.where.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.vichua.where.core.database.entity.FavoriteLocationEntity
import com.vichua.where.core.database.entity.LocalSearchHistoryEntity

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

    /** 保存当前设备去重后的最近查找记录。 */
    @Upsert
    suspend fun upsertSearchHistory(entity: LocalSearchHistoryEntity)

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
}
