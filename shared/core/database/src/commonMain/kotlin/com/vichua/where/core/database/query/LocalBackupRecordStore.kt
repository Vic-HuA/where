package com.vichua.where.core.database.query

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocalBackupRecord

/**
 * 读写当前设备的备份文件引用。
 *
 * 这些记录不属于家庭数据包，因此单独保存，也不生成变更记录。
 */
class LocalBackupRecordStore(
    private val database: WhereDatabase,
) {
    /**
     * 返回当前有效设备最近一条已验证备份。
     */
    suspend fun findLatestVerified(): LocalBackupRecord? {
        val deviceId = currentDeviceId().value
        return database.localBackupRecordDao().findLatestVerified(deviceId)?.toDomain()
    }

    /**
     * 保存一条本机备份记录。
     */
    suspend fun insert(record: LocalBackupRecord) {
        val device = currentDeviceId()
        require(record.deviceId == device) {
            "Local backup record must belong to the current device."
        }
        database.localBackupRecordDao().insert(record.toEntity())
    }

    /**
     * 解析当前未删除家庭 ID。
     */
    suspend fun currentHouseholdId(): HouseholdId {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before accessing backup records.")
        return HouseholdId(household.id)
    }

    /**
     * 解析当前未删除家庭和有效设备 ID。
     */
    suspend fun currentDeviceId(): DeviceId {
        val household = currentHouseholdId()
        val device = database.deviceDao().findFirstActiveByHousehold(household.value)
            ?: error("Active device is required before accessing backup records.")
        return DeviceId(device.id)
    }
}
