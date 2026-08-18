package com.vichua.where.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.vichua.where.core.database.entity.CategoryEntity
import com.vichua.where.core.database.entity.ChangeRecordEntity
import com.vichua.where.core.database.entity.DeviceEntity
import com.vichua.where.core.database.entity.HouseholdEntity
import com.vichua.where.core.database.entity.ItemAliasEntity
import com.vichua.where.core.database.entity.ItemDraftEntity
import com.vichua.where.core.database.entity.ItemEntity
import com.vichua.where.core.database.entity.ItemLocationEventEntity
import com.vichua.where.core.database.entity.LocationNodeEntity
import com.vichua.where.core.database.entity.PhotoAssetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 家庭根记录的数据访问接口。
 */
@Dao
interface HouseholdDao {
    /** 插入新家庭，ID 冲突时终止当前事务。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: HouseholdEntity)

    /** 更新已存在家庭，调用方必须先完成版本校验。 */
    @Update
    suspend fun update(entity: HouseholdEntity): Int

    /** 查询指定未删除家庭。 */
    @Query("SELECT * FROM households WHERE id = :id AND deleted_at IS NULL")
    suspend fun findActiveById(id: String): HouseholdEntity?

    /** 统计当前未删除家庭数量，MVP 初始化前必须为 0。 */
    @Query("SELECT COUNT(*) FROM households WHERE deleted_at IS NULL")
    suspend fun countActive(): Long

    /** 查询本地首个未删除家庭。MVP 只允许存在一个未删除家庭。 */
    @Query(
        """
        SELECT * FROM households
        WHERE deleted_at IS NULL
        ORDER BY created_at ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findFirstActive(): HouseholdEntity?
}

/**
 * 当前及后续配对设备的数据访问接口。
 */
@Dao
interface DeviceDao {
    /** 插入设备记录，ID 冲突时终止当前事务。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DeviceEntity)

    /** 更新设备活动、配对或撤销状态。 */
    @Update
    suspend fun update(entity: DeviceEntity): Int

    /** 查询指定设备。 */
    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun findById(id: String): DeviceEntity?

    /** 查询家庭中首个仍未撤销的设备。 */
    @Query(
        """
        SELECT * FROM devices
        WHERE household_id = :householdId AND revoked_at IS NULL
        ORDER BY created_at ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findFirstActiveByHousehold(householdId: String): DeviceEntity?

    /** 查询家庭下仍未撤销的设备。 */
    @Query(
        """
        SELECT * FROM devices
        WHERE household_id = :householdId AND revoked_at IS NULL
        ORDER BY created_at ASC, id ASC
        """,
    )
    fun observeActiveByHousehold(householdId: String): Flow<List<DeviceEntity>>
}

/**
 * 可变深度位置树的数据访问接口。
 */
@Dao
interface LocationNodeDao {
    /** 插入位置节点，ID 冲突时终止当前事务。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LocationNodeEntity)

    /** 批量插入同一初始化事务中的位置节点。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<LocationNodeEntity>)

    /** 更新位置节点，调用方必须先校验版本和环路。 */
    @Update
    suspend fun update(entity: LocationNodeEntity): Int

    /** 查询指定位置，包括软删除记录。 */
    @Query("SELECT * FROM location_nodes WHERE id = :id")
    suspend fun findById(id: String): LocationNodeEntity?

    /** 查询家庭当前全部未删除位置，用于构建和校验位置树。 */
    @Query(
        """
        SELECT * FROM location_nodes
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY parent_id ASC, sort_order ASC, id ASC
        """,
    )
    suspend fun findActiveTree(householdId: String): List<LocationNodeEntity>

    /** 持续观察指定父位置下的未删除直接子节点。 */
    @Query(
        """
        SELECT * FROM location_nodes
        WHERE household_id = :householdId
          AND parent_id = :parentId
          AND deleted_at IS NULL
        ORDER BY sort_order ASC, id ASC
        """,
    )
    fun observeActiveChildren(
        householdId: String,
        parentId: String,
    ): Flow<List<LocationNodeEntity>>

    /** 统计位置的未删除直接子节点数量，删除位置前必须同时检查关联物品。 */
    @Query(
        """
        SELECT COUNT(*) FROM location_nodes
        WHERE parent_id = :parentId AND deleted_at IS NULL
        """,
    )
    suspend fun countActiveChildren(parentId: String): Long
}

/**
 * 物品分类的数据访问接口。
 */
@Dao
interface CategoryDao {
    /** 插入分类记录。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    /** 更新分类记录。 */
    @Update
    suspend fun update(entity: CategoryEntity): Int

    /** 观察家庭中未删除分类。 */
    @Query(
        """
        SELECT * FROM categories
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY sort_order ASC, normalized_name ASC, id ASC
        """,
    )
    fun observeActiveByHousehold(householdId: String): Flow<List<CategoryEntity>>
}

/**
 * 正式物品档案的数据访问接口。
 */
@Dao
interface ItemDao {
    /** 插入物品记录，ID 冲突时终止当前事务。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ItemEntity)

    /** 更新物品记录，调用方必须先完成版本校验。 */
    @Update
    suspend fun update(entity: ItemEntity): Int

    /** 查询指定物品，包括软删除记录。 */
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun findById(id: String): ItemEntity?

    /** 查询指定未删除物品。 */
    @Query("SELECT * FROM items WHERE id = :id AND deleted_at IS NULL")
    suspend fun findActiveById(id: String): ItemEntity?

    /** 观察首页最近更新的未删除物品。 */
    @Query(
        """
        SELECT * FROM items
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY updated_at DESC, created_at DESC, id ASC
        LIMIT :limit
        """,
    )
    fun observeRecentActive(
        householdId: String,
        limit: Int,
    ): Flow<List<ItemEntity>>

    /** 加载首页最近更新的未删除物品。 */
    @Query(
        """
        SELECT * FROM items
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY updated_at DESC, created_at DESC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun findRecentActive(
        householdId: String,
        limit: Int,
    ): List<ItemEntity>

    /** 统计家庭中位置待确认的未删除物品。 */
    @Query(
        """
        SELECT COUNT(*) FROM items
        WHERE household_id = :householdId
          AND status = 'LOCATION_UNCONFIRMED'
          AND deleted_at IS NULL
        """,
    )
    suspend fun countLocationUnconfirmed(householdId: String): Long

    /** 统计直接关联指定位置的未删除物品数量。 */
    @Query(
        """
        SELECT COUNT(*) FROM items
        WHERE current_location_id = :locationId AND deleted_at IS NULL
        """,
    )
    suspend fun countActiveAtLocation(locationId: String): Long
}

/**
 * 物品别名的数据访问接口。
 */
@Dao
interface ItemAliasDao {
    /** 批量插入新别名，任一冲突都会终止当前事务。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ItemAliasEntity>)

    /** 查询物品全部未删除别名。 */
    @Query(
        """
        SELECT * FROM item_aliases
        WHERE item_id = :itemId AND deleted_at IS NULL
        ORDER BY normalized_alias ASC, id ASC
        """,
    )
    suspend fun findActiveByItem(itemId: String): List<ItemAliasEntity>

    /** 更新别名软删除状态。 */
    @Update
    suspend fun update(entity: ItemAliasEntity): Int
}

/**
 * 物品照片元数据的数据访问接口。
 */
@Dao
interface PhotoAssetDao {
    /** 批量插入照片元数据，文件转正由事务编排层在提交后处理。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<PhotoAssetEntity>)

    /** 更新照片用途、顺序、封面或完整性状态。 */
    @Update
    suspend fun update(entity: PhotoAssetEntity): Int

    /** 查询物品全部未删除照片。 */
    @Query(
        """
        SELECT * FROM photo_assets
        WHERE item_id = :itemId AND deleted_at IS NULL
        ORDER BY sort_order ASC, id ASC
        """,
    )
    fun observeActiveByItem(itemId: String): Flow<List<PhotoAssetEntity>>

    /** 查询物品全部照片，包括软删除记录，用于撤销和恢复。 */
    @Query(
        """
        SELECT * FROM photo_assets
        WHERE item_id = :itemId
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findAllByItem(itemId: String): List<PhotoAssetEntity>

    /** 批量查询物品当前未删除封面照片。 */
    @Query(
        """
        SELECT * FROM photo_assets
        WHERE item_id IN (:itemIds)
          AND is_cover = 1
          AND deleted_at IS NULL
        ORDER BY item_id ASC, id ASC
        """,
    )
    suspend fun findActiveCovers(itemIds: List<String>): List<PhotoAssetEntity>
}

/**
 * 物品位置历史的数据访问接口。
 */
@Dao
interface ItemLocationEventDao {
    /** 插入不可变位置历史事件。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ItemLocationEventEntity)

    /** 观察物品未删除位置历史。 */
    @Query(
        """
        SELECT * FROM item_location_events
        WHERE item_id = :itemId AND deleted_at IS NULL
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    fun observeActiveByItem(itemId: String): Flow<List<ItemLocationEventEntity>>
}

/**
 * 当前设备临时草稿的数据访问接口。
 */
@Dao
interface ItemDraftDao {
    /** 保存草稿；同 ID 再次保存时覆盖当前设备本地内容。 */
    @Upsert
    suspend fun upsert(entity: ItemDraftEntity)

    /** 查询当前设备和家庭最近一份未过期草稿。 */
    @Query(
        """
        SELECT * FROM item_drafts
        WHERE device_id = :deviceId
          AND household_id = :householdId
          AND expires_at > :currentTime
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestUnexpired(
        deviceId: String,
        householdId: String,
        currentTime: Long,
    ): ItemDraftEntity?

    /** 删除已经过期的草稿记录，并返回删除数量。 */
    @Query("DELETE FROM item_drafts WHERE expires_at <= :currentTime")
    suspend fun deleteExpired(currentTime: Long): Int

    /** 删除已转为正式物品或用户主动放弃的草稿。 */
    @Query("DELETE FROM item_drafts WHERE id = :id AND device_id = :deviceId")
    suspend fun deleteByIdAndDevice(
        id: String,
        deviceId: String,
    ): Int
}

/**
 * 正式数据变更记录的数据访问接口。
 */
@Dao
interface ChangeRecordDao {
    /** 插入变更记录；必须与对应正式实体写入处于同一事务。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ChangeRecordEntity)

    /** 批量插入同一事务产生的正式数据变更记录。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ChangeRecordEntity>)

    /** 查询家庭在指定时间之后发生的变更。 */
    @Query(
        """
        SELECT * FROM change_records
        WHERE household_id = :householdId AND occurred_at > :afterTime
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun findAfter(
        householdId: String,
        afterTime: Long,
    ): List<ChangeRecordEntity>
}
