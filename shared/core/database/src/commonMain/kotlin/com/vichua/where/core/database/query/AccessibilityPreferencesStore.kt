package com.vichua.where.core.database.query

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.core.model.UtcTimestamp

/**
 * 读取并写入当前设备的适老与辅助偏好。
 *
 * 偏好不属于家庭事务，因此不生成变更记录，也不参与物品或位置写入。
 */
class AccessibilityPreferencesStore(
    private val database: WhereDatabase,
) {
    /**
     * 读取当前有效设备的辅助偏好；没有行时按普通模式插入默认值。
     *
     * @param nowEpochMilliseconds 首次插入默认行时使用的当前时间。
     */
    suspend fun loadOrCreate(nowEpochMilliseconds: Long): LocalAccessibilityPreferences {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before loading accessibility preferences.")
        val device = database.deviceDao().findFirstActiveByHousehold(household.id)
            ?: error("Active device is required before loading accessibility preferences.")
        val existing = database.accessibilityPreferencesDao().findByDeviceId(device.id)
        if (existing != null) {
            return existing.toDomain()
        }
        val created = LocalAccessibilityPreferences.standardDefault(
            deviceId = DeviceId(device.id),
            updatedAt = UtcTimestamp(nowEpochMilliseconds),
        )
        database.accessibilityPreferencesDao().upsert(created.toEntity())
        return created
    }

    /**
     * 覆盖保存当前设备辅助偏好。
     *
     * 调用方必须先通过 [loadOrCreate] 拿到带正确设备 ID 的对象，避免把偏好写到其他设备。
     */
    suspend fun save(preferences: LocalAccessibilityPreferences) {
        val device = database.deviceDao().findFirstActiveByHousehold(
            requireNotNull(database.householdDao().findFirstActive()) {
                "Active household is required before saving accessibility preferences."
            }.id,
        )
        require(device != null && device.id == preferences.deviceId.value) {
            "Accessibility preferences must belong to the current active device."
        }
        database.accessibilityPreferencesDao().upsert(preferences.toEntity())
    }
}
