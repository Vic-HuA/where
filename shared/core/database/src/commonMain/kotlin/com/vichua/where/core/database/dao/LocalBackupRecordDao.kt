package com.vichua.where.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vichua.where.core.database.entity.LocalBackupRecordEntity

/**
 * 当前设备备份记录的数据访问接口。
 */
@Dao
interface LocalBackupRecordDao {
    /** 插入本机备份记录。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LocalBackupRecordEntity)

    /** 查询当前设备最近一条已验证备份。 */
    @Query(
        """
        SELECT * FROM local_backup_records
        WHERE device_id = :deviceId AND status = 'VERIFIED'
        ORDER BY verified_at DESC, created_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestVerified(deviceId: String): LocalBackupRecordEntity?
}
