package com.vichua.where.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vichua.where.core.database.entity.LocalAccessibilityPreferencesEntity

/**
 * 当前设备适老与辅助偏好的数据访问接口。
 */
@Dao
interface AccessibilityPreferencesDao {
    /** 查询指定设备已保存的辅助偏好。 */
    @Query("SELECT * FROM local_accessibility_preferences WHERE device_id = :deviceId LIMIT 1")
    suspend fun findByDeviceId(deviceId: String): LocalAccessibilityPreferencesEntity?

    /** 按设备覆盖写入辅助偏好，避免同一设备出现多行。 */
    @Upsert
    suspend fun upsert(entity: LocalAccessibilityPreferencesEntity)
}
