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
import com.vichua.where.core.database.entity.LocationPhotoAssetEntity
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

    /** 按主键读取家庭，包括软删除记录，供恢复覆盖使用。 */
    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun findById(id: String): HouseholdEntity?

    /** 删除指定家庭根记录；调用方必须先删除或迁移关联实体。 */
    @Query("DELETE FROM households WHERE id = :id")
    suspend fun deleteById(id: String): Int
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

    /** 查询家庭全部设备，包括已撤销记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM devices
        WHERE household_id = :householdId
        ORDER BY created_at ASC, id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<DeviceEntity>

    /** 删除家庭下除当前设备外的设备记录，避免恢复覆盖时丢掉本机设备。 */
    @Query("DELETE FROM devices WHERE household_id = :householdId AND id != :keepDeviceId")
    suspend fun deleteByHouseholdExcept(householdId: String, keepDeviceId: String): Int
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

    /** 查询家庭全部位置，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM location_nodes
        WHERE household_id = :householdId
        ORDER BY parent_id ASC, sort_order ASC, id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<LocationNodeEntity>

    /** 删除家庭全部位置节点，供替换恢复或清除使用。 */
    @Query("DELETE FROM location_nodes WHERE household_id = :householdId")
    suspend fun deleteByHousehold(householdId: String): Int
}

/**
 * 物品分类的数据访问接口。
 */
@Dao
interface CategoryDao {
    /** 插入分类记录。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    /** 批量插入分类，供家庭首次补种系统分类。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<CategoryEntity>)

    /** 查询家庭中未删除分类，供录入和编辑选择。 */
    @Query(
        """
        SELECT * FROM categories
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY sort_order ASC, normalized_name ASC, id ASC
        """,
    )
    suspend fun findActiveByHousehold(householdId: String): List<CategoryEntity>

    /** 更新分类记录。 */
    @Update
    suspend fun update(entity: CategoryEntity): Int

    /** 查询指定未删除分类。 */
    @Query("SELECT * FROM categories WHERE id = :id AND deleted_at IS NULL")
    suspend fun findActiveById(id: String): CategoryEntity?

    /** 观察家庭中未删除分类。 */
    @Query(
        """
        SELECT * FROM categories
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY sort_order ASC, normalized_name ASC, id ASC
        """,
    )
    fun observeActiveByHousehold(householdId: String): Flow<List<CategoryEntity>>

    /** 查询家庭全部分类，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM categories
        WHERE household_id = :householdId
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<CategoryEntity>

    /** 删除家庭全部分类，供替换恢复或清除使用。 */
    @Query("DELETE FROM categories WHERE household_id = :householdId")
    suspend fun deleteByHousehold(householdId: String): Int
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

    /** 加载家庭中位置待确认的未删除物品，供提醒列表使用。 */
    @Query(
        """
        SELECT * FROM items
        WHERE household_id = :householdId
          AND status = 'LOCATION_UNCONFIRMED'
          AND deleted_at IS NULL
        ORDER BY updated_at DESC, id ASC
        """,
    )
    suspend fun findActiveLocationUnconfirmed(householdId: String): List<ItemEntity>

    /** 加载直接放在指定位置上的未删除物品，供删除位置时迁移。 */
    @Query(
        """
        SELECT * FROM items
        WHERE current_location_id = :locationId AND deleted_at IS NULL
        ORDER BY updated_at DESC, id ASC
        """,
    )
    suspend fun findActiveAtLocation(locationId: String): List<ItemEntity>

    /** 统计直接关联指定位置的未删除物品数量。 */
    @Query(
        """
        SELECT COUNT(*) FROM items
        WHERE current_location_id = :locationId AND deleted_at IS NULL
        """,
    )
    suspend fun countActiveAtLocation(locationId: String): Long

    /** 统计家庭中全部未删除物品，供位置管理页展示物品总数。 */
    @Query(
        """
        SELECT COUNT(*) FROM items
        WHERE household_id = :householdId AND deleted_at IS NULL
        """,
    )
    suspend fun countActiveByHousehold(householdId: String): Long

    /** 加载家庭全部未删除物品，用于重建位置路径和统计各节点物品数。 */
    @Query(
        """
        SELECT * FROM items
        WHERE household_id = :householdId AND deleted_at IS NULL
        ORDER BY updated_at DESC, id ASC
        """,
    )
    suspend fun findActiveByHousehold(householdId: String): List<ItemEntity>

    /** 查询家庭全部物品，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM items
        WHERE household_id = :householdId
        ORDER BY created_at ASC, id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<ItemEntity>

    /** 删除家庭全部物品，供替换恢复或清除使用。 */
    @Query("DELETE FROM items WHERE household_id = :householdId")
    suspend fun deleteByHousehold(householdId: String): Int
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

    /** 查询物品全部别名，包括软删除记录，用于级联删除后的短时撤销。 */
    @Query(
        """
        SELECT * FROM item_aliases
        WHERE item_id = :itemId
        ORDER BY normalized_alias ASC, id ASC
        """,
    )
    suspend fun findAllByItem(itemId: String): List<ItemAliasEntity>

    /** 更新别名软删除状态。 */
    @Update
    suspend fun update(entity: ItemAliasEntity): Int

    /** 查询家庭全部别名，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT item_aliases.* FROM item_aliases
        INNER JOIN items ON items.id = item_aliases.item_id
        WHERE items.household_id = :householdId
        ORDER BY item_aliases.id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<ItemAliasEntity>

    /** 删除家庭物品的全部别名，供替换恢复或清除使用。 */
    @Query(
        """
        DELETE FROM item_aliases
        WHERE item_id IN (SELECT id FROM items WHERE household_id = :householdId)
        """,
    )
    suspend fun deleteByHousehold(householdId: String): Int
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

    /** 加载物品按画廊顺序排列的未删除照片。 */
    @Query(
        """
        SELECT * FROM photo_assets
        WHERE item_id = :itemId AND deleted_at IS NULL
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findActiveByItem(itemId: String): List<PhotoAssetEntity>

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

    /** 查询家庭全部照片元数据，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT photo_assets.* FROM photo_assets
        INNER JOIN items ON items.id = photo_assets.item_id
        WHERE items.household_id = :householdId
        ORDER BY photo_assets.item_id ASC, photo_assets.sort_order ASC, photo_assets.id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<PhotoAssetEntity>

    /** 删除家庭全部照片元数据，供替换恢复或清除使用。 */
    @Query("DELETE FROM photo_assets WHERE household_id = :householdId")
    suspend fun deleteByHousehold(householdId: String): Int
}

/**
 * 位置代表照片的数据访问接口。
 */
@Dao
interface LocationPhotoAssetDao {
    /** 批量插入位置照片元数据，文件转正由事务编排层在提交后处理。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<LocationPhotoAssetEntity>)

    /** 更新封面、完整性或软删除状态。 */
    @Update
    suspend fun update(entity: LocationPhotoAssetEntity): Int

    /** 查询位置全部照片，包括软删除记录，用于更换封面时停用旧照。 */
    @Query(
        """
        SELECT * FROM location_photo_assets
        WHERE location_node_id = :locationNodeId
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findAllByLocation(locationNodeId: String): List<LocationPhotoAssetEntity>

    /** 查询位置当前未删除照片。 */
    @Query(
        """
        SELECT * FROM location_photo_assets
        WHERE location_node_id = :locationNodeId AND deleted_at IS NULL
        ORDER BY sort_order ASC, id ASC
        """,
    )
    suspend fun findActiveByLocation(locationNodeId: String): List<LocationPhotoAssetEntity>

    /** 批量查询位置当前未删除代表照片。 */
    @Query(
        """
        SELECT * FROM location_photo_assets
        WHERE location_node_id IN (:locationNodeIds)
          AND is_cover = 1
          AND deleted_at IS NULL
        ORDER BY location_node_id ASC, id ASC
        """,
    )
    suspend fun findActiveCovers(locationNodeIds: List<String>): List<LocationPhotoAssetEntity>

    /** 查询家庭全部位置照片，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM location_photo_assets
        WHERE household_id = :householdId
        ORDER BY location_node_id ASC, sort_order ASC, id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<LocationPhotoAssetEntity>

    /** 删除家庭全部位置照片元数据，供替换恢复或清除使用。 */
    @Query("DELETE FROM location_photo_assets WHERE household_id = :householdId")
    suspend fun deleteByHousehold(householdId: String): Int
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

    /** 加载物品按时间倒序排列的未删除位置历史。 */
    @Query(
        """
        SELECT * FROM item_location_events
        WHERE item_id = :itemId AND deleted_at IS NULL
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    suspend fun findActiveByItem(itemId: String): List<ItemLocationEventEntity>

    /** 查询家庭全部位置历史，包括软删除记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT item_location_events.* FROM item_location_events
        INNER JOIN items ON items.id = item_location_events.item_id
        WHERE items.household_id = :householdId
        ORDER BY item_location_events.occurred_at ASC, item_location_events.id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<ItemLocationEventEntity>

    /** 删除家庭物品的全部位置历史，供替换恢复或清除使用。 */
    @Query(
        """
        DELETE FROM item_location_events
        WHERE item_id IN (SELECT id FROM items WHERE household_id = :householdId)
        """,
    )
    suspend fun deleteByHousehold(householdId: String): Int
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

    /** 删除当前设备和家庭下的全部草稿，避免正式保存后仍恢复旧输入。 */
    @Query(
        """
        DELETE FROM item_drafts
        WHERE device_id = :deviceId AND household_id = :householdId
        """,
    )
    suspend fun deleteByDeviceAndHousehold(
        deviceId: String,
        householdId: String,
    ): Int

    /** 查询指定设备在家庭下的全部草稿，供恢复后校验正式引用。 */
    @Query(
        """
        SELECT * FROM item_drafts
        WHERE device_id = :deviceId AND household_id = :householdId
        ORDER BY updated_at DESC, id DESC
        """,
    )
    suspend fun findAllByDeviceAndHousehold(
        deviceId: String,
        householdId: String,
    ): List<ItemDraftEntity>
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

    /** 查询家庭全部变更记录，供加密备份快照使用。 */
    @Query(
        """
        SELECT * FROM change_records
        WHERE household_id = :householdId
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun findAllByHousehold(householdId: String): List<ChangeRecordEntity>

    /** 删除家庭全部变更记录，供替换恢复或清除使用。 */
    @Query("DELETE FROM change_records WHERE household_id = :householdId")
    suspend fun deleteByHousehold(householdId: String): Int
}
